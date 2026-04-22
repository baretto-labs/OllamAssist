package fr.baretto.ollamassist.events;

import com.intellij.util.messages.Topic;

public interface NewAgentRequestNotifier {

    Topic<NewAgentRequestNotifier> TOPIC = Topic.create(
            "New Agent Request",
            NewAgentRequestNotifier.class,
            Topic.BroadcastDirection.NONE
    );

    void newAgentRequest(String goal);
}
