package fr.baretto.ollamassist.component;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PluginNotifier {


    public static void notify(String groupId, String title, String message, NotificationType type) {
        if (ApplicationManager.getApplication() != null
                && !ApplicationManager.getApplication().isUnitTestMode()) {
            Notifications.Bus.notify(new Notification(
                    groupId,
                    title,
                    message,
                    type
            ));
        }
    }
}
