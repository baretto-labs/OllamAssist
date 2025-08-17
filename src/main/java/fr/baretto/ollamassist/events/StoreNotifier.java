package fr.baretto.ollamassist.events;

import com.intellij.util.messages.Topic;

public interface StoreNotifier {

    Topic<StoreNotifier> TOPIC = Topic.create("Clear Embedding store", StoreNotifier.class, Topic.BroadcastDirection.NONE);

    void clear();

    void clearDatabaseAndRunIndexation();
}
