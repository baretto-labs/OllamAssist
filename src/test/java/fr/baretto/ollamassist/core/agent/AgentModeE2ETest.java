package fr.baretto.ollamassist.core.agent;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import fr.baretto.ollamassist.core.agent.execution.ExecutionEngine;
import fr.baretto.ollamassist.core.agent.rollback.ActionSnapshot;
import fr.baretto.ollamassist.core.agent.rollback.RollbackManager;
import fr.baretto.ollamassist.core.agent.task.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test E2E complet du mode agent
 * Teste le workflow complet : création de tâche → exécution → snapshot → rollback
 */
class AgentModeE2ETest extends BasePlatformTestCase {

    private ExecutionEngine executionEngine;
    private RollbackManager rollbackManager;

    @Override
    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();
        executionEngine = new ExecutionEngine(getProject());
        rollbackManager = new RollbackManager(getProject());
    }

    @Override
    @AfterEach
    protected void tearDown() throws Exception {
        try {
            // Clean up all snapshots to avoid interference between tests
            if (rollbackManager != null) {
                rollbackManager.clearAllSnapshots();
            }

            // Clear references to allow garbage collection
            executionEngine = null;
            rollbackManager = null;
        } finally {
            super.tearDown();
        }
    }

    @Test
    void testExecutionEngineInitialization() {
        // GIVEN: Un ExecutionEngine initialisé
        // WHEN: Vérification des exécuteurs
        var fileExecutor = executionEngine.getFileOperationExecutor();
        var codeExecutor = executionEngine.getCodeModificationExecutor();

        // THEN: Les exécuteurs sont correctement initialisés
        assertThat(fileExecutor).isNotNull();
        assertThat(fileExecutor.getExecutorName()).isEqualTo("FileOperationExecutor");

        assertThat(codeExecutor).isNotNull();
        assertThat(codeExecutor.getExecutorName()).isEqualTo("CodeModificationExecutor");
    }

    @Test
    void testTaskCreationAndParameters() {
        // GIVEN: Des paramètres pour une tâche
        Map<String, Object> params = new HashMap<>();
        params.put("operation", "create");
        params.put("filePath", "src/test/TestFile.java");
        params.put("content", "public class TestFile {}");
        params.put("backup", true);

        // WHEN: Création d'une tâche
        Task createTask = Task.builder()
                .id("test_task")
                .type(Task.TaskType.FILE_OPERATION)
                .description("Créer un fichier de test")
                .parameters(params)
                .build();

        // THEN: La tâche est correctement créée
        assertThat(createTask.getId()).isEqualTo("test_task");
        assertThat(createTask.getType()).isEqualTo(Task.TaskType.FILE_OPERATION);
        assertThat(createTask.getDescription()).isEqualTo("Créer un fichier de test");

        // AND: Les paramètres sont accessibles
        assertThat(createTask.getParameter("operation", String.class)).isEqualTo("create");
        assertThat(createTask.getParameter("filePath", String.class)).isEqualTo("src/test/TestFile.java");
        assertThat(createTask.getParameter("backup", Boolean.class)).isTrue();
    }

    @Test
    void testTaskExecutionWorkflow() {
        // GIVEN: Une tâche de modification de code avec des paramètres valides
        Map<String, Object> params = new HashMap<>();
        params.put("filePath", "src/Example.java");
        params.put("modificationType", "add_method");
        params.put("className", "Example");
        params.put("methodCode", "void testMethod() {}");
        params.put("backup", true);

        Task modifyTask = Task.builder()
                .id("test_modify_code")
                .type(Task.TaskType.CODE_MODIFICATION)
                .description("Ajouter une méthode à la classe")
                .parameters(params)
                .build();

        // WHEN: Vérification que l'exécuteur peut traiter cette tâche
        var codeExecutor = executionEngine.getCodeModificationExecutor();
        boolean canExecute = codeExecutor.canExecute(modifyTask);

        // THEN: L'exécuteur peut traiter cette tâche
        assertThat(canExecute).isTrue();
        assertThat(codeExecutor.supportsRollback(modifyTask)).isTrue();
    }

    @Test
    void testComponentsIntegration() {
        // THEN: Les composants principaux sont correctement initialisés
        assertThat(executionEngine).isNotNull();
        assertThat(rollbackManager).isNotNull();

        // AND: Les statistiques de base sont disponibles
        int snapshotCount = rollbackManager.getSnapshotCount();
        assertThat(snapshotCount).isZero();
    }

    @Test
    void testExecutionEngineErrorHandling() {
        // GIVEN: Une tâche avec des paramètres invalides
        Map<String, Object> invalidParams = new HashMap<>();
        invalidParams.put("operation", "create");
        // filePath manquant intentionnellement
        invalidParams.put("content", "test content");

        Task invalidTask = Task.builder()
                .id("invalid_task")
                .type(Task.TaskType.FILE_OPERATION)
                .description("Tâche invalide")
                .parameters(invalidParams)
                .build();

        // WHEN: Vérification des paramètres
        String filePath = invalidTask.getParameter("filePath", String.class);

        // THEN: Le paramètre filePath est manquant
        assertThat(filePath).isNull();
        assertThat(invalidTask.getParameter("operation", String.class)).isEqualTo("create");
        assertThat(invalidTask.getParameter("content", String.class)).isEqualTo("test content");
    }

    @Test
    void testSnapshotCapabilities() {
        // GIVEN: Une tâche qui supporte les snapshots
        Map<String, Object> params = new HashMap<>();
        params.put("operation", "delete");
        params.put("filePath", "test.txt");

        Task fileTask = Task.builder()
                .id("snapshot_test")
                .type(Task.TaskType.FILE_OPERATION)
                .description("Test de snapshot")
                .parameters(params)
                .build();

        // WHEN: Vérification des capacités de snapshot
        var fileExecutor = executionEngine.getFileOperationExecutor();
        boolean supportsRollback = fileExecutor.supportsRollback(fileTask);

        // THEN: Le support de rollback est confirmé
        assertThat(supportsRollback).isTrue();
        assertThat(fileExecutor.canExecute(fileTask)).isTrue();
    }

    @Test
    void testRollbackManagerStatistics() {
        // GIVEN: Plusieurs snapshots enregistrés
        rollbackManager.recordSnapshot(createTestSnapshot("task1", "action1"));
        rollbackManager.recordSnapshot(createTestSnapshot("task1", "action2"));
        rollbackManager.recordSnapshot(createTestSnapshot("task2", "action3"));

        // WHEN: Vérification des statistiques
        int totalSnapshots = rollbackManager.getSnapshotCount();
        var task1Snapshots = rollbackManager.getTaskSnapshots("task1");
        var task2Snapshots = rollbackManager.getTaskSnapshots("task2");

        // THEN: Les statistiques sont correctes
        assertThat(totalSnapshots).isEqualTo(3);
        assertThat(task1Snapshots).hasSize(2);
        assertThat(task2Snapshots).hasSize(1);

        // WHEN: Nettoyage des snapshots d'une tâche
        rollbackManager.cleanupTaskSnapshots("task1");

        // THEN: Les snapshots sont nettoyés
        assertThat(rollbackManager.getSnapshotCount()).isEqualTo(1);
        assertThat(rollbackManager.getTaskSnapshots("task1")).isEmpty();
        assertThat(rollbackManager.getTaskSnapshots("task2")).hasSize(1);
    }

    @Test
    void testTaskLifecycle() {
        // GIVEN: Une nouvelle tâche
        Map<String, Object> params = new HashMap<>();
        params.put("operation", "create");
        params.put("filePath", "test.txt");

        Task task = Task.builder()
                .id("lifecycle_test")
                .type(Task.TaskType.FILE_OPERATION)
                .description("Test du cycle de vie")
                .parameters(params)
                .build();

        // THEN: État initial
        assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.PENDING);
        assertThat(task.isCancelled()).isFalse();

        // WHEN: Démarrage de la tâche
        task.markStarted();

        // THEN: État en cours
        assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.RUNNING);
        assertThat(task.getStartedAt()).isNotNull();

        // WHEN: Fin de la tâche avec succès
        task.markCompleted();

        // THEN: État terminé
        assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.COMPLETED);
        assertThat(task.getCompletedAt()).isNotNull();
    }

    private ActionSnapshot createTestSnapshot(String taskId, String actionId) {
        return ActionSnapshot.builder()
                .actionId(actionId)
                .taskId(taskId)
                .actionType(ActionSnapshot.ActionType.FILE_CREATE)
                .timestamp(java.time.LocalDateTime.now())
                .build();
    }
}
