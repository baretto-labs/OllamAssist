package fr.baretto.ollamassist.agent.tools.files;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.agent.tools.ToolApprovalHelper;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppendFileToolTest {

    @Test
    void toolId_isFileAppend() {
        Project project = mock(Project.class);
        when(project.getBasePath()).thenReturn(System.getProperty("java.io.tmpdir"));
        AppendFileTool tool = new AppendFileTool(project);
        assertThat(tool.toolId()).isEqualTo("FILE_APPEND");
    }

    @Test
    void missingPath_returnsFailure() {
        Project project = mock(Project.class);
        when(project.getBasePath()).thenReturn(System.getProperty("java.io.tmpdir"));
        AppendFileTool tool = new AppendFileTool(project);
        ToolResult result = tool.execute(Map.of("content", "hello"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("'path' is required");
    }

    @Test
    void missingContent_returnsFailure() {
        Project project = mock(Project.class);
        when(project.getBasePath()).thenReturn(System.getProperty("java.io.tmpdir"));
        AppendFileTool tool = new AppendFileTool(project);
        ToolResult result = tool.execute(Map.of("path", "some/file.txt"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("'content' is required");
    }

    @Test
    void pathTraversal_isBlocked() {
        Project project = mock(Project.class);
        when(project.getBasePath()).thenReturn(System.getProperty("java.io.tmpdir"));
        AppendFileTool tool = new AppendFileTool(project);
        ToolResult result = tool.execute(Map.of("path", "../../etc/passwd", "content", "evil"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Security");
    }

    @Test
    void secretInContent_isBlocked() {
        Project project = mock(Project.class);
        when(project.getBasePath()).thenReturn(System.getProperty("java.io.tmpdir"));
        AppendFileTool tool = new AppendFileTool(project);
        String secretContent = "-----BEGIN RSA PRIVATE KEY-----\nABC123\n-----END RSA PRIVATE KEY-----";
        ToolResult result = tool.execute(Map.of("path", "file.txt", "content", secretContent));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("secret");
    }
}
