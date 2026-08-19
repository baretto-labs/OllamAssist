package fr.baretto.ollamassist.notification;

import com.intellij.util.xmlb.XmlSerializer;
import fr.baretto.ollamassist.notification.storage.PersistentNotificationStorage;
import org.jdom.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The notification state must survive an IDE restart: a user who muted release
 * notifications would otherwise see them again on the next start.
 *
 * @see fr.baretto.ollamassist.setting.SettingsStatePersistenceTest
 */
@DisplayName("Notification state serialization round-trip")
class NotificationStorageStatePersistenceTest {

    private static PersistentNotificationStorage restart(PersistentNotificationStorage storage) {
        Element serialized = XmlSerializer.serialize(storage.getState());
        PersistentNotificationStorage restarted = new PersistentNotificationStorage();
        restarted.loadState(XmlSerializer.deserialize(serialized, PersistentNotificationStorage.State.class));
        return restarted;
    }

    @Test
    @DisplayName("keeps the mute decision across an IDE restart")
    void shouldPersistMuteDecision() {
        PersistentNotificationStorage storage = new PersistentNotificationStorage();
        storage.setMuted(true);

        assertThat(restart(storage).isMuted()).isTrue();
    }

    @Test
    @DisplayName("keeps dismissed notification ids across an IDE restart")
    void shouldPersistReadNotificationIds() {
        PersistentNotificationStorage storage = new PersistentNotificationStorage();
        storage.saveAsRead("v1.13.1-release");

        assertThat(restart(storage).getReadNotificationIds()).containsExactly("v1.13.1-release");
    }

    @Test
    @DisplayName("keeps the last notified version across an IDE restart")
    void shouldPersistLastNotifiedVersion() {
        PersistentNotificationStorage storage = new PersistentNotificationStorage();
        storage.updateLastNotifiedVersion("1.13.1");

        assertThat(restart(storage).getLastNotifiedVersion()).isEqualTo("1.13.1");
    }

    @Test
    @DisplayName("is not muted by default")
    void shouldNotBeMutedByDefault() {
        assertThat(new PersistentNotificationStorage().isMuted()).isFalse();
    }
}
