package fr.baretto.ollamassist.events;

import com.intellij.util.messages.Topic;

/**
 * Notifier for font settings changes.
 * Publish this event when font settings are modified to refresh UI components.
 */
public interface FontSettingsNotifier {

    Topic<FontSettingsNotifier> TOPIC = Topic.create("Font Settings Changed", FontSettingsNotifier.class);

    void onFontSettingsChanged();
}
