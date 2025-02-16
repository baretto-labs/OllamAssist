package fr.baretto.ollamassist.events;

import com.intellij.util.messages.Topic;

public interface NewUserMessageNotifier {

    Topic<NewUserMessageNotifier> TOPIC = Topic.create("New user message", NewUserMessageNotifier.class);

    void newUserMessage(String message);
}
