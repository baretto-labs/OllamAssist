package fr.baretto.ollamassist.setting;

import com.intellij.util.messages.Topic;

public interface SettingsListener {

    Topic<SettingsListener> TOPIC =
            Topic.create("Settings Changed", SettingsListener.class);

    void settingsChanged(OllamAssistSettings.State newState);
}
