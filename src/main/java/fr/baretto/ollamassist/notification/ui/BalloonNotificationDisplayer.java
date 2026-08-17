package fr.baretto.ollamassist.notification.ui;

import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.notification.core.Notification;
import fr.baretto.ollamassist.notification.core.NotificationDisplayer;
import fr.baretto.ollamassist.notification.core.NotificationManager;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Displays pending notifications as a non-blocking balloon.
 * <p>
 * The detailed dialog is only opened on explicit user action: showing a modal dialog during
 * a startup activity blocks the EDT and freezes the IDE until the user closes it.
 */
@Slf4j
public final class BalloonNotificationDisplayer implements NotificationDisplayer {

    private static final String NOTIFICATION_GROUP = "OllamAssist";
    private static final String TITLE = "OllamAssist updates";
    private static final String OPEN_ACTION = "See what's new";

    @Override
    public void show(Project project, List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            log.debug("No notifications to display");
            return;
        }

        Application application = ApplicationManager.getApplication();
        if (application == null || application.isUnitTestMode() || application.isHeadlessEnvironment()) {
            log.debug("Headless or test environment, skipping notification display");
            return;
        }

        log.info("Displaying {} notifications as a balloon", notifications.size());

        com.intellij.notification.Notification balloon = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification(TITLE, buildContent(notifications), NotificationType.INFORMATION);

        balloon.addAction(new NotificationAction(OPEN_ACTION) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event,
                                        @NotNull com.intellij.notification.Notification notification) {
                notification.expire();
                new NotificationDialog(project, notifications).show();
            }
        });

        balloon.whenExpired(this::acknowledge);

        balloon.notify(project);
    }

    private String buildContent(List<Notification> notifications) {
        if (notifications.size() == 1) {
            return notifications.get(0).getTitle();
        }
        return "%d updates since your last version".formatted(notifications.size());
    }

    /**
     * Once the balloon has been dismissed, the user has been informed: do not show the same
     * notifications again on the next IDE start.
     */
    private void acknowledge() {
        NotificationManager notificationManager = ApplicationManager.getApplication()
                .getService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.updateLastNotifiedVersion();
        }
    }
}
