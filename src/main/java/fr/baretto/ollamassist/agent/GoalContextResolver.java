package fr.baretto.ollamassist.agent;

import com.intellij.openapi.project.Project;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code @ClassName} references in an agent goal by locating the matching source file
 * in the project and prepending its content as context.
 *
 * <p>Example input: {@code "Refactor @OrderService to use @PaymentGateway"}
 * <br>The resolver finds {@code OrderService.java} and {@code PaymentGateway.java}, reads their
 * content, and prepends it to the goal before it is sent to the PlannerAgent.
 *
 * <p>Unresolved references (file not found) are silently skipped — the goal is returned unchanged
 * for those references. This is intentional: the planner should still attempt the goal using
 * FILE_FIND as a fallback.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GoalContextResolver {

    /** Matches {@code @Word} or {@code @Word.java} — stops at whitespace and common punctuation. */
    private static final Pattern AT_REF_PATTERN = Pattern.compile("@([\\w.]+)");

    /** Maximum characters of content to inject per file — prevents blowing up the planner context. */
    static final int MAX_FILE_CHARS = 4_000;

    /**
     * Maximum number of distinct {@code @ClassName} references resolved per goal.
     * Each reference triggers a {@code Files.walkFileTree} — unbounded resolution
     * on large projects would freeze the IDE.
     */
    static final int MAX_AT_REFS = 10;

    /** Boundary string that marks the end of injected context in the planner prompt. */
    private static final String CONTEXT_BOUNDARY = "--- (end of injected context) ---";
    /** Replacement used when the boundary string appears literally inside an injected file. */
    private static final String ESCAPED_BOUNDARY  = "--- (end of injected context [escaped]) ---";

    private static final String[] SKIP_DIRS = {".git", "build", "out", ".ollamassist", "target"};

    /**
     * Resolves all {@code @ClassName} references in {@code goal} and returns an enriched goal
     * with the matched file contents prepended as context.
     *
     * @param goal    the raw goal text entered by the user
     * @param project the current IntelliJ project (used to locate the project root)
     * @return the goal with file contents prepended, or the original goal if no references are found
     */
    public static String resolve(String goal, Project project) {
        if (project == null || project.getBasePath() == null) return goal;

        Matcher m = AT_REF_PATTERN.matcher(goal);
        Map<String, String> resolved = new LinkedHashMap<>();

        while (m.find()) {
            String ref = m.group(1); // e.g. "OrderService" or "OrderService.java"
            if (resolved.containsKey(ref)) continue;
            if (resolved.size() >= MAX_AT_REFS) {
                log.warn("GoalContextResolver: @-reference limit ({}) reached — remaining references ignored", MAX_AT_REFS);
                break;
            }

            String fileName = ref.endsWith(".java") ? ref : ref + ".java";
            String content = findAndRead(fileName, project.getBasePath());
            if (content == null) {
                // Fall back to implementation-name search (A-5):
                // @OrderService → try OrderServiceImpl.java, then any *OrderService*.java
                String implName = ref.endsWith(".java")
                        ? ref.replace(".java", "Impl.java")
                        : ref + "Impl.java";
                content = findAndRead(implName, project.getBasePath());
                if (content == null) {
                    // Broader fallback: any .java file whose stem contains the reference
                    String stem = ref.endsWith(".java") ? ref.substring(0, ref.length() - 5) : ref;
                    content = findContaining(stem, project.getBasePath());
                }
            }
            if (content != null) {
                // Escape the context boundary string so a malicious file cannot close the
                // injected-context block early and have subsequent content treated as instructions.
                content = content.replace(CONTEXT_BOUNDARY, ESCAPED_BOUNDARY);
                resolved.put(ref, content);
                log.debug("GoalContextResolver: resolved @{} → {} chars", ref, content.length());
            } else {
                log.debug("GoalContextResolver: @{} not found — skipped", ref);
            }
        }

        if (resolved.isEmpty()) return goal;

        StringBuilder preamble = new StringBuilder(
                "Context files referenced with @ (injected automatically):\n\n");
        resolved.forEach((ref, content) -> {
            preamble.append("--- @").append(ref).append(" ---\n");
            preamble.append(content).append("\n\n");
        });
        preamble.append(CONTEXT_BOUNDARY).append("\n\n");

        return preamble + goal;
    }

    /**
     * Walks the project file tree looking for the first {@code .java} file whose
     * simple name (without extension) contains {@code stem} (case-insensitive).
     *
     * <p>Used as a broader fallback when neither the exact name ({@code XService.java})
     * nor the implementation convention ({@code XServiceImpl.java}) yields a match (A-5).
     * Matches: {@code AbstractOrderService}, {@code DefaultOrderService}, etc.
     *
     * @return the (possibly truncated) file content, or {@code null} if no match
     */
    /**
     * Resolves the project root to its canonical real path (following symlinks).
     * Required so that {@code realPath.startsWith(root)} works correctly on macOS/Linux
     * where temp directories may themselves be symlinks (e.g. {@code /var} → {@code /private/var}).
     * Returns {@code null} if the path cannot be resolved (I/O error → fail-closed: skip the walk).
     */
    private static Path resolveRoot(String basePath) {
        try {
            return Paths.get(basePath).toRealPath();
        } catch (IOException e) {
            log.warn("GoalContextResolver: cannot resolve project root '{}': {}", basePath, e.getMessage());
            return null;
        }
    }

    private static String findContaining(String stem, String basePath) {
        if (stem == null || stem.isBlank()) return null;
        String lowerStem = stem.toLowerCase(java.util.Locale.ROOT);
        Path root = resolveRoot(basePath);
        if (root == null) return null;
        String[] result = {null};
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    for (String skip : SKIP_DIRS) {
                        if (skip.equals(name)) return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String name = file.getFileName().toString();
                    if (!name.endsWith(".java")) return FileVisitResult.CONTINUE;
                    String stemCandidate = name.substring(0, name.length() - 5).toLowerCase(java.util.Locale.ROOT);
                    if (stemCandidate.contains(lowerStem)) {
                        try {
                            // SI-2 / A3: resolve symlinks before reading.
                            Path realPath = file.toRealPath();
                            if (!realPath.startsWith(root)) {
                                log.warn("GoalContextResolver: symlink escape rejected: {} → {}", file, realPath);
                                return FileVisitResult.CONTINUE;
                            }
                            byte[] bytes = Files.readAllBytes(realPath);
                            String text = new String(bytes, StandardCharsets.UTF_8);
                            result[0] = text.length() > MAX_FILE_CHARS
                                    ? text.substring(0, MAX_FILE_CHARS) + "\n... [content truncated]"
                                    : text;
                        } catch (IOException e) {
                            log.warn("GoalContextResolver: failed to read {}: {}", file, e.getMessage());
                        }
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("GoalContextResolver: file walk failed for stem '{}': {}", stem, e.getMessage());
        }
        return result[0];
    }

    /**
     * Walks the project file tree looking for a file named {@code fileName}.
     *
     * @return the (possibly truncated) file content, or {@code null} if not found
     */
    private static String findAndRead(String fileName, String basePath) {
        Path root = resolveRoot(basePath);
        if (root == null) return null;
        String[] result = {null};

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    for (String skip : SKIP_DIRS) {
                        if (skip.equals(name)) return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    // Case-insensitive comparison — macOS and Windows use case-insensitive
                    // filesystems by default. Exact-case matching silently fails on these
                    // platforms when the user types @orderService but the file is OrderService.java.
                    if (file.getFileName().toString().equalsIgnoreCase(fileName)) {
                        try {
                            // SI-2 / A3: resolve symlinks before reading — a symlink inside the project
                            // could point to a sensitive file outside (e.g. /etc/passwd).
                            Path realPath = file.toRealPath();
                            if (!realPath.startsWith(root)) {
                                log.warn("GoalContextResolver: symlink escape rejected: {} → {}", file, realPath);
                                return FileVisitResult.CONTINUE;
                            }
                            byte[] bytes = Files.readAllBytes(realPath);
                            String text = new String(bytes, StandardCharsets.UTF_8);
                            result[0] = text.length() > MAX_FILE_CHARS
                                    ? text.substring(0, MAX_FILE_CHARS) + "\n... [content truncated]"
                                    : text;
                        } catch (IOException e) {
                            log.warn("GoalContextResolver: failed to read {}: {}", file, e.getMessage());
                        }
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("GoalContextResolver: file walk failed for '{}': {}", fileName, e.getMessage());
        }

        return result[0];
    }
}
