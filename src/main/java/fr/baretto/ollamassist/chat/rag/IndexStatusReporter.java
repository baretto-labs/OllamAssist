package fr.baretto.ollamassist.chat.rag;

/**
 * Tells the user what happens to the KnowledgeIndex. Implemented by an IDE notification adapter.
 */
public interface IndexStatusReporter {

    void info(String message);

    void warning(String message);
}
