package fr.baretto.ollamassist.notification.service;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.notification.core.Notification;
import fr.baretto.ollamassist.notification.core.NotificationDisplayer;
import fr.baretto.ollamassist.notification.core.NotificationManager;
import fr.baretto.ollamassist.notification.core.NotificationStorage;
import fr.baretto.ollamassist.notification.provider.NotificationProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Implementation of NotificationManager with version comparison and cumulative notification logic.
 */
@Slf4j
public final class NotificationManagerImpl implements NotificationManager {

    private final NotificationStorage storage;
    private final NotificationProvider provider;
    private final NotificationDisplayer displayer;
    private final String currentPluginVersion;

    public NotificationManagerImpl() {
        NotificationStorage storageService = ApplicationManager.getApplication().getService(NotificationStorage.class);
        NotificationProvider providerService = ApplicationManager.getApplication().getService(NotificationProvider.class);
        NotificationDisplayer displayerService = ApplicationManager.getApplication().getService(NotificationDisplayer.class);

        if (storageService == null || providerService == null || displayerService == null) {
            log.error("Failed to initialize NotificationManager: One or more services are null");
            throw new IllegalStateException("NotificationManager dependencies not available");
        }

        this.storage = storageService;
        this.provider = providerService;
        this.displayer = displayerService;
        this.currentPluginVersion = getCurrentPluginVersion();

        log.info("NotificationManager initialized with plugin version: {}", currentPluginVersion);
    }

    @Override
    public List<Notification> getUnreadNotifications() {
        String lastNotifiedVersion = storage.getLastNotifiedVersion();
        Set<String> readIds = storage.getReadNotificationIds();

        log.debug("Getting unread notifications. Last notified version: {}, Read IDs: {}",
                lastNotifiedVersion, readIds);

        List<Notification> allNotifications = provider.getAllNotifications();

        List<Notification> unread = allNotifications.stream()
                // 1. Filter notifications for versions > lastNotifiedVersion
                .filter(n -> isVersionGreater(n.getVersion(), lastNotifiedVersion))
                // 2. Filter out manually dismissed notifications
                .filter(n -> !readIds.contains(n.getId()))
                // 3. Sort by priority (HIGH first), then by version (newest first)
                .sorted(Comparator
                        .comparing(Notification::getPriority).reversed()
                        .thenComparing(Notification::getVersion, (v1, v2) -> compareVersions(v2, v1)))
                .toList();

        log.info("Found {} unread notifications", unread.size());
        return unread;
    }

    @Override
    public void markAsRead(String notificationId) {
        log.info("Marking notification as read: {}", notificationId);
        storage.saveAsRead(notificationId);
    }

    @Override
    public void displayPendingNotifications(Project project) {
        List<Notification> unread = getUnreadNotifications();

        if (unread.isEmpty()) {
            log.debug("No unread notifications to display");
            return;
        }

        log.info("Displaying {} pending notifications", unread.size());
        displayer.show(project, unread);
    }

    @Override
    public void updateLastNotifiedVersion() {
        storage.updateLastNotifiedVersion(currentPluginVersion);
        log.info("Updated last notified version to: {}", currentPluginVersion);
    }

    @Override
    public void resetNotificationState() {
        log.info("Resetting notification state");
        storage.reset();
    }

    /**
     * Compares two semantic versions (e.g., "1.9.0" vs "1.10.0").
     *
     * @return -1 if v1 < v2, 0 if equal, 1 if v1 > v2
     */
    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        int maxLength = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < maxLength; i++) {
            int n1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int n2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;

            if (n1 != n2) {
                return Integer.compare(n1, n2);
            }
        }

        return 0;
    }

    /**
     * Parses a version part, handling non-numeric suffixes (e.g., "1.9.0-beta" → 1).
     */
    private int parseVersionPart(String part) {
        try {
            // Extract numeric part only (e.g., "0-beta" → "0")
            String numericPart = part.split("-")[0];
            return Integer.parseInt(numericPart);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse version part: {}", part);
            return 0;
        }
    }

    /**
     * Checks if version is greater than reference version.
     */
    private boolean isVersionGreater(String version, String reference) {
        return compareVersions(version, reference) > 0;
    }

    /**
     * Retrieves the current plugin version from IntelliJ's plugin descriptor.
     */
    private String getCurrentPluginVersion() {
        try {
            PluginId pluginId = PluginId.getId("fr.baretto.ollamassist");
            IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(pluginId);
            return plugin != null ? plugin.getVersion() : "0.0.0";
        } catch (Exception e) {
            log.error("Failed to retrieve plugin version", e);
            return "0.0.0";
        }
    }
}
