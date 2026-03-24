package fr.baretto.ollamassist.events;

import com.intellij.util.messages.Topic;
import fr.baretto.ollamassist.conversation.Conversation;

public interface ConversationSwitchedNotifier {

    Topic<ConversationSwitchedNotifier> TOPIC = Topic.create(
            "Conversation Switched",
            ConversationSwitchedNotifier.class,
            Topic.BroadcastDirection.NONE);

    void conversationSwitched(Conversation conversation);
}
