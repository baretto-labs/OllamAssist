package fr.baretto.ollamassist.notification.core;

import com.intellij.openapi.project.Project;

import java.util.List;

/**
 * Interface for displaying notifications to users.
 * Abstracts the UI implementation from business logic.
 */
public interface NotificationDisplayer {

    /**
     * Displays notifications to the user.
     *
     * @param project       The current project context
     * @param notifications List of notifications to display
     */
    void show(Project project, List<Notification> notifications);
}
