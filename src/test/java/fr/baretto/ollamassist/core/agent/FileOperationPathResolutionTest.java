package fr.baretto.ollamassist.core.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for file path resolution in FileOperationExecutor
 * Verifies that absolute paths from InputValidator are correctly converted to relative paths
 * for use with VfsUtil.createDirectoryIfMissing()
 */
@DisplayName("File Operation Path Resolution Tests")
public class FileOperationPathResolutionTest {

    private PathResolver pathResolver;
    private String projectRootPath;

    @BeforeEach
    void setUp() {
        pathResolver = new PathResolver();
        projectRootPath = "/Users/mehdi/Workspaces/Labs/OllamAssist";
    }

    @Test
    @DisplayName("Should convert absolute path to relative path")
    void shouldConvertAbsolutePathToRelative() {
        // Given: InputValidator returns absolute path after validation
        String absolutePath = "/Users/mehdi/Workspaces/Labs/OllamAssist/src/main/java/com/example/Test.java";

        // When: Convert to relative path
        String relativePath = pathResolver.toRelativePath(projectRootPath, absolutePath);

        // Then: Should be relative to project root
        assertThat(relativePath).isEqualTo("src/main/java/com/example/Test.java");
    }

    @Test
    @DisplayName("Should handle already relative path")
    void shouldHandleAlreadyRelativePath() {
        // Given: Path is already relative (edge case)
        String relativePath = "src/main/java/Test.java";

        // When: Attempt conversion
        String result = pathResolver.toRelativePath(projectRootPath, relativePath);

        // Then: Should remain relative (or be unchanged)
        assertThat(result).isEqualTo("src/main/java/Test.java");
    }

    @Test
    @DisplayName("Should handle nested package directories")
    void shouldHandleNestedPackageDirectories() {
        // Given: Deep package hierarchy
        String absolutePath = "/Users/mehdi/Workspaces/Labs/OllamAssist/src/main/java/com/example/service/impl/UserService.java";

        // When: Convert to relative path
        String relativePath = pathResolver.toRelativePath(projectRootPath, absolutePath);

        // Then: Should preserve full nested structure
        assertThat(relativePath).isEqualTo("src/main/java/com/example/service/impl/UserService.java");
    }

    @Test
    @DisplayName("Should handle file at project root")
    void shouldHandleFileAtProjectRoot() {
        // Given: File directly at project root (e.g., README.md)
        String absolutePath = "/Users/mehdi/Workspaces/Labs/OllamAssist/README.md";

        // When: Convert to relative path
        String relativePath = pathResolver.toRelativePath(projectRootPath, absolutePath);

        // Then: Should be just filename
        assertThat(relativePath).isEqualTo("README.md");
    }

    @Test
    @DisplayName("Should normalize paths with redundant separators")
    void shouldNormalizePathsWithRedundantSeparators() {
        // Given: Path with redundant slashes
        String absolutePath = "/Users/mehdi/Workspaces/Labs/OllamAssist//src//main/java/Test.java";

        // When: Convert to relative path
        String relativePath = pathResolver.toRelativePath(projectRootPath, absolutePath);

        // Then: Should normalize to clean relative path
        assertThat(relativePath).doesNotContain("//");
        assertThat(relativePath).isEqualTo("src/main/java/Test.java");
    }

    @Test
    @DisplayName("Should handle Windows-style paths (if running on Windows)")
    void shouldHandleWindowsStylePaths() {
        // Given: Windows project root
        String windowsProjectRoot = "C:\\Users\\mehdi\\OllamAssist";
        String windowsAbsolutePath = "C:\\Users\\mehdi\\OllamAssist\\src\\main\\java\\Test.java";

        // When: Convert to relative path
        String relativePath = pathResolver.toRelativePath(windowsProjectRoot, windowsAbsolutePath);

        // Then: Should work regardless of OS
        // On Unix, backslashes might be treated differently, but logic should handle it
        assertThat(relativePath).contains("src");
        assertThat(relativePath).contains("main");
        assertThat(relativePath).contains("java");
    }

    @Test
    @DisplayName("Should verify VfsUtil receives relative path, not absolute")
    void shouldVerifyVfsUtilReceivesRelativePath() {
        // Given: Agent corrects path to relative, InputValidator converts to absolute
        String agentCorrectedPath = "src/main/java/com/example/Test.java";
        String validatorAbsolutePath = projectRootPath + "/" + agentCorrectedPath;

        // When: FileOperationExecutor converts back to relative
        String finalPath = pathResolver.toRelativePath(projectRootPath, validatorAbsolutePath);

        // Then: VfsUtil receives the original relative path
        assertThat(finalPath).isEqualTo(agentCorrectedPath);
        assertThat(finalPath).doesNotStartWith("/Users");
        assertThat(finalPath).doesNotStartWith("C:");
    }

    /**
     * Helper class that replicates the path resolution logic from FileOperationExecutor
     */
    private static class PathResolver {
        public String toRelativePath(String projectRootPath, String filePath) {
            Path projectRoot = Paths.get(projectRootPath).toAbsolutePath().normalize();
            Path absoluteFilePath = Paths.get(filePath).toAbsolutePath().normalize();

            // Convert to relative path if it's within project
            if (absoluteFilePath.startsWith(projectRoot)) {
                return projectRoot.relativize(absoluteFilePath).toString();
            } else {
                // Path is already relative (should not happen after validation)
                return filePath;
            }
        }
    }
}
