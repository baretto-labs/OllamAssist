package fr.baretto.ollamassist.notification.core;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Represents a notification to be displayed to users after plugin updates.
 * Notifications are version-specific and can be cumulative across multiple versions.
 */
@Getter
@Builder
public class Notification {

    /**
     * Unique identifier for this notification (e.g., "v1.9.0-agent-warning")
     */
    private final String id;

    /**
     * Version when this notification was introduced (e.g., "1.9.0")
     */
    private final String version;

    /**
     * Type of notification affecting icon and styling
     */
    private final NotificationType type;

    /**
     * Short title displayed prominently
     */
    private final String title;

    /**
     * Full message content (supports Markdown/HTML)
     */
    private final String message;

    /**
     * When this notification was created
     */
    private final LocalDateTime createdAt;

    /**
     * Priority determines display order
     */
    private final Priority priority;

    /**
     * Whether user can dismiss this notification with "Don't show again"
     */
    private final boolean dismissible;

    public enum NotificationType {
        INFO,              // 💡 General information
        WARNING,           // ⚠️ Warning/caution
        FEATURE,           // 🎉 New feature announcement
        BREAKING_CHANGE    // 🔴 Breaking change requiring attention
    }

    public enum Priority {
        LOW,      // Show only if user opens manually
        MEDIUM,   // Show at startup
        HIGH      // Show immediately (modal)
    }
}
