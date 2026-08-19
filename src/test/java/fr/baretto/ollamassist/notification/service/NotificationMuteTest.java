package fr.baretto.ollamassist.notification.service;

import fr.baretto.ollamassist.notification.core.Notification;
import fr.baretto.ollamassist.notification.core.NotificationDisplayer;
import fr.baretto.ollamassist.notification.core.NotificationStorage;
import fr.baretto.ollamassist.notification.provider.NotificationProvider;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A user who asked never to see release notifications again must not see them again,
 * whatever the plugin version they upgrade to.
 */
@DisplayName("Muted release notifications")
class NotificationMuteTest {

    private InMemoryStorage storage;
    private RecordingDisplayer displayer;
    private NotificationManagerImpl manager;

    @BeforeEach
    void setUp() {
        storage = new InMemoryStorage();
        displayer = new RecordingDisplayer();
        manager = new NotificationManagerImpl(storage, new SingleNotificationProvider(), displayer, "2.0.0");
    }

    @Test
    @DisplayName("returns no notification once the user muted them")
    void shouldReturnNoNotificationWhenMuted() {
        storage.setMuted(true);

        assertThat(manager.getUnreadNotifications()).isEmpty();
    }

    @Test
    @DisplayName("returns pending notifications as long as the user did not mute them")
    void shouldReturnPendingNotificationsWhenNotMuted() {
        assertThat(manager.getUnreadNotifications()).hasSize(1);
    }

    @Test
    @DisplayName("displays nothing at startup once the user muted them")
    void shouldDisplayNothingWhenMuted() {
        storage.setMuted(true);

        manager.displayPendingNotifications(null);

        assertThat(displayer.shown).isEmpty();
    }

    @Test
    @DisplayName("persists the decision when the user mutes notifications")
    void shouldPersistMuteDecision() {
        manager.muteNotifications();

        assertThat(storage.isMuted()).isTrue();
    }

    @Test
    @DisplayName("reports whether notifications are currently muted")
    void shouldReportMuteState() {
        assertThat(manager.areNotificationsMuted()).isFalse();

        manager.muteNotifications();

        assertThat(manager.areNotificationsMuted()).isTrue();
    }

    @Test
    @DisplayName("shows notifications again once the user unmutes them from the settings")
    void shouldShowNotificationsAgainWhenUnmuted() {
        manager.muteNotifications();

        manager.setNotificationsMuted(false);

        assertThat(manager.getUnreadNotifications()).hasSize(1);
    }

    private static final class InMemoryStorage implements NotificationStorage {
        private final Set<String> readIds = new HashSet<>();
        private String lastNotifiedVersion = "0.0.0";
        private boolean muted = false;

        @Override
        public Set<String> getReadNotificationIds() {
            return new HashSet<>(readIds);
        }

        @Override
        public void saveAsRead(String notificationId) {
            readIds.add(notificationId);
        }

        @Override
        public String getLastNotifiedVersion() {
            return lastNotifiedVersion;
        }

        @Override
        public void updateLastNotifiedVersion(String version) {
            lastNotifiedVersion = version;
        }

        @Override
        public boolean isMuted() {
            return muted;
        }

        @Override
        public void setMuted(boolean muted) {
            this.muted = muted;
        }

        @Override
        public void reset() {
            readIds.clear();
            lastNotifiedVersion = "0.0.0";
            muted = false;
        }
    }

    private static final class RecordingDisplayer implements NotificationDisplayer {
        private final List<Notification> shown = new ArrayList<>();

        @Override
        public void show(Project project, List<Notification> notifications) {
            shown.addAll(notifications);
        }
    }

    private static final class SingleNotificationProvider implements NotificationProvider {
        @Override
        public List<Notification> getAllNotifications() {
            return List.of(Notification.builder()
                    .id("v2.0.0-release")
                    .version("2.0.0")
                    .type(Notification.NotificationType.FEATURE)
                    .priority(Notification.Priority.MEDIUM)
                    .title("Release 2.0.0")
                    .message("<html><body>Something new</body></html>")
                    .dismissible(true)
                    .createdAt(LocalDateTime.of(2026, 8, 19, 0, 0))
                    .build());
        }
    }
}
