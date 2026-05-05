package fr.baretto.ollamassist.agent.tools.files;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic fallback for file paths that the LLM produced without a source-root prefix.
 *
 * <h2>Problem</h2>
 * <p>When asked to create {@code com/example/service/MathService.java}, the LLM may omit
 * the source-root prefix and give just {@code com/example/service/MathService.java}.
 * The tool would then create a spurious directory at the project root instead of placing
 * the file under {@code src/main/java/}.
 *
 * <h2>Strategy</h2>
 * <ul>
 *   <li><b>Write correction ({@link #correctWritePath}):</b> if the file content contains a
 *       {@code package} (or {@code namespace}) declaration whose path matches the tail of
 *       the given path, the path is prepended with the best matching source root.</li>
 *   <li><b>Read correction ({@link #findInSourceRoots}):</b> if a file is not found at the
 *       given path, all source roots are searched as potential prefixes.</li>
 * </ul>
 *
 * <p>Only JVM-family languages with explicit package declarations are handled
 * (Java, Kotlin, Groovy, Scala). For all other file types — config, scripts, markdown, etc. —
 * no correction is applied; the prompt is the first line of defence.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SourceRootResolver {

    /** Matches {@code package a.b.c;} (Java/Groovy/Scala) or {@code package a.b.c} (Kotlin). */
    static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "^\\s*package\\s+([\\w.]+)\\s*;?", Pattern.MULTILINE);

    /** Extensions for source files where package-path detection applies. */
    private static final List<String> SOURCE_EXTENSIONS =
            List.of(".java", ".kt", ".groovy", ".scala");

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * For FILE_WRITE: if {@code rawPath} looks like a bare package path (e.g.
     * {@code com/example/Foo.java} without {@code src/main/java/}), prepend the
     * best source root and return the corrected relative path.
     *
     * <p>Returns {@code rawPath} unchanged if no correction can be determined.
     *
     * @param rawPath the path as given by the LLM
     * @param content the file content (used to extract the package declaration)
     * @param project the current IntelliJ project
     */
    public static String correctWritePath(String rawPath, String content, Project project) {
        if (project == null || rawPath == null || content == null) return rawPath;
        String packagePath = extractMatchingPackagePath(rawPath, content);
        if (packagePath == null) return rawPath;

        String corrected = prependBestSourceRoot(rawPath, project);
        if (!corrected.equals(rawPath)) {
            log.warn("SourceRootResolver: bare package path detected — '{}' → '{}'", rawPath, corrected);
        }
        return corrected;
    }

    /**
     * Returns the source root paths relative to the project root, sorted by preference
     * (non-test first, then by path length). Intended for injection into the Planner prompt
     * so the LLM can produce correct file paths without guessing or using placeholders.
     *
     * <p>Example output: {@code ["src/main/java", "src/test/java"]}
     */
    public static List<String> sourceRootRelativePaths(Project project) {
        String basePath = project.getBasePath();
        if (basePath == null) return List.of();
        return sortByPreference(
                Arrays.asList(ProjectRootManager.getInstance(project).getContentSourceRoots()))
                .stream()
                .map(r -> r.getPath().replace(basePath + "/", ""))
                .toList();
    }

    /**
     * For FILE_READ / FILE_EDIT: when a file is not found at {@code rawPath}, search
     * all content source roots as prefixes.
     *
     * @return the corrected relative path (from project root) if the file is found in a
     *         source root, or {@code null} if not found anywhere
     */
    public static String findInSourceRoots(String rawPath, Project project) {
        if (project == null || rawPath == null) return null;
        String basePath = project.getBasePath();
        if (basePath == null) return null;

        VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentSourceRoots();
        for (VirtualFile root : sortByPreference(Arrays.asList(roots))) {
            String candidate = root.getPath() + "/" + rawPath;
            VirtualFile found = LocalFileSystem.getInstance().refreshAndFindFileByPath(candidate);
            if (found != null && found.exists()) {
                String relative = candidate.replace(basePath + "/", "");
                log.warn("SourceRootResolver: '{}' not found at root, resolved to '{}'", rawPath, relative);
                return relative;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Package-path detection (package-private for tests)
    // -------------------------------------------------------------------------

    /**
     * Returns the package path (e.g. {@code "com/example/service"}) if {@code rawPath}
     * is a bare package path whose parent directory matches the declared package.
     * Returns {@code null} otherwise.
     */
    static String extractMatchingPackagePath(String rawPath, String content) {
        if (rawPath == null || content == null) return null;
        boolean isSourceFile = SOURCE_EXTENSIONS.stream().anyMatch(rawPath::endsWith);
        if (!isSourceFile) return null;

        Matcher m = PACKAGE_PATTERN.matcher(content);
        if (!m.find()) return null;

        String packagePath = m.group(1).replace('.', '/');
        // The parent directory of rawPath must exactly equal the declared package path.
        Path parentPath = Path.of(rawPath).getParent();
        String parent = parentPath != null ? parentPath.toString().replace('\\', '/') : "";
        return parent.equals(packagePath) ? packagePath : null;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static String prependBestSourceRoot(String rawPath, Project project) {
        String basePath = project.getBasePath();
        if (basePath == null) return rawPath;

        List<VirtualFile> roots = sortByPreference(
                Arrays.asList(ProjectRootManager.getInstance(project).getContentSourceRoots()));
        if (roots.isEmpty()) return rawPath;

        VirtualFile chosen = roots.get(0);
        return chosen.getPath().replace(basePath + "/", "") + "/" + rawPath;
    }

    /**
     * Sorts source roots so non-test roots come first (prefer {@code src/main/java} over
     * {@code src/test/java}). Within each group, roots are ordered by path length (shorter
     * first, so {@code src/main/java} beats {@code src/main/java/generated}).
     */
    private static List<VirtualFile> sortByPreference(List<VirtualFile> roots) {
        return roots.stream()
                .filter(r -> r.isValid() && r.isDirectory())
                .sorted(Comparator
                        .comparingInt((VirtualFile r) -> r.getPath().contains("test") ? 1 : 0)
                        .thenComparingInt(r -> r.getPath().length()))
                .toList();
    }
}
