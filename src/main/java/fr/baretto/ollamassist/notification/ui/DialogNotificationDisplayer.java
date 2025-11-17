package fr.baretto.ollamassist.notification.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.notification.core.Notification;
import fr.baretto.ollamassist.notification.core.NotificationDisplayer;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Displays notifications using a Swing dialog.
 */
@Slf4j
public final class DialogNotificationDisplayer implements NotificationDisplayer {

    @Override
    public void show(Project project, List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            log.debug("No notifications to display");
            return;
        }

        log.info("Displaying {} notifications in dialog", notifications.size());

        ApplicationManager.getApplication().invokeLater(() -> {
            NotificationDialog dialog = new NotificationDialog(project, notifications);
            dialog.show();
        });
    }
}
