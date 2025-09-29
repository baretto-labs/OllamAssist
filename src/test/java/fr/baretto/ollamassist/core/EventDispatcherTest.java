package fr.baretto.ollamassist.core;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import fr.baretto.ollamassist.core.events.EventDispatcher;
import fr.baretto.ollamassist.core.events.OllamAssistEvents;
import fr.baretto.ollamassist.core.model.ChatRequest;
import fr.baretto.ollamassist.core.model.CompletionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for EventDispatcher - TDD approach for event publishing via IntelliJ MessageBus.
 */
class EventDispatcherTest {

    @Mock
    private Project project;

    @Mock
    private MessageBus messageBus;

    @Mock
    private OllamAssistEvents.CompletionRequestListener completionPublisher;

    @Mock
    private OllamAssistEvents.ChatRequestListener chatPublisher;

    @Mock
    private OllamAssistEvents.CommitMessageRequestListener commitPublisher;

    private EventDispatcher eventDispatcher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(project.getMessageBus()).thenReturn(messageBus);
        when(messageBus.syncPublisher(OllamAssistEvents.COMPLETION_REQUESTED)).thenReturn(completionPublisher);
        when(messageBus.syncPublisher(OllamAssistEvents.CHAT_REQUESTED)).thenReturn(chatPublisher);
        when(messageBus.syncPublisher(OllamAssistEvents.COMMIT_MESSAGE_REQUESTED)).thenReturn(commitPublisher);

        eventDispatcher = new EventDispatcher(project);
    }

    @Test
    @DisplayName("Should publish completion request event")
    void should_publish_completion_request_event() {
        // Given
        CompletionRequest request = CompletionRequest.builder()
                .context("public class Test {")
                .fileExtension("java")
                .build();

        // When
        eventDispatcher.publishCompletionRequest(request);

        // Then
        verify(messageBus).syncPublisher(OllamAssistEvents.COMPLETION_REQUESTED);
        verify(completionPublisher).onCompletionRequested("public class Test {", "java");
    }

    @Test
    @DisplayName("Should publish chat request event")
    void should_publish_chat_request_event() {
        // Given
        ChatRequest request = ChatRequest.builder()
                .message("Hello, how are you?")
                .build();

        // When
        eventDispatcher.publishChatRequest(request);

        // Then
        verify(messageBus).syncPublisher(OllamAssistEvents.CHAT_REQUESTED);
        verify(chatPublisher).onChatRequested("Hello, how are you?");
    }

    @Test
    @DisplayName("Should publish commit message request event")
    void should_publish_commit_message_request_event() {
        // Given
        String gitDiff = "diff --git a/Test.java b/Test.java\nnew file mode 100644";

        // When
        eventDispatcher.publishCommitMessageRequest(gitDiff);

        // Then
        verify(messageBus).syncPublisher(OllamAssistEvents.COMMIT_MESSAGE_REQUESTED);
        verify(commitPublisher).onCommitMessageRequested(gitDiff);
    }
}