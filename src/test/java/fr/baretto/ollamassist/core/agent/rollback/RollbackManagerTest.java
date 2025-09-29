package fr.baretto.ollamassist.core.agent.rollback;

import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RollbackManager Tests")
class RollbackManagerTest {

    @Mock
    private Project project;

    private RollbackManager rollbackManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        rollbackManager = new RollbackManager(project);
    }

    @Test
    @DisplayName("Should record and retrieve snapshots")
    void shouldRecordAndRetrieveSnapshots() {
        // Given
        ActionSnapshot snapshot = createTestSnapshot("action-1", "task-1", ActionSnapshot.ActionType.FILE_CREATE);

        // When
        rollbackManager.recordSnapshot(snapshot);

        // Then
        List<ActionSnapshot> taskSnapshots = rollbackManager.getTaskSnapshots("task-1");
        assertThat(taskSnapshots).hasSize(1);
        assertThat(taskSnapshots.get(0)).isEqualTo(snapshot);
        assertThat(rollbackManager.getSnapshotCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should record multiple snapshots for same task")
    void shouldRecordMultipleSnapshotsForSameTask() {
        // Given
        ActionSnapshot snapshot1 = createTestSnapshot("action-1", "task-1", ActionSnapshot.ActionType.FILE_CREATE);
        ActionSnapshot snapshot2 = createTestSnapshot("action-2", "task-1", ActionSnapshot.ActionType.FILE_MODIFY);

        // When
        rollbackManager.recordSnapshot(snapshot1);
        rollbackManager.recordSnapshot(snapshot2);

        // Then
        List<ActionSnapshot> taskSnapshots = rollbackManager.getTaskSnapshots("task-1");
        assertThat(taskSnapshots).hasSize(2);
        assertThat(rollbackManager.getSnapshotCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should rollback file creation action")
    void shouldRollbackFileCreationAction() {
        // Given
        ActionSnapshot snapshot = ActionSnapshot.builder()
                .actionId("action-1")
                .taskId("task-1")
                .actionType(ActionSnapshot.ActionType.FILE_CREATE)
                .beforeState(SnapshotData.forDeletedFile("test.txt"))
                .afterState(SnapshotData.forFile("test.txt", "content"))
                .timestamp(LocalDateTime.now())
                .build();

        rollbackManager.recordSnapshot(snapshot);

        // When
        RollbackResult result = rollbackManager.rollbackAction("action-1");

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("Fichier supprimé");
    }

    @Test
    @DisplayName("Should rollback file deletion action")
    void shouldRollbackFileDeletionAction() {
        // Given
        ActionSnapshot snapshot = ActionSnapshot.builder()
                .actionId("action-1")
                .taskId("task-1")
                .actionType(ActionSnapshot.ActionType.FILE_DELETE)
                .beforeState(SnapshotData.forFile("test.txt", "original content"))
                .afterState(SnapshotData.forDeletedFile("test.txt"))
                .timestamp(LocalDateTime.now())
                .build();

        rollbackManager.recordSnapshot(snapshot);

        // When
        RollbackResult result = rollbackManager.rollbackAction("action-1");

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("Fichier recréé");
    }

    @Test
    @DisplayName("Should rollback file modification action")
    void shouldRollbackFileModificationAction() {
        // Given
        ActionSnapshot snapshot = ActionSnapshot.builder()
                .actionId("action-1")
                .taskId("task-1")
                .actionType(ActionSnapshot.ActionType.FILE_MODIFY)
                .beforeState(SnapshotData.forFile("test.txt", "original content"))
                .afterState(SnapshotData.forFile("test.txt", "modified content"))
                .timestamp(LocalDateTime.now())
                .build();

        rollbackManager.recordSnapshot(snapshot);

        // When
        RollbackResult result = rollbackManager.rollbackAction("action-1");

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("Contenu restauré");
    }

    @Test
    @DisplayName("Should rollback entire task with multiple actions")
    void shouldRollbackEntireTaskWithMultipleActions() {
        // Given
        ActionSnapshot snapshot1 = createTestSnapshot("action-1", "task-1", ActionSnapshot.ActionType.FILE_CREATE);
        ActionSnapshot snapshot2 = createTestSnapshot("action-2", "task-1", ActionSnapshot.ActionType.FILE_MODIFY);

        rollbackManager.recordSnapshot(snapshot1);
        rollbackManager.recordSnapshot(snapshot2);

        // When
        RollbackResult result = rollbackManager.rollbackTask("task-1");

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("Rollback complet de la tâche task-1");
        assertThat(result.getMessage()).contains("2 actions");
    }

    @Test
    @DisplayName("Should handle rollback of non-existent action")
    void shouldHandleRollbackOfNonExistentAction() {
        // When
        RollbackResult result = rollbackManager.rollbackAction("non-existent");

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Snapshot introuvable");
    }

    @Test
    @DisplayName("Should handle rollback of non-existent task")
    void shouldHandleRollbackOfNonExistentTask() {
        // When
        RollbackResult result = rollbackManager.rollbackTask("non-existent");

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Aucun snapshot trouvé");
    }

    @Test
    @DisplayName("Should cleanup task snapshots")
    void shouldCleanupTaskSnapshots() {
        // Given
        ActionSnapshot snapshot1 = createTestSnapshot("action-1", "task-1", ActionSnapshot.ActionType.FILE_CREATE);
        ActionSnapshot snapshot2 = createTestSnapshot("action-2", "task-1", ActionSnapshot.ActionType.FILE_MODIFY);

        rollbackManager.recordSnapshot(snapshot1);
        rollbackManager.recordSnapshot(snapshot2);
        assertThat(rollbackManager.getSnapshotCount()).isEqualTo(2);

        // When
        rollbackManager.cleanupTaskSnapshots("task-1");

        // Then
        assertThat(rollbackManager.getSnapshotCount()).isEqualTo(0);
        assertThat(rollbackManager.getTaskSnapshots("task-1")).isEmpty();
    }

    @Test
    @DisplayName("Should clear all snapshots")
    void shouldClearAllSnapshots() {
        // Given
        ActionSnapshot snapshot1 = createTestSnapshot("action-1", "task-1", ActionSnapshot.ActionType.FILE_CREATE);
        ActionSnapshot snapshot2 = createTestSnapshot("action-2", "task-2", ActionSnapshot.ActionType.FILE_MODIFY);

        rollbackManager.recordSnapshot(snapshot1);
        rollbackManager.recordSnapshot(snapshot2);
        assertThat(rollbackManager.getSnapshotCount()).isEqualTo(2);

        // When
        rollbackManager.clearAllSnapshots();

        // Then
        assertThat(rollbackManager.getSnapshotCount()).isEqualTo(0);
        assertThat(rollbackManager.getTaskSnapshots("task-1")).isEmpty();
        assertThat(rollbackManager.getTaskSnapshots("task-2")).isEmpty();
    }

    @Test
    @DisplayName("Should handle unsupported action type rollback")
    void shouldHandleUnsupportedActionTypeRollback() {
        // Given
        ActionSnapshot snapshot = ActionSnapshot.builder()
                .actionId("action-1")
                .taskId("task-1")
                .actionType(ActionSnapshot.ActionType.BUILD_OPERATION)
                .beforeState(SnapshotData.empty())
                .afterState(SnapshotData.empty())
                .timestamp(LocalDateTime.now())
                .build();

        rollbackManager.recordSnapshot(snapshot);

        // When
        RollbackResult result = rollbackManager.rollbackAction("action-1");

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("Artefacts de build nettoyés");
    }

    private ActionSnapshot createTestSnapshot(String actionId, String taskId, ActionSnapshot.ActionType actionType) {
        return ActionSnapshot.builder()
                .actionId(actionId)
                .taskId(taskId)
                .actionType(actionType)
                .beforeState(SnapshotData.forFile("test.txt", "before"))
                .afterState(SnapshotData.forFile("test.txt", "after"))
                .timestamp(LocalDateTime.now())
                .metadata(new HashMap<>())
                .build();
    }
}