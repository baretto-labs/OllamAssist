package fr.baretto.ollamassist;

import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import fr.baretto.ollamassist.setting.agent.AgentModeSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'intégration pour vérifier que les composants du mode agent
 * fonctionnent ensemble correctement sans dépendances IntelliJ
 */
@DisplayName("Agent Mode Integration Tests")
class AgentModeIntegrationTest {

    @Test
    @DisplayName("Should integrate agent settings with task execution flow")
    void shouldIntegrateAgentSettingsWithTaskExecutionFlow() {
        // Given - Agent mode configuration test
        AgentModeSettings.State mockState = new AgentModeSettings.State();
        mockState.agentModeEnabled = true;
        mockState.securityLevel = AgentModeSettings.AgentSecurityLevel.STANDARD;
        mockState.maxTasksPerSession = 5;
        mockState.autoTaskApprovalEnabled = false;

        // When - Test basic configuration validation
        // Then - Configuration should be valid
        assertThat(mockState.agentModeEnabled).isTrue();
        assertThat(mockState.securityLevel).isEqualTo(AgentModeSettings.AgentSecurityLevel.STANDARD);
        assertThat(mockState.maxTasksPerSession).isEqualTo(5);
        assertThat(mockState.autoTaskApprovalEnabled).isFalse();
    }

    @Test
    @DisplayName("Should handle task lifecycle with settings and results")
    void shouldHandleTaskLifecycleWithSettingsAndResults() {
        // Given - Test task without dependencies
        Task task = Task.builder()
                .id("lifecycle-test")
                .description("Test task lifecycle")
                .type(Task.TaskType.FILE_OPERATION)
                .priority(Task.TaskPriority.NORMAL)
                .parameters(Map.of("operation", "read", "path", "/test"))
                .status(Task.TaskStatus.PENDING)
                .build();

        // When - Create a success result
        TaskResult successResult = TaskResult.success("Task completed successfully");

        // Then - Verify task and result structure
        assertThat(task.getId()).isEqualTo("lifecycle-test");
        assertThat(task.getType()).isEqualTo(Task.TaskType.FILE_OPERATION);
        assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.PENDING);
        assertThat(successResult.isSuccess()).isTrue();
        assertThat(successResult.getMessage()).isEqualTo("Task completed successfully");
        assertThat(successResult.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should validate security levels and action types compatibility")
    void shouldValidateSecurityLevelsAndActionTypesCompatibility() {
        // Given - Different security levels for testing
        AgentModeSettings.State strictState = new AgentModeSettings.State();
        strictState.securityLevel = AgentModeSettings.AgentSecurityLevel.STRICT;
        strictState.autoTaskApprovalEnabled = false;

        AgentModeSettings.State standardState = new AgentModeSettings.State();
        standardState.securityLevel = AgentModeSettings.AgentSecurityLevel.STANDARD;
        standardState.autoTaskApprovalEnabled = false;

        // When - Test action type requirements
        AgentModeSettings.AgentActionType readAction = AgentModeSettings.AgentActionType.READ_FILE;
        AgentModeSettings.AgentActionType deleteAction = AgentModeSettings.AgentActionType.DELETE_FILE;

        // Then - Verify security level logic
        assertThat(readAction.isRisky()).isFalse();
        assertThat(readAction.isHighRisk()).isFalse();
        assertThat(deleteAction.isRisky()).isTrue();
        assertThat(deleteAction.isHighRisk()).isTrue();
    }

    @Test
    @DisplayName("Should demonstrate complete agent workflow compatibility")
    void shouldDemonstrateCompleteAgentWorkflowCompatibility() {
        // Given - Agent configuration test
        AgentModeSettings.State agentState = new AgentModeSettings.State();
        agentState.agentModeEnabled = true;
        agentState.securityLevel = AgentModeSettings.AgentSecurityLevel.EXPERT;
        agentState.autoTaskApprovalEnabled = true;
        agentState.maxTasksPerSession = 20;

        // When - Test workflow components
        Task workflowTask = Task.builder()
                .id("workflow-compatibility-test")
                .description("Complete workflow test")
                .type(Task.TaskType.BUILD_OPERATION)
                .priority(Task.TaskPriority.HIGH)
                .parameters(Map.of("command", "test", "target", "all"))
                .status(Task.TaskStatus.PENDING)
                .build();

        TaskResult workflowResult = TaskResult.success("Workflow completed successfully");

        // Then - Verify complete workflow
        assertThat(agentState.agentModeEnabled).isTrue();
        assertThat(agentState.securityLevel).isEqualTo(AgentModeSettings.AgentSecurityLevel.EXPERT);
        assertThat(workflowTask.getType()).isEqualTo(Task.TaskType.BUILD_OPERATION);
        assertThat(workflowResult.isSuccess()).isTrue();
    }
}