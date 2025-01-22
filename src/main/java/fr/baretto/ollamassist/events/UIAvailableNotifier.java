package fr.baretto.ollamassist.events;

import com.intellij.util.messages.Topic;

public interface UIAvailableNotifier {

    Topic<UIAvailableNotifier> TOPIC = Topic.create("UI Available", UIAvailableNotifier.class);

    void onUIAvailable();

}
