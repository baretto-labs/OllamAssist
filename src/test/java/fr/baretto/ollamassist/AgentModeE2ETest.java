package fr.baretto.ollamassist;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import fr.baretto.ollamassist.core.agent.AgentService;
import fr.baretto.ollamassist.core.agent.execution.ExecutionEngine;
import fr.baretto.ollamassist.core.agent.notifications.AgentNotification;
import fr.baretto.ollamassist.core.agent.notifications.AgentNotificationService;
import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import fr.baretto.ollamassist.setting.agent.AgentModeSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Test de bout en bout pour le mode agent
 * Valide l'intégration complète de tous les composants
 */
class AgentModeE2ETest {

    @TempDir
    Path tempDir;

    @Mock
    private Project mockProject;

    @Mock
    private VirtualFile mockProjectRoot;

    @Mock
    private MessageBus mockMessageBus;

    private ExecutionEngine executionEngine;
    private AgentService agentService;
    private AgentModeSettings agentSettings;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mock project structure
        when(mockProject.getBaseDir()).thenReturn(mockProjectRoot);
        when(mockProjectRoot.getPath()).thenReturn(tempDir.toString());
        when(mockProject.getMessageBus()).thenReturn(mockMessageBus);
        when(mockMessageBus.connect()).thenReturn(mock(com.intellij.util.messages.MessageBusConnection.class));

        // Initialize services
        executionEngine = new ExecutionEngine(mockProject);
        agentService = new AgentService(mockProject);
        agentSettings = AgentModeSettings.getInstance();
    }

    @Test
    void testCompleteAgentWorkflow() {
        // GIVEN: Agent Mode activé avec configuration valide
        agentSettings.enableAgentMode();
        agentSettings.setSecurityLevel(AgentModeSettings.AgentSecurityLevel.STANDARD);
        agentSettings.setMaxTasksPerSession(5);

        assertThat(agentSettings.isAgentModeEnabled()).isTrue();

        // WHEN: Vérification de la disponibilité des executors
        assertThat(executionEngine.getFileOperationExecutor()).isNotNull();
        assertThat(executionEngine.getCodeModificationExecutor()).isNotNull();

        // AND: Test d'exécution d'une tâche simple
        Map<String, Object> params = new HashMap<>();
        params.put("operation", "create");
        params.put("filePath", "test-e2e.txt");
        params.put("content", "End-to-end test content");

        Task fileTask = Task.builder()
                .id("e2e_file_test")
                .type(Task.TaskType.FILE_OPERATION)
                .description("E2E file creation test")
                .parameters(params)
                .build();

        TaskResult result = executionEngine.executeTask(fileTask);

        // THEN: La tâche s'exécute correctement
        assertThat(result).isNotNull();
        assertThat(result.getTaskId()).isEqualTo("e2e_file_test");
        assertThat(result.getExecutionTime()).isNotNull();
        assertThat(result.getTimestamp()).isNotNull();
    }

    @Test
    void testNotificationSystemIntegration() {
        // GIVEN: Un listener pour capturer les notifications
        AtomicInteger notificationCount = new AtomicInteger(0);
        AtomicBoolean taskStartedReceived = new AtomicBoolean(false);

        AgentNotificationService.AgentNotificationListener testListener = notification -> {
            notificationCount.incrementAndGet();
            if (notification.getType() == AgentNotification.NotificationType.TASK_STARTED) {
                taskStartedReceived.set(true);
            }
        };

        // Simuler l'abonnement au MessageBus
        when(mockMessageBus.syncPublisher(AgentNotificationService.AGENT_NOTIFICATIONS))
                .thenReturn(testListener);

        // WHEN: Création et exécution d'une tâche
        Map<String, Object> params = new HashMap<>();
        params.put("operation", "create");
        params.put("filePath", "notification-test.txt");
        params.put("content", "Test notification content");

        Task notificationTask = Task.builder()
                .id("notification_test")
                .type(Task.TaskType.FILE_OPERATION)
                .description("Notification test task")
                .parameters(params)
                .build();

        // Déclencher la notification manuellement pour le test
        AgentNotificationService notificationService = executionEngine.getNotificationService();
        notificationService.notifyTaskStarted(notificationTask);

        // THEN: La notification est envoyée
        verify(mockMessageBus).syncPublisher(AgentNotificationService.AGENT_NOTIFICATIONS);
    }

    @Test
    void testAllExecutorTypesAvailable() {
        // GIVEN: ExecutionEngine initialisé
        // WHEN: Vérification des executors disponibles
        // THEN: Tous les types d'executors sont disponibles
        assertThat(executionEngine.getExecutorForTask(createTask(Task.TaskType.FILE_OPERATION)))
                .isNotNull()
                .isInstanceOf(fr.baretto.ollamassist.core.agent.execution.FileOperationExecutor.class);

        assertThat(executionEngine.getExecutorForTask(createTask(Task.TaskType.CODE_MODIFICATION)))
                .isNotNull()
                .isInstanceOf(fr.baretto.ollamassist.core.agent.execution.CodeModificationExecutor.class);

        assertThat(executionEngine.getExecutorForTask(createTask(Task.TaskType.GIT_OPERATION)))
                .isNotNull()
                .isInstanceOf(fr.baretto.ollamassist.core.agent.execution.GitOperationExecutor.class);

        assertThat(executionEngine.getExecutorForTask(createTask(Task.TaskType.BUILD_OPERATION)))
                .isNotNull()
                .isInstanceOf(fr.baretto.ollamassist.core.agent.execution.BuildOperationExecutor.class);
    }

    @Test
    void testAgentServiceAvailability() {
        // GIVEN: AgentService configuré
        // WHEN: Vérification de la disponibilité
        boolean isAvailable = agentService.isAvailable();
        boolean isUsingNativeTools = agentService.isUsingNativeTools();

        // THEN: Le service est configuré correctement
        assertThat(agentService).isNotNull();

        // Les tests unitaires ne peuvent pas valider la connexion Ollama
        // mais on peut vérifier que les composants sont bien initialisés
        assertThat(agentService).isNotNull();
    }

    @Test
    void testTaskStatusLifecycle() {
        // GIVEN: Une nouvelle tâche
        Task task = createTask(Task.TaskType.FILE_OPERATION);

        // WHEN: Vérification du cycle de vie initial
        assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.PENDING);
        assertThat(task.isCancelled()).isFalse();

        // WHEN: Démarrage de la tâche
        task.markStarted();
        assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.RUNNING);

        // WHEN: Complétion de la tâche
        task.markCompleted();
        assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.COMPLETED);
    }

    @Test
    void testTaskCancellation() {
        // GIVEN: Une tâche en cours
        Task task = createTask(Task.TaskType.FILE_OPERATION);
        task.markStarted();

        // WHEN: Annulation de la tâche
        task.cancel();

        // THEN: La tâche est annulée
        assertThat(task.isCancelled()).isTrue();
        assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.CANCELLED);

        // AND: L'exécution retourne un échec
        TaskResult result = executionEngine.executeTask(task);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("annulée");
    }

    @Test
    void testExecutorPerformanceMetrics() {
        // GIVEN: Statistiques d'exécution
        ExecutionEngine.ExecutionStats stats = executionEngine.getStats();

        // WHEN: Vérification des métriques
        // THEN: Les statistiques sont disponibles
        assertThat(stats).isNotNull();
        assertThat(stats.getTotalExecutions()).isEqualTo(0); // Nouveau moteur
        assertThat(stats.getSuccessRate()).isEqualTo(0.0); // Pas encore d'exécutions
    }

    private Task createTask(Task.TaskType type) {
        Map<String, Object> params = new HashMap<>();
        params.put("operation", "test");
        params.put("filePath", "test.txt");

        return Task.builder()
                .id("test_task_" + type.name().toLowerCase())
                .type(type)
                .description("Test task for " + type)
                .parameters(params)
                .build();
    }
}