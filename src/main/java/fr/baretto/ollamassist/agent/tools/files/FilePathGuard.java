package fr.baretto.ollamassist.agent.tools.files;

import com.intellij.openapi.project.Project;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves and confines file paths to the project root directory.
 *
 * <p>Prevents path traversal attacks: any path that resolves outside
 * {@code project.getBasePath()} (e.g. {@code /etc/passwd}, {@code ../../.ssh/id_rsa})
 * is rejected with a {@link PathTraversalException}.
 *
 * <p>All file tools must call {@link #resolveConfined} instead of
 * implementing their own {@code resolveAbsolute} logic.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FilePathGuard {

    /**
     * Thrown when a path resolves to a location outside the project root.
     */
    public static final class PathTraversalException extends SecurityException {
        public PathTraversalException(String path, String basePath) {
            super("Security: path '" + path + "' resolves outside project root '" + basePath + "'");
        }
    }

    /**
     * Resolves {@code path} relative to {@code project.getBasePath()} and
     * verifies that the result stays within the project root.
     *
     * @param path    a relative or absolute path string
     * @param project the current IntelliJ project
     * @return the normalized, confined absolute path
     * @throws PathTraversalException  if the path escapes the project root
     * @throws IllegalStateException   if the project base path is unavailable
     * @throws IllegalArgumentException if {@code path} is null
     */
    public static Path resolveConfined(String path, Project project) {
        if (project == null || project.getBasePath() == null || project.getBasePath().isBlank()) {
            throw new IllegalStateException("Project base path is not available");
        }
        return resolveConfined(path, project.getBasePath());
    }

    /**
     * Resolves {@code path} relative to {@code basePath} and verifies confinement.
     * Exposed as package-visible for unit testing without an IntelliJ Platform runtime.
     *
     * @param path     a relative or absolute path string
     * @param basePath the project root as a string
     * @return the normalized, confined absolute path
     */
    public static Path resolveConfined(String path, String basePath) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (basePath == null || basePath.isBlank()) {
            throw new IllegalStateException("Project base path is not available");
        }

        Path root = Paths.get(basePath).normalize().toAbsolutePath();
        Path resolved = Paths.get(path).isAbsolute()
                ? Paths.get(path).normalize()
                : root.resolve(path).normalize();

        if (!resolved.startsWith(root)) {
            throw new PathTraversalException(path, basePath);
        }

        // Follow the full symlink chain to prevent escape via intermediate symlinks
        // (e.g. A → B → /etc/passwd).
        try {
            Path realResolved = resolved.toRealPath();
            Path realRoot;
            try {
                realRoot = root.toRealPath();
            } catch (IOException e2) {
                realRoot = root;
            }
            if (!realResolved.startsWith(realRoot)) {
                throw new PathTraversalException(path, basePath);
            }
        } catch (IOException e) {
            // The target file does not exist yet (valid for write operations).
            // Walk up ancestor directories until we find one that exists, then verify
            // its real path is still within the project root.  This prevents a symlink
            // in an ancestor directory from escaping confinement when the target file
            // is created for the first time (SI-1 / A3).
            checkAncestorConfinement(resolved, root, path, basePath);
        }

        return resolved;
    }

    /**
     * Walks up {@code path}'s ancestor chain until it finds a directory that exists on disk,
     * then verifies that its real path (after resolving all symlinks) is still within {@code root}.
     * This guards against a symlinked parent directory escaping the project boundary.
     */
    private static void checkAncestorConfinement(Path path, Path root, String originalPath, String basePath) {
        Path realRoot;
        try {
            realRoot = root.toRealPath();
        } catch (IOException e) {
            realRoot = root;
        }
        Path ancestor = path.getParent();
        while (ancestor != null) {
            try {
                Path realAncestor = ancestor.toRealPath();
                if (!realAncestor.startsWith(realRoot)) {
                    throw new PathTraversalException(originalPath, basePath);
                }
                return; // Found an existing, confined ancestor — we're good.
            } catch (PathTraversalException ex) {
                throw ex; // Re-throw security exception immediately.
            } catch (IOException ex) {
                // This ancestor doesn't exist either; go up one more level.
                ancestor = ancestor.getParent();
            }
        }
    }
}
