package fr.baretto.ollamassist.agent;

import com.intellij.util.messages.Topic;

public interface AgentProgressNotifier {

    Topic<AgentProgressNotifier> TOPIC = Topic.create(
            "Agent Progress",
            AgentProgressNotifier.class,
            Topic.BroadcastDirection.NONE
    );

    void onProgress(AgentProgressEvent event);
}
