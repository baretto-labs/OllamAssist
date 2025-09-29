package fr.baretto.ollamassist;

import fr.baretto.ollamassist.core.agent.notifications.AgentNotification;
import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import fr.baretto.ollamassist.core.agent.ui.TaskProgressIndicator;
import fr.baretto.ollamassist.setting.agent.AgentModeSettings;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de validation de compilation pour le mode agent
 * Ces tests vérifient que toutes les classes se compilent et s'instancient correctement
 */
class AgentModeCompilationTest {

    @Test
    void testTaskCreationAndBuilder() {
        // GIVEN: Paramètres de tâche
        Map<String, Object> params = new HashMap<>();
        params.put("operation", "create");
        params.put("filePath", "test.txt");
        params.put("content", "Test content");

        // WHEN: Création d'une tâche avec le builder
        Task task = Task.builder()
                .id("test_task")
                .type(Task.TaskType.FILE_OPERATION)
                .description("Test task creation")
                .priority(Task.TaskPriority.NORMAL)
                .parameters(params)
                .createdAt(LocalDateTime.now())
                .build();

        // THEN: La tâche est créée correctement
        assertThat(task).isNotNull();
        assertThat(task.getId()).isEqualTo("test_task");
        assertThat(task.getType()).isEqualTo(Task.TaskType.FILE_OPERATION);
        assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.PENDING);
        assertThat(task.isCancelled()).isFalse();

        // AND: Les paramètres sont accessibles
        String operation = task.getParameter("operation", String.class);
        assertThat(operation).isEqualTo("create");

        String filePath = task.getParameter("filePath", String.class);
        assertThat(filePath).isEqualTo("test.txt");
    }

    @Test
    void testTaskStatusTransitions() {
        // GIVEN: Une nouvelle tâche
        Task task = Task.builder()
                .id("status_test")
                .type(Task.TaskType.CODE_MODIFICATION)
                .description("Status transition test")
                .parameters(new HashMap<>())
                .build();

        // WHEN: Transitions de statut
        assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.PENDING);

        task.markStarted();
        assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.RUNNING);
        assertThat(task.getStartedAt()).isNotNull();

        task.markCompleted();
        assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.COMPLETED);
        assertThat(task.getCompletedAt()).isNotNull();
    }

    @Test
    void testTaskCancellation() {
        // GIVEN: Une tâche
        Task task = Task.builder()
                .id("cancel_test")
                .type(Task.TaskType.GIT_OPERATION)
                .description("Cancellation test")
                .parameters(new HashMap<>())
                .build();

        // WHEN: Annulation
        task.cancel();

        // THEN: La tâche est annulée
        assertThat(task.isCancelled()).isTrue();
        assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.CANCELLED);
    }

    @Test
    void testTaskResultCreation() {
        // GIVEN: Données de résultat
        Map<String, Object> data = new HashMap<>();
        data.put("filePath", "/test/path.txt");
        data.put("linesCreated", 42);

        // WHEN: Création d'un résultat de succès
        TaskResult successResult = TaskResult.builder()
                .success(true)
                .message("File created successfully")
                .data(data)
                .taskId("test_task")
                .timestamp(LocalDateTime.now())
                .executionTime(Duration.ofMillis(150))
                .build();

        // THEN: Le résultat est correct
        assertThat(successResult.isSuccess()).isTrue();
        assertThat(successResult.getMessage()).isEqualTo("File created successfully");
        assertThat(successResult.getTaskId()).isEqualTo("test_task");
        assertThat(successResult.getData()).isEqualTo(data);

        // AND: Accès typé aux données
        String filePath = successResult.getData("filePath", String.class);
        assertThat(filePath).isEqualTo("/test/path.txt");

        Integer linesCreated = successResult.getData("linesCreated", Integer.class);
        assertThat(linesCreated).isEqualTo(42);
    }

    @Test
    void testTaskResultFailure() {
        // WHEN: Création d'un résultat d'échec
        TaskResult failureResult = TaskResult.failure("Operation failed: file not found");

        // THEN: Le résultat indique un échec
        assertThat(failureResult.isSuccess()).isFalse();
        assertThat(failureResult.getErrorMessage()).isEqualTo("Operation failed: file not found");
    }

    @Test
    void testAgentNotificationTypes() {
        // WHEN: Création de différents types de notifications
        AgentNotification taskStarted = AgentNotification.taskStarted("task_1", "Starting file operation");
        AgentNotification taskSuccess = AgentNotification.taskSuccess("task_1", "File operation completed",
                TaskResult.builder().success(true).message("Success").build());
        AgentNotification agentActivated = AgentNotification.agentActivated();
        AgentNotification agentError = AgentNotification.agentError("Connection failed", "Details here");

        // THEN: Les notifications sont créées correctement
        assertThat(taskStarted.getType()).isEqualTo(AgentNotification.NotificationType.TASK_STARTED);
        assertThat(taskStarted.getTaskId()).isEqualTo("task_1");
        assertThat(taskStarted.getMessage()).contains("Starting file operation");

        assertThat(taskSuccess.getType()).isEqualTo(AgentNotification.NotificationType.TASK_SUCCESS);
        assertThat(taskSuccess.getPriority()).isEqualTo(AgentNotification.Priority.NORMAL);

        assertThat(agentActivated.getType()).isEqualTo(AgentNotification.NotificationType.AGENT_ACTIVATED);
        assertThat(agentActivated.getPriority()).isEqualTo(AgentNotification.Priority.HIGH);

        assertThat(agentError.getType()).isEqualTo(AgentNotification.NotificationType.AGENT_ERROR);
        assertThat(agentError.getPriority()).isEqualTo(AgentNotification.Priority.CRITICAL);
    }

    @Test
    void testAgentModeSettingsEnumValues() {
        // WHEN: Vérification des niveaux de sécurité
        AgentModeSettings.AgentSecurityLevel[] securityLevels = AgentModeSettings.AgentSecurityLevel.values();

        // THEN: Tous les niveaux sont disponibles
        assertThat(securityLevels).containsExactlyInAnyOrder(
                AgentModeSettings.AgentSecurityLevel.STRICT,
                AgentModeSettings.AgentSecurityLevel.STANDARD,
                AgentModeSettings.AgentSecurityLevel.EXPERT
        );

        // AND: Les descriptions sont définies
        assertThat(AgentModeSettings.AgentSecurityLevel.STRICT.getDescription())
                .contains("Strict");
        assertThat(AgentModeSettings.AgentSecurityLevel.STANDARD.getDescription())
                .contains("Standard");
        assertThat(AgentModeSettings.AgentSecurityLevel.EXPERT.getDescription())
                .contains("Expert");
    }

    @Test
    void testAgentSecurityLevels() {
        // GIVEN: Different action types
        AgentModeSettings.AgentActionType readFile = AgentModeSettings.AgentActionType.READ_FILE;
        AgentModeSettings.AgentActionType writeFile = AgentModeSettings.AgentActionType.WRITE_FILE;
        AgentModeSettings.AgentActionType deleteFile = AgentModeSettings.AgentActionType.DELETE_FILE;
        AgentModeSettings.AgentActionType executeCommand = AgentModeSettings.AgentActionType.EXECUTE_COMMAND;

        // THEN: Risk levels are correct
        assertThat(readFile.isRisky()).isFalse();
        assertThat(readFile.isHighRisk()).isFalse();

        assertThat(writeFile.isRisky()).isTrue();
        assertThat(writeFile.isHighRisk()).isFalse();

        assertThat(deleteFile.isRisky()).isTrue();
        assertThat(deleteFile.isHighRisk()).isTrue();

        assertThat(executeCommand.isRisky()).isTrue();
        assertThat(executeCommand.isHighRisk()).isTrue();
    }

    @Test
    void testTaskProgressIndicatorInstantiation() {
        // WHEN: Création d'un indicateur de progression
        TaskProgressIndicator indicator = new TaskProgressIndicator();

        // THEN: L'indicateur est créé
        assertThat(indicator).isNotNull();
        // Note: Ne peut pas tester davantage sans contexte Swing/IntelliJ
    }

    @Test
    void testAllTaskTypes() {
        // WHEN: Vérification de tous les types de tâches
        Task.TaskType[] types = Task.TaskType.values();

        // THEN: Tous les types sont disponibles
        assertThat(types).containsExactlyInAnyOrder(
                Task.TaskType.CODE_ANALYSIS,
                Task.TaskType.CODE_MODIFICATION,
                Task.TaskType.FILE_OPERATION,
                Task.TaskType.BUILD_OPERATION,
                Task.TaskType.GIT_OPERATION,
                Task.TaskType.MCP_OPERATION,
                Task.TaskType.COMPOSITE
        );
    }

    @Test
    void testAllTaskPriorities() {
        // WHEN: Vérification de toutes les priorités
        Task.TaskPriority[] priorities = Task.TaskPriority.values();

        // THEN: Toutes les priorités sont disponibles avec les bonnes valeurs
        assertThat(Task.TaskPriority.LOW.getValue()).isEqualTo(1);
        assertThat(Task.TaskPriority.NORMAL.getValue()).isEqualTo(2);
        assertThat(Task.TaskPriority.HIGH.getValue()).isEqualTo(3);
        assertThat(Task.TaskPriority.CRITICAL.getValue()).isEqualTo(4);
    }

    @Test
    void testTaskResultDataAccess() {
        // GIVEN: TaskResult avec données complexes
        Map<String, Object> complexData = new HashMap<>();
        complexData.put("stringValue", "test");
        complexData.put("intValue", 42);
        complexData.put("boolValue", true);
        complexData.put("nullValue", null);

        TaskResult result = TaskResult.builder()
                .success(true)
                .data(complexData)
                .build();

        // WHEN: Accès typé aux données
        String stringVal = result.getData("stringValue", String.class);
        Integer intVal = result.getData("intValue", Integer.class);
        Boolean boolVal = result.getData("boolValue", Boolean.class);
        String nullVal = result.getData("nullValue", String.class);
        String nonExistent = result.getData("nonExistent", String.class);

        // THEN: Les valeurs sont correctement typées
        assertThat(stringVal).isEqualTo("test");
        assertThat(intVal).isEqualTo(42);
        assertThat(boolVal).isTrue();
        assertThat(nullVal).isNull();
        assertThat(nonExistent).isNull();
    }
}