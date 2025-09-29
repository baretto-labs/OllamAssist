package fr.baretto.ollamassist.core.events;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import fr.baretto.ollamassist.core.model.ChatRequest;
import fr.baretto.ollamassist.core.model.CompletionRequest;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

/**
 * Central event dispatcher using IntelliJ's MessageBus system.
 * Provides a clean API for publishing events across the application.
 */
@Slf4j
public class EventDispatcher {

    private final MessageBus messageBus;

    public EventDispatcher(@NotNull Project project) {
        this.messageBus = project.getMessageBus();
    }

    /**
     * Publishes a completion request event.
     *
     * @param request The completion request
     */
    public void publishCompletionRequest(@NotNull CompletionRequest request) {
        log.debug("Publishing completion request for context: {}", request.getContext().substring(0, Math.min(50, request.getContext().length())));
        messageBus.syncPublisher(OllamAssistEvents.COMPLETION_REQUESTED)
                .onCompletionRequested(request.getContext(), request.getFileExtension());
    }

    /**
     * Publishes a chat request event.
     *
     * @param request The chat request
     */
    public void publishChatRequest(@NotNull ChatRequest request) {
        log.debug("Publishing chat request: {}", request.getMessage().substring(0, Math.min(50, request.getMessage().length())));
        messageBus.syncPublisher(OllamAssistEvents.CHAT_REQUESTED)
                .onChatRequested(request.getMessage());
    }

    /**
     * Publishes a commit message request event.
     *
     * @param gitDiff The git diff content
     */
    public void publishCommitMessageRequest(@NotNull String gitDiff) {
        log.debug("Publishing commit message request for diff length: {}", gitDiff.length());
        messageBus.syncPublisher(OllamAssistEvents.COMMIT_MESSAGE_REQUESTED)
                .onCommitMessageRequested(gitDiff);
    }
}