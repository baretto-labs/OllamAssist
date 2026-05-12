package fr.baretto.ollamassist.agent.tools.files;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link EditFileTool} and {@link WriteFileTool} respect the
 * {@link PsiSyntaxValidator} contract:
 * <ul>
 *   <li>When the validator reports a syntax error, the tool returns {@link ToolResult#failure}
 *       and does NOT write the file.</li>
 *   <li>When the validator reports no error, execution continues normally.</li>
 * </ul>
 *
 * IntelliJ Platform statics (LocalFileSystem, FilePathGuard) are mocked so these
 * tests run without a full IDE runtime.
 */
class FileSyntaxValidationTest {

    private Project mockProject;
    private LocalFileSystem mockLfs;
    private VirtualFile mockFile;

    @BeforeEach
    void setUp() throws IOException {
        mockProject = mock(Project.class);

        mockFile = mock(VirtualFile.class);
        when(mockFile.exists()).thenReturn(true);
        when(mockFile.getName()).thenReturn("PingController.java");
        when(mockFile.contentsToByteArray()).thenReturn(
                "public class PingController {\n    @GetMapping(\"/ping\")\n    public String ping() { return \"Pong\"; }\n}".getBytes(StandardCharsets.UTF_8));

        mockLfs = mock(LocalFileSystem.class);
        when(mockLfs.refreshAndFindFileByPath(anyString())).thenReturn(mockFile);
    }

    // -------------------------------------------------------------------------
    // EditFileTool — syntax error blocks the write
    // -------------------------------------------------------------------------

    @Test
    void editFileTool_syntaxError_returnsFailureWithoutWriting() throws IOException {
        try (MockedStatic<LocalFileSystem> lfsStatic = mockStatic(LocalFileSystem.class);
             MockedStatic<FilePathGuard> fpgStatic = mockStatic(FilePathGuard.class)) {

            lfsStatic.when(LocalFileSystem::getInstance).thenReturn(mockLfs);
            fpgStatic.when(() -> FilePathGuard.resolveConfined(anyString(), any(Project.class)))
                     .thenReturn(Path.of("/project/src/PingController.java"));

            PsiSyntaxValidator failingValidator =
                    PsiSyntaxValidator.alwaysError("'class' or 'interface' expected");
            EditFileTool tool = new EditFileTool(mockProject, failingValidator);

            ToolResult result = tool.execute(Map.of(
                    "path", "src/PingController.java",
                    "search", "public String ping()",
                    "replace", "public String ping()\n}\n\n@GetMapping(\"/v2/health\")\npublic boolean checkHealth()"
            ));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage())
                    .contains("Syntax error in the proposed edit")
                    .contains("'class' or 'interface' expected");
            // File must NOT have been written
            verify(mockFile, never()).setBinaryContent(any());
        }
    }

    @Test
    void editFileTool_syntaxError_errorMessageMentionsFixInstruction() throws IOException {
        try (MockedStatic<LocalFileSystem> lfsStatic = mockStatic(LocalFileSystem.class);
             MockedStatic<FilePathGuard> fpgStatic = mockStatic(FilePathGuard.class)) {

            lfsStatic.when(LocalFileSystem::getInstance).thenReturn(mockLfs);
            fpgStatic.when(() -> FilePathGuard.resolveConfined(anyString(), any(Project.class)))
                     .thenReturn(Path.of("/project/src/PingController.java"));

            EditFileTool tool = new EditFileTool(mockProject,
                    PsiSyntaxValidator.alwaysError("unexpected token '}'"));

            ToolResult result = tool.execute(Map.of(
                    "path", "src/PingController.java",
                    "search", "public String ping()",
                    "replace", "malformed code {"
            ));

            // The error message must guide the Critic to fix the replace param
            assertThat(result.getErrorMessage())
                    .containsIgnoringCase("fix")
                    .containsIgnoringCase("search")
                    .containsIgnoringCase("replace");
        }
    }

    @Test
    void editFileTool_validSyntax_doesNotReturnEarly() throws IOException {
        // When the validator reports no error, execute() must proceed past validation.
        // Here it will fail at the approval dialog (no approval helper injected),
        // but it must NOT fail with a "Syntax error" message.
        try (MockedStatic<LocalFileSystem> lfsStatic = mockStatic(LocalFileSystem.class);
             MockedStatic<FilePathGuard> fpgStatic = mockStatic(FilePathGuard.class)) {

            lfsStatic.when(LocalFileSystem::getInstance).thenReturn(mockLfs);
            fpgStatic.when(() -> FilePathGuard.resolveConfined(anyString(), any(Project.class)))
                     .thenReturn(Path.of("/project/src/PingController.java"));

            EditFileTool tool = new EditFileTool(mockProject, PsiSyntaxValidator.alwaysValid());

            ToolResult result = tool.execute(Map.of(
                    "path", "src/PingController.java",
                    "search", "public String ping()",
                    "replace", "public String ping() { return \"updated\"; }"
            ));

            // Must not be a syntax-error failure
            if (!result.isSuccess()) {
                assertThat(result.getErrorMessage()).doesNotContain("Syntax error in the proposed edit");
            }
        }
    }

    @Test
    void editFileTool_searchStringNotFound_neverCallsValidator() throws IOException {
        // If the search string is not in the file, validation must not be reached.
        // spy() cannot instrument lambdas (JVM restriction), so we track calls with AtomicBoolean.
        try (MockedStatic<LocalFileSystem> lfsStatic = mockStatic(LocalFileSystem.class);
             MockedStatic<FilePathGuard> fpgStatic = mockStatic(FilePathGuard.class)) {

            lfsStatic.when(LocalFileSystem::getInstance).thenReturn(mockLfs);
            fpgStatic.when(() -> FilePathGuard.resolveConfined(anyString(), any(Project.class)))
                     .thenReturn(Path.of("/project/src/PingController.java"));

            AtomicBoolean validatorCalled = new AtomicBoolean(false);
            PsiSyntaxValidator trackingValidator = (fileName, content) -> {
                validatorCalled.set(true);
                return Optional.empty();
            };
            EditFileTool tool = new EditFileTool(mockProject, trackingValidator);

            tool.execute(Map.of(
                    "path", "src/PingController.java",
                    "search", "THIS_STRING_DOES_NOT_EXIST",
                    "replace", "irrelevant"
            ));

            assertThat(validatorCalled.get())
                    .as("Validator must not be called when the search string is absent")
                    .isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // WriteFileTool — syntax error blocks the write
    // -------------------------------------------------------------------------

    @Test
    void writeFileTool_syntaxError_returnsFailureWithoutCreatingFile() throws IOException {
        try (MockedStatic<LocalFileSystem> lfsStatic = mockStatic(LocalFileSystem.class);
             MockedStatic<FilePathGuard> fpgStatic = mockStatic(FilePathGuard.class)) {

            // File does not exist yet (write creates it)
            when(mockLfs.refreshAndFindFileByPath(anyString())).thenReturn(null);
            lfsStatic.when(LocalFileSystem::getInstance).thenReturn(mockLfs);
            fpgStatic.when(() -> FilePathGuard.resolveConfined(anyString(), any(Project.class)))
                     .thenReturn(Path.of("/project/src/NewController.java"));

            PsiSyntaxValidator failingValidator =
                    PsiSyntaxValidator.alwaysError("method outside class body");
            WriteFileTool tool = new WriteFileTool(mockProject, failingValidator);

            ToolResult result = tool.execute(Map.of(
                    "path", "src/NewController.java",
                    "content",
                    // Simulates qwen3's broken output: method placed after closing brace
                    "public class NewController {\n}\n\n@GetMapping(\"/health\")\npublic boolean health() { return true; }"
            ));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage())
                    .contains("Syntax error in the proposed content")
                    .contains("method outside class body");
        }
    }

    @Test
    void writeFileTool_validSyntax_doesNotReturnEarlyWithSyntaxError() throws IOException {
        // With alwaysValid, execution must proceed past the syntax check.
        // WriteFileTool's approval dialog throws NPE in a headless test (no MessageBus stub).
        // That NPE proves validation was passed — we verify it is not syntax-related.
        try (MockedStatic<LocalFileSystem> lfsStatic = mockStatic(LocalFileSystem.class);
             MockedStatic<FilePathGuard> fpgStatic = mockStatic(FilePathGuard.class)) {

            when(mockLfs.refreshAndFindFileByPath(anyString())).thenReturn(null);
            lfsStatic.when(LocalFileSystem::getInstance).thenReturn(mockLfs);
            fpgStatic.when(() -> FilePathGuard.resolveConfined(anyString(), any(Project.class)))
                     .thenReturn(Path.of("/project/src/NewController.java"));

            WriteFileTool tool = new WriteFileTool(mockProject, PsiSyntaxValidator.alwaysValid());

            try {
                ToolResult result = tool.execute(Map.of(
                        "path", "src/NewController.java",
                        "content", "public class NewController {}"
                ));
                // If execute() returns (no throw), the failure must not be a syntax error
                if (!result.isSuccess()) {
                    assertThat(result.getErrorMessage())
                            .doesNotContain("Syntax error in the proposed content");
                }
            } catch (NullPointerException e) {
                // NPE from approval dialog (project.getMessageBus() = null in headless tests).
                // This means we got PAST the syntax check — which is the objective.
            }
        }
    }

    // -------------------------------------------------------------------------
    // Validator receives correct arguments
    // -------------------------------------------------------------------------

    @Test
    void editFileTool_validatorReceivesFilenameAndModifiedContent() throws IOException {
        try (MockedStatic<LocalFileSystem> lfsStatic = mockStatic(LocalFileSystem.class);
             MockedStatic<FilePathGuard> fpgStatic = mockStatic(FilePathGuard.class)) {

            lfsStatic.when(LocalFileSystem::getInstance).thenReturn(mockLfs);
            fpgStatic.when(() -> FilePathGuard.resolveConfined(anyString(), any(Project.class)))
                     .thenReturn(Path.of("/project/src/PingController.java"));

            PsiSyntaxValidator captureValidator = mock(PsiSyntaxValidator.class);
            when(captureValidator.validate(anyString(), anyString()))
                    .thenReturn(java.util.Optional.empty());
            EditFileTool tool = new EditFileTool(mockProject, captureValidator);

            tool.execute(Map.of(
                    "path", "src/PingController.java",
                    "search", "return \"Pong\";",
                    "replace", "return \"Pong v2\";"
            ));

            verify(captureValidator).validate(
                    eq("PingController.java"),
                    // Modified content must contain the replacement
                    argThat(content -> content.contains("Pong v2"))
            );
        }
    }
}
