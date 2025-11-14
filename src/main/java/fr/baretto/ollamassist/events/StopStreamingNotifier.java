package fr.baretto.ollamassist.events;

import com.intellij.util.messages.Topic;

public interface StopStreamingNotifier {
    Topic<StopStreamingNotifier> TOPIC = Topic.create("Stop LLM Streaming", StopStreamingNotifier.class);

    void stopStreaming();
}
