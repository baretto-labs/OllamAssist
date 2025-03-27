package fr.baretto.ollamassist.events;

import com.intellij.util.messages.Topic;

public interface ChatModelModifiedNotifier {

    Topic<ChatModelModifiedNotifier> TOPIC = Topic.create("Model Available", ChatModelModifiedNotifier.class);

    void onChatModelModified();
}
