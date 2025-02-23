package fr.baretto.ollamassist.events;

import com.intellij.util.messages.Topic;

public interface PrerequisteAvailableNotifier {

    Topic<PrerequisteAvailableNotifier> TOPIC = Topic.create("Prerequisite Available", PrerequisteAvailableNotifier.class);

    void prerequisiteAvailable();

}
