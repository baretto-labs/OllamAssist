package fr.baretto.ollamassist.core.agent.rollback;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Snapshot d'une action pour permettre le rollback
 */
@Data
@Builder
public class ActionSnapshot {
    private final String actionId;
    private final String taskId;
    private final ActionType actionType;
    private final SnapshotData beforeState;
    private final SnapshotData afterState;
    private final LocalDateTime timestamp;
    private final Map<String, Object> metadata;

    public enum ActionType {
        FILE_CREATE,
        FILE_DELETE,
        FILE_MODIFY,
        FILE_MOVE,
        GIT_ADD,
        GIT_COMMIT,
        GIT_PUSH,
        BUILD_OPERATION
    }
}