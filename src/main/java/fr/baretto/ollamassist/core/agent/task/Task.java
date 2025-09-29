package fr.baretto.ollamassist.core.agent.task;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Représente une tâche à exécuter par l'agent
 */
@lombok.Data
@lombok.Builder
public class Task {
    private final String id;
    private final String description;
    private final TaskType type;
    private final TaskPriority priority;
    private final Map<String, Object> parameters;
    private final LocalDateTime createdAt;

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    @lombok.Builder.Default
    private TaskStatus status = TaskStatus.PENDING;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    /**
     * Marque la tâche comme annulée
     */
    public void cancel() {
        cancelled.set(true);
        if (status == TaskStatus.PENDING || status == TaskStatus.RUNNING) {
            status = TaskStatus.CANCELLED;
        }
    }

    /**
     * Vérifie si la tâche est annulée
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * Marque le début de l'exécution
     */
    public void markStarted() {
        this.status = TaskStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    /**
     * Marque la fin de l'exécution avec succès
     */
    public void markCompleted() {
        this.status = TaskStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Marque la tâche comme échouée
     */
    public void markFailed(String errorMessage) {
        this.status = TaskStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Obtient un paramètre typé
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key, Class<T> type) {
        Object value = parameters.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }

    /**
     * Types de tâches
     */
    public enum TaskType {
        CODE_ANALYSIS,      // Analyse de code
        CODE_MODIFICATION,  // Modification de code
        FILE_OPERATION,     // Opération sur fichiers
        BUILD_OPERATION,    // Compilation/tests
        GIT_OPERATION,      // Opérations Git
        MCP_OPERATION,      // Opérations via MCP
        COMPOSITE           // Tâche composite
    }


    /**
     * Priorités de tâches
     */
    public enum TaskPriority {
        LOW(1),
        NORMAL(2),
        HIGH(3),
        CRITICAL(4);

        private final int value;

        TaskPriority(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * États de tâches
     */
    public enum TaskStatus {
        PENDING,    // En attente
        RUNNING,    // En cours d'exécution
        COMPLETED,  // Terminée avec succès
        FAILED,     // Échouée
        CANCELLED   // Annulée
    }

    // Alias pour compatibilité tests TDD
    public static class Status {
        public static final TaskStatus PENDING = TaskStatus.PENDING;
        public static final TaskStatus RUNNING = TaskStatus.RUNNING;
        public static final TaskStatus COMPLETED = TaskStatus.COMPLETED;
        public static final TaskStatus FAILED = TaskStatus.FAILED;
        public static final TaskStatus CANCELLED = TaskStatus.CANCELLED;
    }
}