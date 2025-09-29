package fr.baretto.ollamassist.core.agent.notifications;

import com.intellij.notification.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Service de notifications pour les actions de l'agent
 * Utilise le système de notifications IntelliJ et le MessageBus
 */
@Slf4j
public class AgentNotificationService {

    // Topic pour les notifications d'agent via MessageBus
    public static final Topic<AgentNotificationListener> AGENT_NOTIFICATIONS =
            Topic.create("AgentNotifications", AgentNotificationListener.class);

    private final Project project;
    private final MessageBus messageBus;
    private final NotificationGroup notificationGroup;
    private final ConcurrentLinkedQueue<AgentNotification> notificationHistory;
    private final ConcurrentHashMap<String, Notification> activeNotifications;

    public AgentNotificationService(Project project) {
        this.project = project;
        this.messageBus = project.getMessageBus();
        this.notificationHistory = new ConcurrentLinkedQueue<>();
        this.activeNotifications = new ConcurrentHashMap<>();

        // Créer un groupe de notifications spécifique pour l'agent
        this.notificationGroup = NotificationGroupManager.getInstance()
                .getNotificationGroup("OllamAssist.Agent");
    }

    /**
     * Notifie le début d'une tâche
     */
    public void notifyTaskStarted(Task task) {
        String message = "🚀 Starting: " + task.getDescription();

        AgentNotification notification = AgentNotification.builder()
                .type(AgentNotification.NotificationType.TASK_STARTED)
                .taskId(task.getId())
                .title("Agent Task Started")
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();

        // Publier via MessageBus
        publishNotification(notification);

        // Afficher notification IntelliJ (info, non persistante)
        Notification ideNotification = notificationGroup.createNotification(
                "Agent Mode",
                message,
                NotificationType.INFORMATION
        );
        ideNotification.setImportant(false);

        activeNotifications.put(task.getId(), ideNotification);
        ideNotification.notify(project);

        log.info("📢 Task started notification: {}", task.getId());
    }

    /**
     * Notifie la progression d'une tâche
     */
    public void notifyTaskProgress(Task task, int percentage, String details) {
        String message = String.format("⚙️ %s - %d%% complete", task.getDescription(), percentage);
        if (details != null && !details.trim().isEmpty()) {
            message += "\n" + details;
        }

        AgentNotification notification = AgentNotification.builder()
                .type(AgentNotification.NotificationType.TASK_PROGRESS)
                .taskId(task.getId())
                .title("Agent Progress")
                .message(message)
                .progress(percentage)
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();

        publishNotification(notification);

        // Mettre à jour la notification existante
        Notification existingNotification = activeNotifications.get(task.getId());
        if (existingNotification != null && !existingNotification.isExpired()) {
            existingNotification.setContent(message);
        }
    }

    /**
     * Notifie le succès d'une tâche
     */
    public void notifyTaskSuccess(Task task, TaskResult result) {
        String message = "✅ Completed: " + task.getDescription();
        if (result.getMessage() != null) {
            message += "\n" + result.getMessage();
        }

        AgentNotification notification = AgentNotification.builder()
                .type(AgentNotification.NotificationType.TASK_SUCCESS)
                .taskId(task.getId())
                .title("Agent Task Completed")
                .message(message)
                .result(result)
                .timestamp(LocalDateTime.now())
                .build();

        publishNotification(notification);

        // Remplacer par une notification de succès
        expireTaskNotification(task.getId());

        Notification successNotification = notificationGroup.createNotification(
                "Agent Success",
                message,
                NotificationType.INFORMATION
        );
        successNotification.setImportant(true);
        successNotification.notify(project);

        // Auto-expirer après 5 secondes
        Timer expireTimer = new Timer(5000, e -> successNotification.expire());
        expireTimer.setRepeats(false);
        expireTimer.start();

        log.info("✅ Task success notification: {}", task.getId());
    }

    /**
     * Notifie l'échec d'une tâche
     */
    public void notifyTaskFailure(Task task, TaskResult result) {
        String message = "❌ Failed: " + task.getDescription();
        if (result.getErrorMessage() != null) {
            message += "\nError: " + result.getErrorMessage();
        }

        AgentNotification notification = AgentNotification.builder()
                .type(AgentNotification.NotificationType.TASK_FAILURE)
                .taskId(task.getId())
                .title("Agent Task Failed")
                .message(message)
                .result(result)
                .timestamp(LocalDateTime.now())
                .build();

        publishNotification(notification);

        // Remplacer par une notification d'erreur
        expireTaskNotification(task.getId());

        Notification errorNotification = notificationGroup.createNotification(
                "Agent Error",
                message,
                NotificationType.ERROR
        );
        errorNotification.setImportant(true);

        // Ajouter action pour retry si possible
        errorNotification.addAction(NotificationAction.createSimple("View Details", () ->
                showTaskErrorDetails(task, result)));

        errorNotification.notify(project);

        log.error("❌ Task failure notification: {} - {}", task.getId(), result.getErrorMessage());
    }

    /**
     * Notifie l'annulation d'une tâche
     */
    public void notifyTaskCancelled(Task task) {
        String message = "🚫 Cancelled: " + task.getDescription();

        AgentNotification notification = AgentNotification.builder()
                .type(AgentNotification.NotificationType.TASK_CANCELLED)
                .taskId(task.getId())
                .title("Agent Task Cancelled")
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();

        publishNotification(notification);

        // Expirer la notification active
        expireTaskNotification(task.getId());

        log.info("🚫 Task cancelled notification: {}", task.getId());
    }

    /**
     * Notifie un événement général de l'agent
     */
    public void notifyAgentEvent(String title, String message, AgentNotification.NotificationType type) {
        AgentNotification notification = AgentNotification.builder()
                .type(type)
                .title(title)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();

        publishNotification(notification);

        // Déterminer le type de notification IntelliJ
        NotificationType ideType = switch (type) {
            case AGENT_ERROR, TASK_FAILURE -> NotificationType.ERROR;
            case AGENT_WARNING -> NotificationType.WARNING;
            default -> NotificationType.INFORMATION;
        };

        Notification ideNotification = notificationGroup.createNotification(
                title,
                message,
                ideType
        );
        ideNotification.notify(project);

        log.info("📢 Agent event notification: {} - {}", title, type);
    }

    /**
     * Notifie l'état de l'agent (activé/désactivé)
     */
    public void notifyAgentStateChange(boolean enabled) {
        String message = enabled ?
                "🤖 Agent Mode activated - Ready for autonomous tasks" :
                "💤 Agent Mode deactivated";

        AgentNotification notification = AgentNotification.builder()
                .type(enabled ? AgentNotification.NotificationType.AGENT_ACTIVATED :
                        AgentNotification.NotificationType.AGENT_DEACTIVATED)
                .title("Agent Mode")
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();

        publishNotification(notification);

        Notification ideNotification = notificationGroup.createNotification(
                "Agent Mode State",
                message,
                NotificationType.INFORMATION
        );
        ideNotification.setImportant(true);
        ideNotification.notify(project);

        log.info("🔄 Agent state change notification: enabled={}", enabled);
    }

    /**
     * Affiche un résumé des notifications récentes
     */
    public void showNotificationHistory() {
        StringBuilder summary = new StringBuilder();
        summary.append("Recent Agent Notifications:\n\n");

        notificationHistory.stream()
                .limit(10) // Dernières 10 notifications
                .forEach(notification -> {
                    String timestamp = notification.getTimestamp()
                            .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    summary.append(String.format("[%s] %s: %s\n",
                            timestamp, notification.getTitle(), notification.getMessage()));
                });

        Notification historyNotification = notificationGroup.createNotification(
                "Agent Notification History",
                summary.toString(),
                NotificationType.INFORMATION
        );
        historyNotification.notify(project);
    }

    /**
     * Publie une notification via MessageBus et l'ajoute à l'historique
     */
    private void publishNotification(AgentNotification notification) {
        // Ajouter à l'historique
        notificationHistory.offer(notification);

        // Limiter l'historique à 100 notifications
        while (notificationHistory.size() > 100) {
            notificationHistory.poll();
        }

        // Publier via MessageBus
        messageBus.syncPublisher(AGENT_NOTIFICATIONS).onNotification(notification);
    }

    /**
     * Expire une notification de tâche active
     */
    private void expireTaskNotification(String taskId) {
        Notification notification = activeNotifications.remove(taskId);
        if (notification != null && !notification.isExpired()) {
            notification.expire();
        }
    }

    /**
     * Affiche les détails d'une erreur de tâche
     */
    private void showTaskErrorDetails(Task task, TaskResult result) {
        String details = String.format(
                "Task: %s\nID: %s\nError: %s\nTime: %s",
                task.getDescription(),
                task.getId(),
                result.getErrorMessage(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );

        Notification detailsNotification = notificationGroup.createNotification(
                "Task Error Details",
                details,
                NotificationType.ERROR
        );
        detailsNotification.notify(project);
    }

    /**
     * Nettoie les ressources
     */
    public void dispose() {
        // Expirer toutes les notifications actives
        activeNotifications.values().forEach(notification -> {
            if (!notification.isExpired()) {
                notification.expire();
            }
        });
        activeNotifications.clear();
        notificationHistory.clear();
    }

    /**
     * Interface pour écouter les notifications d'agent
     */
    public interface AgentNotificationListener {
        void onNotification(AgentNotification notification);
    }
}