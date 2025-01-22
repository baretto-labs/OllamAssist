package fr.baretto.ollamassist.events;

import com.intellij.util.messages.Topic;

public interface ModelAvailableNotifier {

    Topic<ModelAvailableNotifier> TOPIC = Topic.create("Model Available", ModelAvailableNotifier.class);

    void onModelAvailable();
}
