package fr.baretto.ollamassist.events;

import com.intellij.util.messages.Topic;

public interface ConversationNotifier {

    Topic<ConversationNotifier> TOPIC = Topic.create("Conversation Notifier", ConversationNotifier.class);

    void newConversation();
}
