package fr.baretto.ollamassist.notification.core;

import java.util.Set;

/**
 * Interface for persisting notification read state.
 * No business logic dependencies - pure storage abstraction.
 */
public interface NotificationStorage {

    /**
     * Retrieves the IDs of notifications that have been marked as read/dismissed.
     *
     * @return Set of notification IDs
     */
    Set<String> getReadNotificationIds();

    /**
     * Marks a notification as read/dismissed.
     *
     * @param notificationId The ID of the notification
     */
    void saveAsRead(String notificationId);

    /**
     * Gets the last plugin version for which notifications were displayed.
     *
     * @return Version string (e.g., "1.8.0"), or "0.0.0" if never notified
     */
    String getLastNotifiedVersion();

    /**
     * Updates the last notified version.
     *
     * @param version The version string
     */
    void updateLastNotifiedVersion(String version);

    /**
     * Tells whether the user asked never to see release notifications again.
     *
     * @return true if notifications are muted
     */
    boolean isMuted();

    /**
     * Mutes or unmutes release notifications.
     *
     * @param muted true to never display release notifications again
     */
    void setMuted(boolean muted);

    /**
     * Clears all notification state (for testing/debugging).
     */
    void reset();
}
