package fr.baretto.ollamassist.notification.storage;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import fr.baretto.ollamassist.notification.core.NotificationStorage;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Persistent storage for notification state using IntelliJ's persistence mechanism.
 * Stores read notification IDs and last notified version.
 */
@State(name = "OllamAssistNotifications", storages = @Storage("OllamAssistNotifications.xml"))
public final class PersistentNotificationStorage implements NotificationStorage, PersistentStateComponent<PersistentNotificationStorage.State> {

    private State state = new State();

    public static PersistentNotificationStorage getInstance() {
        return ApplicationManager.getApplication().getService(PersistentNotificationStorage.class);
    }

    @Override
    public Set<String> getReadNotificationIds() {
        return new HashSet<>(state.readNotificationIds);
    }

    @Override
    public void saveAsRead(String notificationId) {
        state.readNotificationIds.add(notificationId);
    }

    @Override
    public String getLastNotifiedVersion() {
        return state.lastNotifiedVersion;
    }

    @Override
    public void updateLastNotifiedVersion(String version) {
        state.lastNotifiedVersion = version;
    }

    @Override
    public void reset() {
        state.readNotificationIds.clear();
        state.lastNotifiedVersion = "0.0.0";
    }

    @Nullable
    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    @Getter
    @Setter
    public static class State {
        private Set<String> readNotificationIds = new HashSet<>();
        private String lastNotifiedVersion = "0.0.0";
    }
}
