package fr.baretto.ollamassist.core.agent.notifications;

import fr.baretto.ollamassist.core.agent.task.TaskResult;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Représente une notification d'agent
 */
@Data
@Builder
public class AgentNotification {

    private final String id;
    private final NotificationType type;
    private final String taskId;
    private final String title;
    private final String message;
    private final String details;
    private final Integer progress;
    private final TaskResult result;
    private final LocalDateTime timestamp;
    private final Priority priority;

    /**
     * Génère un ID unique pour la notification
     */
    public static String generateId() {
        return "agent_notif_" + System.currentTimeMillis() + "_" + Thread.currentThread().threadId();
    }

    /**
     * Crée une notification de début de tâche
     */
    public static AgentNotification taskStarted(String taskId, String description) {
        return AgentNotification.builder()
                .id(generateId())
                .type(NotificationType.TASK_STARTED)
                .taskId(taskId)
                .title("Task Started")
                .message("" + description)
                .timestamp(LocalDateTime.now())
                .priority(Priority.NORMAL)
                .build();
    }

    /**
     * Crée une notification de progression de tâche
     */
    public static AgentNotification taskProgress(String taskId, String description, int progress, String details) {
        return AgentNotification.builder()
                .id(generateId())
                .type(NotificationType.TASK_PROGRESS)
                .taskId(taskId)
                .title("Task Progress")
                .message("⚙️ " + description + " - " + progress + "% complete")
                .details(details)
                .progress(progress)
                .timestamp(LocalDateTime.now())
                .priority(Priority.LOW)
                .build();
    }

    /**
     * Crée une notification de succès de tâche
     */
    public static AgentNotification taskSuccess(String taskId, String description, TaskResult result) {
        return AgentNotification.builder()
                .id(generateId())
                .type(NotificationType.TASK_SUCCESS)
                .taskId(taskId)
                .title("Task Completed")
                .message("" + description)
                .details(result.getMessage())
                .result(result)
                .timestamp(LocalDateTime.now())
                .priority(Priority.NORMAL)
                .build();
    }

    /**
     * Crée une notification d'échec de tâche
     */
    public static AgentNotification taskFailure(String taskId, String description, TaskResult result) {
        return AgentNotification.builder()
                .id(generateId())
                .type(NotificationType.TASK_FAILURE)
                .taskId(taskId)
                .title("Task Failed")
                .message("" + description)
                .details(result.getErrorMessage())
                .result(result)
                .timestamp(LocalDateTime.now())
                .priority(Priority.HIGH)
                .build();
    }

    /**
     * Crée une notification d'annulation de tâche
     */
    public static AgentNotification taskCancelled(String taskId, String description) {
        return AgentNotification.builder()
                .id(generateId())
                .type(NotificationType.TASK_CANCELLED)
                .taskId(taskId)
                .title("Task Cancelled")
                .message("" + description)
                .timestamp(LocalDateTime.now())
                .priority(Priority.NORMAL)
                .build();
    }

    /**
     * Crée une notification d'activation d'agent
     */
    public static AgentNotification agentActivated() {
        return AgentNotification.builder()
                .id(generateId())
                .type(NotificationType.AGENT_ACTIVATED)
                .title("Agent Mode")
                .message("🤖 Agent Mode activated - Ready for autonomous tasks")
                .timestamp(LocalDateTime.now())
                .priority(Priority.HIGH)
                .build();
    }

    /**
     * Crée une notification de désactivation d'agent
     */
    public static AgentNotification agentDeactivated() {
        return AgentNotification.builder()
                .id(generateId())
                .type(NotificationType.AGENT_DEACTIVATED)
                .title("Agent Mode")
                .message("Agent Mode deactivated")
                .timestamp(LocalDateTime.now())
                .priority(Priority.NORMAL)
                .build();
    }

    /**
     * Crée une notification d'erreur d'agent
     */
    public static AgentNotification agentError(String message, String details) {
        return AgentNotification.builder()
                .id(generateId())
                .type(NotificationType.AGENT_ERROR)
                .title("Agent Error")
                .message("" + message)
                .details(details)
                .timestamp(LocalDateTime.now())
                .priority(Priority.CRITICAL)
                .build();
    }

    /**
     * Crée une notification d'avertissement d'agent
     */
    public static AgentNotification agentWarning(String message, String details) {
        return AgentNotification.builder()
                .id(generateId())
                .type(NotificationType.AGENT_WARNING)
                .title("Agent Warning")
                .message("️ " + message)
                .details(details)
                .timestamp(LocalDateTime.now())
                .priority(Priority.HIGH)
                .build();
    }

    /**
     * Crée une notification d'information d'agent
     */
    public static AgentNotification agentInfo(String message, String details) {
        return AgentNotification.builder()
                .id(generateId())
                .type(NotificationType.AGENT_INFO)
                .title("Agent Info")
                .message("ℹ️ " + message)
                .details(details)
                .timestamp(LocalDateTime.now())
                .priority(Priority.LOW)
                .build();
    }

    /**
     * Crée une notification de demande d'approbation utilisateur
     */
    public static AgentNotification userApprovalRequired(String taskId, String description, String details) {
        return AgentNotification.builder()
                .id(generateId())
                .type(NotificationType.USER_APPROVAL_REQUIRED)
                .taskId(taskId)
                .title("User Approval Required")
                .message("" + description)
                .details(details)
                .timestamp(LocalDateTime.now())
                .priority(Priority.HIGH)
                .build();
    }

    /**
     * Crée une notification d'approbation accordée
     */
    public static AgentNotification userApprovalGranted(String taskId, String description) {
        return AgentNotification.builder()
                .id(generateId())
                .type(NotificationType.USER_APPROVAL_GRANTED)
                .taskId(taskId)
                .title("User Approval Granted")
                .message("" + description)
                .timestamp(LocalDateTime.now())
                .priority(Priority.NORMAL)
                .build();
    }

    /**
     * Crée une notification d'approbation refusée
     */
    public static AgentNotification userApprovalDenied(String taskId, String description) {
        return AgentNotification.builder()
                .id(generateId())
                .type(NotificationType.USER_APPROVAL_DENIED)
                .taskId(taskId)
                .title("User Approval Denied")
                .message("" + description)
                .timestamp(LocalDateTime.now())
                .priority(Priority.NORMAL)
                .build();
    }

    /**
     * Vérifie si la notification est une notification de tâche
     */
    public boolean isTaskNotification() {
        return taskId != null && (
                type == NotificationType.TASK_STARTED ||
                        type == NotificationType.TASK_PROGRESS ||
                        type == NotificationType.TASK_SUCCESS ||
                        type == NotificationType.TASK_FAILURE ||
                        type == NotificationType.TASK_CANCELLED
        );
    }

    /**
     * Vérifie si la notification est une notification d'agent
     */
    public boolean isAgentNotification() {
        return type == NotificationType.AGENT_ACTIVATED ||
                type == NotificationType.AGENT_DEACTIVATED ||
                type == NotificationType.AGENT_ERROR ||
                type == NotificationType.AGENT_WARNING ||
                type == NotificationType.AGENT_INFO;
    }

    /**
     * Vérifie si la notification nécessite une action utilisateur
     */
    public boolean requiresUserAction() {
        return type == NotificationType.USER_APPROVAL_REQUIRED ||
                type == NotificationType.TASK_FAILURE ||
                type == NotificationType.AGENT_ERROR;
    }

    /**
     * Retourne une représentation textuelle courte de la notification
     */
    @Override
    public String toString() {
        return String.format("[%s] %s: %s",
                timestamp.toLocalTime(),
                title,
                message);
    }

    /**
     * Types de notifications d'agent
     */
    public enum NotificationType {
        // Notifications de tâches
        TASK_STARTED,
        TASK_PROGRESS,
        TASK_SUCCESS,
        TASK_FAILURE,
        TASK_CANCELLED,

        // Notifications de l'agent
        AGENT_ACTIVATED,
        AGENT_DEACTIVATED,
        AGENT_ERROR,
        AGENT_WARNING,
        AGENT_INFO,

        // Notifications système
        ROLLBACK_STARTED,
        ROLLBACK_SUCCESS,
        ROLLBACK_FAILURE,

        // Notifications de validation
        USER_APPROVAL_REQUIRED,
        USER_APPROVAL_GRANTED,
        USER_APPROVAL_DENIED
    }

    /**
     * Priorité des notifications
     */
    public enum Priority {
        LOW(1),
        NORMAL(2),
        HIGH(3),
        CRITICAL(4);

        private final int level;

        Priority(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }
    }
}