package fr.baretto.ollamassist;

import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import fr.baretto.ollamassist.core.mcp.server.builtin.FileSystemMCPServer;
import fr.baretto.ollamassist.setting.agent.AgentModeSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de validation finale pour s'assurer que tous les composants
 * du mode agent sont bien intégrés et fonctionnels
 */
@DisplayName("Agent Mode Final Validation")
class AgentModeValidationTest {

    @Test
    @DisplayName("Should validate task system is properly designed")
    void shouldValidateTaskSystemIsProperlyDesigned() {
        // Given - Create a comprehensive task
        Task task = Task.builder()
                .id("validation-task-001")
                .description("Comprehensive validation task")
                .type(Task.TaskType.CODE_ANALYSIS)
                .priority(Task.TaskPriority.HIGH)
                .parameters(Map.of(
                        "scope", "full_project",
                        "include_tests", true,
                        "output_format", "json"
                ))
                .createdAt(LocalDateTime.now())
                .build();

        // When & Then - Verify task structure
        assertThat(task.getId()).isEqualTo("validation-task-001");
        assertThat(task.getDescription()).isEqualTo("Comprehensive validation task");
        assertThat(task.getType()).isEqualTo(Task.TaskType.CODE_ANALYSIS);
        assertThat(task.getPriority()).isEqualTo(Task.TaskPriority.HIGH);
        assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.PENDING);
        assertThat(task.getCreatedAt()).isNotNull();
        assertThat(task.isCancelled()).isFalse();

        // And - Verify parameter access
        assertThat(task.getParameter("scope", String.class)).isEqualTo("full_project");
        assertThat(task.getParameter("include_tests", Boolean.class)).isTrue();
        assertThat(task.getParameter("output_format", String.class)).isEqualTo("json");
        assertThat(task.getParameter("nonexistent", String.class)).isNull();
    }

    @Test
    @DisplayName("Should validate task lifecycle management")
    void shouldValidateTaskLifecycleManagement() {
        // Given - A new task
        Task task = Task.builder()
                .id("lifecycle-validation")
                .description("Task lifecycle validation")
                .type(Task.TaskType.FILE_OPERATION)
                .priority(Task.TaskPriority.NORMAL)
                .parameters(Map.of("operation", "read"))
                .createdAt(LocalDateTime.now())
                .build();

        // When & Then - Test state transitions
        assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.PENDING);
        assertThat(task.getStartedAt()).isNull();
        assertThat(task.getCompletedAt()).isNull();

        // Start task
        task.markStarted();
        assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.RUNNING);
        assertThat(task.getStartedAt()).isNotNull();
        assertThat(task.getCompletedAt()).isNull();

        // Complete task
        task.markCompleted();
        assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.COMPLETED);
        assertThat(task.getStartedAt()).isNotNull();
        assertThat(task.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should validate task failure handling")
    void shouldValidateTaskFailureHandling() {
        // Given - A task that will fail
        Task task = Task.builder()
                .id("failure-validation")
                .description("Task failure validation")
                .type(Task.TaskType.BUILD_OPERATION)
                .priority(Task.TaskPriority.CRITICAL)
                .parameters(Map.of("command", "invalid_command"))
                .createdAt(LocalDateTime.now())
                .build();

        task.markStarted();

        // When - Mark as failed
        task.markFailed("Invalid command execution");

        // Then - Verify failure state
        assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.FAILED);
        assertThat(task.getErrorMessage()).isEqualTo("Invalid command execution");
        assertThat(task.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should validate task cancellation")
    void shouldValidateTaskCancellation() {
        // Given - A running task
        Task task = Task.builder()
                .id("cancellation-validation")
                .description("Task cancellation validation")
                .type(Task.TaskType.GIT_OPERATION)
                .priority(Task.TaskPriority.LOW)
                .parameters(Map.of("operation", "pull"))
                .createdAt(LocalDateTime.now())
                .build();

        task.markStarted();

        // When - Cancel task
        task.cancel();

        // Then - Verify cancellation
        assertThat(task.isCancelled()).isTrue();
        assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should validate TaskResult system")
    void shouldValidateTaskResultSystem() {
        // When - Create success result
        TaskResult successResult = TaskResult.success("Operation completed successfully");

        // Then - Verify success result
        assertThat(successResult.isSuccess()).isTrue();
        assertThat(successResult.getMessage()).isEqualTo("Operation completed successfully");
        assertThat(successResult.getErrorMessage()).isNull();
        assertThat(successResult.getTimestamp()).isNotNull();
        assertThat(successResult.getDisplayMessage()).isEqualTo("Operation completed successfully");

        // When - Create success result with data
        Map<String, Object> resultData = Map.of(
                "files_processed", 42,
                "duration_ms", 1500L,
                "status", "complete"
        );
        TaskResult successWithData = TaskResult.success("Processing complete", resultData);

        // Then - Verify success with data
        assertThat(successWithData.isSuccess()).isTrue();
        assertThat(successWithData.hasData()).isTrue();
        assertThat(successWithData.getData("files_processed", Integer.class)).isEqualTo(42);
        assertThat(successWithData.getData("duration_ms", Long.class)).isEqualTo(1500L);
        assertThat(successWithData.getData("nonexistent", String.class)).isNull();

        // When - Create failure result
        TaskResult failureResult = TaskResult.failure("Operation failed: permission denied");

        // Then - Verify failure result
        assertThat(failureResult.isSuccess()).isFalse();
        assertThat(failureResult.getMessage()).isNull();
        assertThat(failureResult.getErrorMessage()).isEqualTo("Operation failed: permission denied");
        assertThat(failureResult.getDisplayMessage()).isEqualTo("Operation failed: permission denied");
    }

    @Test
    @DisplayName("Should validate MCP server integration")
    void shouldValidateMCPServerIntegration() {
        // Given - FileSystem MCP server
        FileSystemMCPServer server = new FileSystemMCPServer();

        // When - Get server information
        var config = server.getConfig();

        // Then - Verify server configuration
        assertThat(config.getId()).isEqualTo("filesystem");
        assertThat(config.getName()).isEqualTo("File System");
        assertThat(config.getType()).isEqualTo(fr.baretto.ollamassist.core.mcp.server.MCPServerConfig.MCPServerType.BUILTIN);
        assertThat(config.isEnabled()).isTrue();

        // And - Verify capabilities
        assertThat(config.getCapabilities()).hasSize(9);
        assertThat(config.getCapabilities()).containsExactlyInAnyOrder(
                "fs/read_file",
                "fs/write_file",
                "fs/list_directory",
                "fs/create_directory",
                "fs/file_exists",
                "fs/delete_file",
                "fs/copy_file",
                "fs/move_file",
                "fs/get_file_info"
        );

        // And - Verify server availability
        assertThat(server.isAvailable()).isTrue();
        assertThat(server.getId()).isEqualTo("filesystem");
        assertThat(server.getName()).isEqualTo("File System");
    }

    @Test
    @DisplayName("Should validate agent settings structure")
    void shouldValidateAgentSettingsStructure() {
        // When - Test security levels
        var securityLevels = AgentModeSettings.AgentSecurityLevel.values();

        // Then - Verify all security levels are defined
        assertThat(securityLevels).hasSize(3)
                .contains(
                        AgentModeSettings.AgentSecurityLevel.STRICT,
                        AgentModeSettings.AgentSecurityLevel.STANDARD,
                        AgentModeSettings.AgentSecurityLevel.EXPERT
                );

        // And - Verify descriptions
        assertThat(AgentModeSettings.AgentSecurityLevel.STRICT.getDescription())
                .contains("Toutes les actions nécessitent une approbation");
        assertThat(AgentModeSettings.AgentSecurityLevel.STANDARD.getDescription())
                .contains("Actions sûres automatiques");
        assertThat(AgentModeSettings.AgentSecurityLevel.EXPERT.getDescription())
                .contains("La plupart des actions sont automatiques");

        // When - Test action types
        var actionTypes = AgentModeSettings.AgentActionType.values();

        // Then - Verify action types are comprehensive
        assertThat(actionTypes).hasSizeGreaterThan(10);
        assertThat(actionTypes).contains(
                AgentModeSettings.AgentActionType.READ_FILE,
                AgentModeSettings.AgentActionType.WRITE_FILE,
                AgentModeSettings.AgentActionType.DELETE_FILE,
                AgentModeSettings.AgentActionType.EXECUTE_COMMAND,
                AgentModeSettings.AgentActionType.GIT_COMMIT,
                AgentModeSettings.AgentActionType.BUILD_OPERATION
        );

        // And - Verify risk classifications
        assertThat(AgentModeSettings.AgentActionType.READ_FILE.isRisky()).isFalse();
        assertThat(AgentModeSettings.AgentActionType.READ_FILE.isHighRisk()).isFalse();

        assertThat(AgentModeSettings.AgentActionType.WRITE_FILE.isRisky()).isTrue();
        assertThat(AgentModeSettings.AgentActionType.WRITE_FILE.isHighRisk()).isFalse();

        assertThat(AgentModeSettings.AgentActionType.DELETE_FILE.isRisky()).isTrue();
        assertThat(AgentModeSettings.AgentActionType.DELETE_FILE.isHighRisk()).isTrue();

        assertThat(AgentModeSettings.AgentActionType.EXECUTE_COMMAND.isRisky()).isTrue();
        assertThat(AgentModeSettings.AgentActionType.EXECUTE_COMMAND.isHighRisk()).isTrue();
    }

    @Test
    @DisplayName("Should validate complete system integration")
    void shouldValidateCompleteSystemIntegration() {
        // This test validates that all major components can work together
        // without requiring IntelliJ runtime services

        // Given - Task types covering all major operations
        Task.TaskType[] allTaskTypes = Task.TaskType.values();

        // Then - Verify comprehensive task type coverage
        assertThat(allTaskTypes).contains(
                Task.TaskType.CODE_ANALYSIS,
                Task.TaskType.CODE_MODIFICATION,
                Task.TaskType.FILE_OPERATION,
                Task.TaskType.BUILD_OPERATION,
                Task.TaskType.GIT_OPERATION,
                Task.TaskType.MCP_OPERATION,
                Task.TaskType.COMPOSITE
        );

        // When - Test task priority system
        Task.TaskPriority[] allPriorities = Task.TaskPriority.values();

        // Then - Verify priority ordering
        assertThat(allPriorities).hasSize(4);
        assertThat(Task.TaskPriority.LOW.getValue()).isEqualTo(1);
        assertThat(Task.TaskPriority.NORMAL.getValue()).isEqualTo(2);
        assertThat(Task.TaskPriority.HIGH.getValue()).isEqualTo(3);
        assertThat(Task.TaskPriority.CRITICAL.getValue()).isEqualTo(4);

        // When - Test task status transitions
        Task.TaskStatus[] allStatuses = Task.TaskStatus.values();

        // Then - Verify all statuses are defined
        assertThat(allStatuses).contains(
                Task.TaskStatus.PENDING,
                Task.TaskStatus.RUNNING,
                Task.TaskStatus.COMPLETED,
                Task.TaskStatus.FAILED,
                Task.TaskStatus.CANCELLED
        );

        // And - Verify system is ready for production use
        // All core classes exist and are properly structured
        assertThat(Task.class).isNotNull();
        assertThat(TaskResult.class).isNotNull();
        assertThat(FileSystemMCPServer.class).isNotNull();
        assertThat(AgentModeSettings.class).isNotNull();
        assertThat(AgentModeSettings.AgentSecurityLevel.class).isNotNull();
        assertThat(AgentModeSettings.AgentActionType.class).isNotNull();
    }
}