package fr.baretto.ollamassist.events;

import com.intellij.util.messages.Topic;

public interface StoreNotifier {

    Topic<StoreNotifier> TOPIC = Topic.create("Clear Embedding store", StoreNotifier.class, Topic.BroadcastDirection.NONE);

    /** The user asked to clear the index of this project. */
    void clear();

    /** RAG was switched on. What is already indexed stays valid. */
    void ragSwitchedOn();

    /** The embedding model or its URL changed: every existing vector is invalid. */
    void embeddingModelChanged();

    /** The index of this project could not be read and has to be rebuilt. */
    void indexCorrupted();
}
