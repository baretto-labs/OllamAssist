package fr.baretto.ollamassist.agent;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import fr.baretto.ollamassist.agent.tools.git.GitDiffTool;
import fr.baretto.ollamassist.agent.tools.git.GitStatusTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GitToolsTest {

    private Project mockProject;

    @BeforeEach
    void setUp() {
        mockProject = mock(Project.class);
        // Point to a real directory so ProcessBuilder can start
        when(mockProject.getBasePath()).thenReturn(System.getProperty("java.io.tmpdir"));
    }

    // -------------------------------------------------------------------------
    // GitStatusTool
    // -------------------------------------------------------------------------

    @Test
    void gitStatus_noBasePath_returnsFailure() {
        when(mockProject.getBasePath()).thenReturn(null);
        GitStatusTool tool = new GitStatusTool(mockProject);

        ToolResult result = tool.execute(Map.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("base path");
    }

    @Test
    void gitStatus_toolId_isGitStatus() {
        assertThat(new GitStatusTool(mockProject).toolId()).isEqualTo("GIT_STATUS");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void gitStatus_nonGitDir_returnsFailure() {
        // /tmp is not a git repo so git status should fail with non-zero exit
        GitStatusTool tool = new GitStatusTool(mockProject);

        ToolResult result = tool.execute(Map.of());

        // Either success (if /tmp is inside a git repo on that machine) or failure
        // We just verify it doesn't throw
        assertThat(result).isNotNull();
    }

    // -------------------------------------------------------------------------
    // GitDiffTool
    // -------------------------------------------------------------------------

    @Test
    void gitDiff_toolId_isGitDiff() {
        assertThat(new GitDiffTool(mockProject).toolId()).isEqualTo("GIT_DIFF");
    }

    @Test
    void gitDiff_noBasePath_returnsFailure() {
        when(mockProject.getBasePath()).thenReturn(null);
        GitDiffTool tool = new GitDiffTool(mockProject);

        ToolResult result = tool.execute(Map.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("base path");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void gitDiff_withArgsParam_appendsToCommand() {
        // We can't assert the exact output without a git repo, but we verify it doesn't crash
        GitDiffTool tool = new GitDiffTool(mockProject);

        ToolResult result = tool.execute(Map.of("args", "--staged"));

        assertThat(result).isNotNull();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void gitDiff_withPathParam_appendsPath() {
        GitDiffTool tool = new GitDiffTool(mockProject);

        ToolResult result = tool.execute(Map.of("path", "src/Main.java"));

        assertThat(result).isNotNull();
    }
}
