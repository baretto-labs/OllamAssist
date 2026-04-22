package fr.baretto.ollamassist.agent;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.agent.tools.ToolApprovalHelper;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import fr.baretto.ollamassist.agent.tools.files.DeleteFileTool;
import fr.baretto.ollamassist.agent.tools.files.EditFileTool;
import fr.baretto.ollamassist.agent.tools.files.ReadFileTool;
import fr.baretto.ollamassist.agent.tools.files.WriteFileTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Parameter validation tests for file tools.
 *
 * These tests cover only the validation layer — the part that does not require
 * the IntelliJ Platform (no VirtualFile, no WriteAction).
 * Platform-dependent behaviour (actual file read/write/delete) is covered by
 * manual testing and the integration scenarios in AgentLoopIntegrationTest.
 */
class FileToolsParamValidationTest {

    private Project mockProject;

    @BeforeEach
    void setUp() {
        mockProject = mock(Project.class);
        when(mockProject.getBasePath()).thenReturn(System.getProperty("java.io.tmpdir"));
    }

    // =========================================================================
    // ReadFileTool
    // =========================================================================

    @Test
    void readFile_toolId_isFileRead() {
        assertThat(new ReadFileTool(mockProject).toolId()).isEqualTo("FILE_READ");
    }

    @Test
    void readFile_missingPath_returnsFailure() {
        ToolResult result = new ReadFileTool(mockProject).execute(Map.of());
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("path");
    }

    @Test
    void readFile_blankPath_returnsFailure() {
        ToolResult result = new ReadFileTool(mockProject).execute(Map.of("path", "  "));
        assertThat(result.isSuccess()).isFalse();
    }

    // Note: file-not-found case requires LocalFileSystem (Platform API) and is
    // therefore not testable in unit tests. Covered by manual testing.

    // =========================================================================
    // WriteFileTool
    // =========================================================================

    @Test
    void writeFile_toolId_isFileWrite() {
        assertThat(new WriteFileTool(mockProject).toolId()).isEqualTo("FILE_WRITE");
    }

    @Test
    void writeFile_missingPath_returnsFailure() {
        ToolResult result = new WriteFileTool(mockProject).execute(Map.of("content", "body"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("path");
    }

    @Test
    void writeFile_blankPath_returnsFailure() {
        ToolResult result = new WriteFileTool(mockProject).execute(Map.of("path", "  ", "content", "body"));
        assertThat(result.isSuccess()).isFalse();
    }

    // =========================================================================
    // EditFileTool
    // =========================================================================

    @Test
    void editFile_toolId_isFileEdit() {
        assertThat(new EditFileTool(mockProject).toolId()).isEqualTo("FILE_EDIT");
    }

    @Test
    void editFile_missingPath_returnsFailure() {
        ToolResult result = new EditFileTool(mockProject).execute(
                Map.of("search", "old", "replace", "new"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("path");
    }

    @Test
    void editFile_blankPath_returnsFailure() {
        ToolResult result = new EditFileTool(mockProject).execute(
                Map.of("path", "  ", "search", "old", "replace", "new"));
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void editFile_missingSearch_returnsFailure() {
        ToolResult result = new EditFileTool(mockProject).execute(
                Map.of("path", "Foo.java", "replace", "new"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("search");
    }

    @Test
    void editFile_missingReplace_returnsFailure() {
        ToolResult result = new EditFileTool(mockProject).execute(
                Map.of("path", "Foo.java", "search", "old"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("replace");
    }

    @Test
    void editFile_replaceAllParam_isOptional() {
        // replaceAll is optional — omitting it must not trigger a param validation error.
        // Using an absolute path outside the project root to trigger FilePathGuard
        // before hitting the IntelliJ Platform LocalFileSystem (unavailable in unit tests).
        ToolResult result = new EditFileTool(mockProject).execute(
                Map.of("path", "/etc/passwd", "search", "old", "replace", "new"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).doesNotContainIgnoringCase("replaceAll");
    }

    @Test
    void editFile_replaceAllFalse_doesNotFailValidation() {
        ToolResult result = new EditFileTool(mockProject).execute(
                Map.of("path", "/etc/passwd", "search", "old", "replace", "new", "replaceAll", false));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).doesNotContainIgnoringCase("replaceAll");
    }

    @Test
    void editFile_replaceAllTrue_doesNotFailValidation() {
        ToolResult result = new EditFileTool(mockProject).execute(
                Map.of("path", "/etc/passwd", "search", "old", "replace", "new", "replaceAll", true));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).doesNotContainIgnoringCase("replaceAll");
    }

    // Note: actual first-vs-all replacement behaviour requires LocalFileSystem (Platform API).

    // =========================================================================
    // DeleteFileTool
    // =========================================================================

    @Test
    void deleteFile_toolId_isFileDelete() {
        assertThat(new DeleteFileTool(mockProject).toolId()).isEqualTo("FILE_DELETE");
    }

    @Test
    void deleteFile_missingPath_returnsFailure() {
        ToolResult result = new DeleteFileTool(mockProject).execute(Map.of());
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("path");
    }

    @Test
    void deleteFile_blankPath_returnsFailure() {
        ToolResult result = new DeleteFileTool(mockProject).execute(Map.of("path", "  "));
        assertThat(result.isSuccess()).isFalse();
    }

    // Note: file-not-found case requires LocalFileSystem (Platform API).

    // =========================================================================
    // T-4 — Symlink escape blocked at each file tool (SI-2 / A3)
    //
    // These tests verify that a symlinked directory inside the project pointing
    // to an external location is blocked by FilePathGuard before any I/O occurs,
    // and that each tool surfaces the rejection as ToolResult.failure.
    // =========================================================================

    private static Project projectWithRoot(Path root) {
        Project p = mock(Project.class);
        when(p.getBasePath()).thenReturn(root.toString());
        return p;
    }

    @Test
    void writeFile_symlinkDirPointingOutside_returnsFailure(
            @TempDir Path projectRoot, @TempDir Path externalDir) throws Exception {
        Path symlink = projectRoot.resolve("ext");
        Files.createSymbolicLink(symlink, externalDir);

        ToolResult result = new WriteFileTool(projectWithRoot(projectRoot))
                .execute(Map.of("path", "ext/secret.txt", "content", "evil"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("project root");
    }

    @Test
    void readFile_symlinkDirPointingOutside_returnsFailure(
            @TempDir Path projectRoot, @TempDir Path externalDir) throws Exception {
        Path symlink = projectRoot.resolve("ext");
        Files.createSymbolicLink(symlink, externalDir);
        // Place a real file in externalDir so the symlink target exists
        Files.writeString(externalDir.resolve("secret.txt"), "secret");

        ToolResult result = new ReadFileTool(projectWithRoot(projectRoot))
                .execute(Map.of("path", "ext/secret.txt"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("project root");
    }

    @Test
    void editFile_symlinkDirPointingOutside_returnsFailure(
            @TempDir Path projectRoot, @TempDir Path externalDir) throws Exception {
        Path symlink = projectRoot.resolve("ext");
        Files.createSymbolicLink(symlink, externalDir);

        ToolResult result = new EditFileTool(projectWithRoot(projectRoot))
                .execute(Map.of("path", "ext/secret.txt", "search", "a", "replace", "b"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("project root");
    }

    @Test
    void deleteFile_symlinkDirPointingOutside_returnsFailure(
            @TempDir Path projectRoot, @TempDir Path externalDir) throws Exception {
        Path symlink = projectRoot.resolve("ext");
        Files.createSymbolicLink(symlink, externalDir);

        ToolResult result = new DeleteFileTool(projectWithRoot(projectRoot))
                .execute(Map.of("path", "ext/secret.txt"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("project root");
    }
}
