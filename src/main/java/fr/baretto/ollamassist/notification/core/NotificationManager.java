package fr.baretto.ollamassist.notification.core;

import com.intellij.openapi.project.Project;

import java.util.List;

/**
 * Core manager for notification system.
 * Handles notification filtering, versioning, and display orchestration.
 */
public interface NotificationManager {

    /**
     * Gets all unread notifications for versions newer than the last notified version.
     * This includes cumulative notifications across multiple skipped versions.
     *
     * @return List of unread notifications, sorted by priority and version
     */
    List<Notification> getUnreadNotifications();

    /**
     * Marks a notification as read/dismissed by the user.
     *
     * @param notificationId The ID of the notification to mark as read
     */
    void markAsRead(String notificationId);

    /**
     * Displays all pending notifications to the user.
     *
     * @param project The current project context
     */
    void displayPendingNotifications(Project project);

    /**
     * Updates the last notified version to the current plugin version.
     * Should be called after the user acknowledges/closes the notification dialog.
     */
    void updateLastNotifiedVersion();

    /**
     * Tells whether the user asked never to see release notifications again.
     *
     * @return true if notifications are muted
     */
    boolean areNotificationsMuted();

    /**
     * Mutes release notifications: nothing will be displayed again, whatever the
     * version the user upgrades to.
     */
    void muteNotifications();

    /**
     * Mutes or unmutes release notifications. Called from the settings panel, so that a
     * muted user has a way back.
     *
     * @param muted true to never display release notifications again
     */
    void setNotificationsMuted(boolean muted);

    /**
     * Resets all notification state (for testing/debugging).
     */
    void resetNotificationState();
}
