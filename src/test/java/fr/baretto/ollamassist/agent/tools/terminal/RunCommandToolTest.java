package fr.baretto.ollamassist.agent.tools.terminal;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.agent.tools.ToolApprovalHelper;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RunCommandToolTest {

    private Project mockProject;
    private ToolApprovalHelper mockApprovalHelper;
    private RunCommandTool tool;

    @BeforeEach
    void setUp() {
        mockProject = mock(Project.class);
        when(mockProject.getBasePath()).thenReturn(System.getProperty("java.io.tmpdir"));
        mockApprovalHelper = mock(ToolApprovalHelper.class);
        tool = new RunCommandTool(mockProject, mockApprovalHelper);
    }

    @Test
    void missingCommand_returnsFailure() {
        ToolResult result = tool.execute(Map.of());
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("'command' is required");
    }

    @Test
    void blankCommand_returnsFailure() {
        ToolResult result = tool.execute(Map.of("command", "  "));
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void destructiveCommand_blockedWithoutApproval() {
        ToolResult result = tool.execute(Map.of("command", "rm -rf /tmp/test"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("DESTRUCTIVE");
        verifyNoInteractions(mockApprovalHelper);
    }

    @Test
    void mutatingCommand_requiresApproval_rejected() {
        when(mockApprovalHelper.requestApproval(anyString(), anyString(), anyString())).thenReturn(false);

        ToolResult result = tool.execute(Map.of("command", "git commit -m 'test'"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("rejected");
        verify(mockApprovalHelper).requestApproval(anyString(), anyString(), eq("git commit -m 'test'"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void readOnlyCommand_executesWithoutApproval() {
        // git status is READ_ONLY — no approval needed
        ToolResult result = tool.execute(Map.of("command", "git status"));

        // git status may succeed or fail depending on working directory — the point is no approval was requested
        verifyNoInteractions(mockApprovalHelper);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void mutatingCommand_approved_executes() {
        when(mockApprovalHelper.requestApproval(anyString(), anyString(), anyString())).thenReturn(true);

        ToolResult result = tool.execute(Map.of("command", "mkdir -p /tmp/ollamassist-test-run"));

        assertThat(result.isSuccess()).isTrue();
        verify(mockApprovalHelper).requestApproval(anyString(), anyString(), anyString());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void failingCommand_returnsFailureWithExitCode() {
        ToolResult result = tool.execute(Map.of("command", "ls /path/that/does/not/exist/xyz123"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("exited with code");
    }

    @Test
    void toolId_isRunCommand() {
        assertThat(tool.toolId()).isEqualTo("RUN_COMMAND");
    }
}
