package fr.baretto.ollamassist.core.agent.rollback;

import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * Résultat d'une opération de rollback
 */
@Data
public class RollbackResult {
    private final boolean success;
    private final String message;
    private final String errorMessage;
    private final List<String> failedActions;

    private RollbackResult(boolean success, String message, String errorMessage, List<String> failedActions) {
        this.success = success;
        this.message = message;
        this.errorMessage = errorMessage;
        this.failedActions = failedActions != null ? failedActions : Collections.emptyList();
    }

    public static RollbackResult success(String message) {
        return new RollbackResult(true, message, null, null);
    }

    public static RollbackResult failure(String errorMessage) {
        return new RollbackResult(false, null, errorMessage, null);
    }

    public static RollbackResult partial(String message, List<String> failedActions) {
        return new RollbackResult(false, message, "Rollback partiel", failedActions);
    }

    public boolean isPartial() {
        return !success && !failedActions.isEmpty();
    }
}