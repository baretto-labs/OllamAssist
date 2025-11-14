package fr.baretto.ollamassist.notification.provider;

import fr.baretto.ollamassist.notification.core.Notification;

import java.util.List;

/**
 * Interface for providing notifications.
 * Implementations can be hardcoded, remote-fetched, or dynamically generated.
 */
public interface NotificationProvider {

    /**
     * Retrieves all available notifications across all versions.
     *
     * @return List of all notifications
     */
    List<Notification> getAllNotifications();
}
