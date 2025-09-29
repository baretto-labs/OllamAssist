package fr.baretto.ollamassist.core.agent;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import fr.baretto.ollamassist.core.agent.execution.ExecutionEngine;
import fr.baretto.ollamassist.core.agent.notifications.AgentNotification;
import fr.baretto.ollamassist.core.agent.notifications.AgentNotificationService;
import fr.baretto.ollamassist.core.agent.rollback.RollbackManager;
import fr.baretto.ollamassist.core.agent.rollback.RollbackResult;
import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests d'intégration complets pour le mode agent
 * Teste les vraies opérations de fichiers et l'intégration complète
 */
class AgentModeIntegrationTest {

    @TempDir
    Path tempDir;

    @Mock
    private Project mockProject;

    @Mock
    private VirtualFile mockProjectRoot;

    private ExecutionEngine executionEngine;
    private RollbackManager rollbackManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(mockProject.getBaseDir()).thenReturn(mockProjectRoot);
        when(mockProjectRoot.getPath()).thenReturn(tempDir.toString());

        executionEngine = new ExecutionEngine(mockProject);
        rollbackManager = new RollbackManager(mockProject);
    }

    @Test
    void testCompleteWorkflowWithNotifications() throws InterruptedException {
        // GIVEN: Un listener pour capturer les notifications
        AtomicInteger notificationCount = new AtomicInteger(0);
        AtomicBoolean taskStartedNotified = new AtomicBoolean(false);
        AtomicBoolean taskCompletedNotified = new AtomicBoolean(false);

        AgentNotificationService.AgentNotificationListener listener = notification -> {
            notificationCount.incrementAndGet();

            if (notification.getType() == AgentNotification.NotificationType.TASK_STARTED) {
                taskStartedNotified.set(true);
            } else if (notification.getType() == AgentNotification.NotificationType.TASK_SUCCESS) {
                taskCompletedNotified.set(true);
            }
        };

        // S'abonner aux notifications via MessageBus
        mockProject.getMessageBus().connect().subscribe(
                AgentNotificationService.AGENT_NOTIFICATIONS, listener);

        // WHEN: Exécution d'une tâche
        Map<String, Object> params = new HashMap<>();
        params.put("operation", "create");
        params.put("filePath", "test-integration.txt");
        params.put("content", "Integration test content");
        params.put("backup", true);

        Task testTask = Task.builder()
                .id("integration_test_task")
                .type(Task.TaskType.FILE_OPERATION)
                .description("Integration test file creation")
                .parameters(params)
                .build();

        TaskResult result = executionEngine.executeTask(testTask);

        // THEN: La tâche réussit
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("créé avec succès");

        // AND: Les notifications sont envoyées
        // Attendre un peu pour les notifications asynchrones
        Thread.sleep(500);

        assertThat(notificationCount.get()).isGreaterThan(0);
        assertTrue(taskStartedNotified.get(), "Task started notification should be sent");
        assertTrue(taskCompletedNotified.get(), "Task completed notification should be sent");
    }

    @Test
    void testFileOperationsWithRealFiles() throws IOException {
        // GIVEN: Un fichier réel dans le répertoire temporaire
        Path testFile = tempDir.resolve("real-test-file.txt");
        Files.write(testFile, "Original content".getBytes());

        // Mock VirtualFile behavior
        when(mockProjectRoot.findFileByRelativePath("real-test-file.txt"))
                .thenReturn(mock(VirtualFile.class));

        // WHEN: Création d'une tâche de modification de fichier
        Map<String, Object> params = new HashMap<>();
        params.put("operation", "create");
        params.put("filePath", "new-test-file.txt");
        params.put("content", "New file content for integration test");

        Task createTask = Task.builder()
                .id("real_file_test")
                .type(Task.TaskType.FILE_OPERATION)
                .description("Real file creation test")
                .parameters(params)
                .build();

        // AND: Exécution de la tâche
        TaskResult result = executionEngine.executeTask(createTask);

        // THEN: Vérifier que la tâche a des informations correctes
        assertThat(result.getTaskId()).isEqualTo("real_file_test");
        assertThat(result.getExecutionTime()).isNotNull();
        assertThat(result.getTimestamp()).isNotNull();
    }

    @Test
    void testGitOperationIntegration() {
        // GIVEN: Une tâche Git
        Map<String, Object> params = new HashMap<>();
        params.put("operation", "status");

        Task gitTask = Task.builder()
                .id("git_integration_test")
                .type(Task.TaskType.GIT_OPERATION)
                .description("Git status check")
                .parameters(params)
                .build();

        // WHEN: Exécution de la tâche Git
        TaskResult result = executionEngine.executeTask(gitTask);

        // THEN: La tâche se termine (succès ou échec selon l'environnement)
        assertThat(result).isNotNull();
        assertThat(result.getTaskId()).isEqualTo("git_integration_test");

        // Dans un environnement de test, Git peut ne pas être disponible
        // On vérifie juste que la tâche est traitée correctement
        if (!result.isSuccess()) {
            assertThat(result.getErrorMessage()).isNotNull();
        }
    }

    @Test
    void testBuildOperationIntegration() {
        // GIVEN: Une tâche de build
        Map<String, Object> params = new HashMap<>();
        params.put("operation", "gradle_build");
        params.put("tasks", "compileJava");
        params.put("timeout", 30); // 30 secondes

        Task buildTask = Task.builder()
                .id("build_integration_test")
                .type(Task.TaskType.BUILD_OPERATION)
                .description("Gradle build test")
                .parameters(params)
                .build();

        // WHEN: Exécution de la tâche de build
        TaskResult result = executionEngine.executeTask(buildTask);

        // THEN: La tâche se termine avec un résultat
        assertThat(result).isNotNull();
        assertThat(result.getTaskId()).isEqualTo("build_integration_test");

        // Dans un environnement de test, Gradle peut ne pas être disponible
        // On vérifie que le workflow fonctionne correctement
        if (result.isSuccess()) {
            assertThat(result.getData()).isNotNull();
            assertThat(result.getData("command", String.class)).contains("gradlew");
        }
    }

    @Test
    void testErrorHandlingWithNotifications() throws InterruptedException {
        // GIVEN: Un listener pour capturer les notifications d'erreur
        AtomicBoolean errorNotified = new AtomicBoolean(false);

        AgentNotificationService.AgentNotificationListener errorListener = notification -> {
            if (notification.getType() == AgentNotification.NotificationType.TASK_FAILURE) {
                errorNotified.set(true);
            }
        };

        mockProject.getMessageBus().connect().subscribe(
                AgentNotificationService.AGENT_NOTIFICATIONS, errorListener);

        // WHEN: Exécution d'une tâche avec des paramètres invalides
        Map<String, Object> invalidParams = new HashMap<>();
        invalidParams.put("operation", "invalid_operation");
        invalidParams.put("filePath", "");

        Task invalidTask = Task.builder()
                .id("error_test_task")
                .type(Task.TaskType.FILE_OPERATION)
                .description("Error test task")
                .parameters(invalidParams)
                .build();

        TaskResult result = executionEngine.executeTask(invalidTask);

        // THEN: La tâche échoue
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isNotNull();

        // AND: Une notification d'erreur est envoyée
        Thread.sleep(300);
        assertTrue(errorNotified.get(), "Error notification should be sent");
    }

    @Test
    void testRollbackManagerWithMultipleTasks() {
        // GIVEN: Plusieurs tâches avec le même ID de tâche pour simuler une transaction
        String transactionId = "multi_task_transaction";

        // WHEN: Enregistrement de plusieurs snapshots
        rollbackManager.recordSnapshot(createTestSnapshot(transactionId, "action1"));
        rollbackManager.recordSnapshot(createTestSnapshot(transactionId, "action2"));
        rollbackManager.recordSnapshot(createTestSnapshot(transactionId, "action3"));

        // THEN: Les snapshots sont correctement enregistrés
        assertThat(rollbackManager.getSnapshotCount()).isEqualTo(3);
        assertThat(rollbackManager.getTaskSnapshots(transactionId)).hasSize(3);

        // WHEN: Rollback de toute la transaction
        RollbackResult rollbackResult = rollbackManager.rollbackTask(transactionId);

        // THEN: Le rollback est traité (peut réussir ou échouer selon l'environnement)
        assertThat(rollbackResult).isNotNull();

        // Nettoyage
        rollbackManager.cleanupTaskSnapshots(transactionId);
        assertThat(rollbackManager.getTaskSnapshots(transactionId)).isEmpty();
    }

    @Test
    void testAsyncTaskExecution() throws Exception {
        // GIVEN: Une tâche à exécuter de manière asynchrone
        Map<String, Object> params = new HashMap<>();
        params.put("operation", "create");
        params.put("filePath", "async-test.txt");
        params.put("content", "Async test content");

        Task asyncTask = Task.builder()
                .id("async_test_task")
                .type(Task.TaskType.FILE_OPERATION)
                .description("Async execution test")
                .parameters(params)
                .build();

        // WHEN: Exécution asynchrone
        CompletableFuture<TaskResult> futureResult = CompletableFuture.supplyAsync(() ->
                executionEngine.executeTask(asyncTask));

        // THEN: La tâche se termine dans les temps
        TaskResult result = futureResult.get(5, TimeUnit.SECONDS);
        assertThat(result).isNotNull();
        assertThat(result.getTaskId()).isEqualTo("async_test_task");
    }

    @Test
    void testTaskCancellation() {
        // GIVEN: Une tâche
        Map<String, Object> params = new HashMap<>();
        params.put("operation", "create");
        params.put("filePath", "cancel-test.txt");
        params.put("content", "Cancel test content");

        Task cancellableTask = Task.builder()
                .id("cancel_test_task")
                .type(Task.TaskType.FILE_OPERATION)
                .description("Cancellation test")
                .parameters(params)
                .build();

        // WHEN: Annulation de la tâche avant exécution
        cancellableTask.cancel();

        // AND: Tentative d'exécution
        TaskResult result = executionEngine.executeTask(cancellableTask);

        // THEN: La tâche est annulée
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("annulée");
        assertThat(cancellableTask.getStatus()).isEqualTo(Task.TaskStatus.CANCELLED);
    }

    @Test
    void testTaskParameterValidation() {
        // GIVEN: Différents types de paramètres
        Map<String, Object> params = new HashMap<>();
        params.put("stringParam", "test string");
        params.put("intParam", 42);
        params.put("boolParam", true);

        Task paramTask = Task.builder()
                .id("param_test_task")
                .type(Task.TaskType.FILE_OPERATION)
                .description("Parameter validation test")
                .parameters(params)
                .build();

        // WHEN: Récupération des paramètres typés
        String stringValue = paramTask.getParameter("stringParam", String.class);
        Integer intValue = paramTask.getParameter("intParam", Integer.class);
        Boolean boolValue = paramTask.getParameter("boolParam", Boolean.class);
        String nonExistentValue = paramTask.getParameter("nonExistent", String.class);

        // THEN: Les paramètres sont correctement typés
        assertThat(stringValue).isEqualTo("test string");
        assertThat(intValue).isEqualTo(42);
        assertThat(boolValue).isTrue();
        assertThat(nonExistentValue).isNull();
    }

    private fr.baretto.ollamassist.core.agent.rollback.ActionSnapshot createTestSnapshot(String taskId, String actionId) {
        return fr.baretto.ollamassist.core.agent.rollback.ActionSnapshot.builder()
                .actionId(actionId)
                .taskId(taskId)
                .actionType(fr.baretto.ollamassist.core.agent.rollback.ActionSnapshot.ActionType.FILE_CREATE)
                .timestamp(java.time.LocalDateTime.now())
                .build();
    }
}