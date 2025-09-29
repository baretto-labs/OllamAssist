package fr.baretto.ollamassist.core.agent;

/**
 * Interface pour déléguer le traitement des questions au chat classique
 */
@FunctionalInterface
public interface ChatFallbackHandler {
    void processUserMessage(String message);
}