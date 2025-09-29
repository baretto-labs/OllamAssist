package fr.baretto.ollamassist.core.service;

import fr.baretto.ollamassist.core.model.ChatRequest;
import fr.baretto.ollamassist.core.model.CompletionRequest;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for AI model assistance services.
 * Replaces static LightModelAssistant calls with dependency injection.
 */
public interface ModelAssistant {

    /**
     * Handles chat conversation with AI model.
     *
     * @param request Chat request with message and context
     * @return Future containing AI response
     */
    CompletableFuture<String> chat(ChatRequest request);

    /**
     * Handles code completion requests.
     *
     * @param request Completion request with context and file info
     * @return Future containing code completion suggestion
     */
    CompletableFuture<String> complete(CompletionRequest request);

    /**
     * Generates commit message from git diff.
     *
     * @param gitDiff Git diff content
     * @return Future containing generated commit message
     */
    CompletableFuture<String> writeCommitMessage(String gitDiff);

    /**
     * Creates web search query from user input.
     *
     * @param userInput User input to transform into search query
     * @return Search query string
     */
    String createWebSearchQuery(String userInput);

    /**
     * Gets request statistics for monitoring.
     *
     * @return Request statistics
     */
    RequestStats getRequestStats();

    /**
     * Statistics interface for model assistant requests.
     */
    interface RequestStats {
        int getTotalRequests();

        int getChatRequests();

        int getCompletionRequests();

        int getCommitMessageRequests();
    }
}