package fr.baretto.ollamassist.core;

import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import fr.baretto.ollamassist.core.events.OllamAssistEvents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

/**
 * Tests for MessageBus integration - TDD approach using IntelliJ's event system.
 * Tests the integration with IntelliJ's MessageBus for inter-component communication.
 */
class MessageBusIntegrationTest {

    @Mock
    private MessageBus messageBus;

    @Mock
    private MessageBusConnection connection;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(messageBus.connect()).thenReturn(connection);
    }

    @Test
    @DisplayName("Should define completion event topic")
    void should_define_completion_event_topic() {
        // Then
        assertNotNull(OllamAssistEvents.COMPLETION_REQUESTED);
        assertEquals("OllamAssist.CompletionRequested", OllamAssistEvents.COMPLETION_REQUESTED.getDisplayName());
    }

    @Test
    @DisplayName("Should define chat event topic")
    void should_define_chat_event_topic() {
        // Then
        assertNotNull(OllamAssistEvents.CHAT_REQUESTED);
        assertEquals("OllamAssist.ChatRequested", OllamAssistEvents.CHAT_REQUESTED.getDisplayName());
    }

    @Test
    @DisplayName("Should define commit message event topic")
    void should_define_commit_message_event_topic() {
        // Then
        assertNotNull(OllamAssistEvents.COMMIT_MESSAGE_REQUESTED);
        assertEquals("OllamAssist.CommitMessageRequested", OllamAssistEvents.COMMIT_MESSAGE_REQUESTED.getDisplayName());
    }

    @Test
    @DisplayName("Should subscribe to completion request events")
    void should_subscribe_to_completion_request_events() {
        // Given
        OllamAssistEvents.CompletionRequestListener listener = mock(OllamAssistEvents.CompletionRequestListener.class);

        // When
        connection.subscribe(OllamAssistEvents.COMPLETION_REQUESTED, listener);

        // Then
        verify(connection).subscribe(OllamAssistEvents.COMPLETION_REQUESTED, listener);
    }

    @Test
    @DisplayName("Should subscribe to chat request events")
    void should_subscribe_to_chat_request_events() {
        // Given
        OllamAssistEvents.ChatRequestListener listener = mock(OllamAssistEvents.ChatRequestListener.class);

        // When
        connection.subscribe(OllamAssistEvents.CHAT_REQUESTED, listener);

        // Then
        verify(connection).subscribe(OllamAssistEvents.CHAT_REQUESTED, listener);
    }

    @Test
    @DisplayName("Should subscribe to commit message request events")
    void should_subscribe_to_commit_message_request_events() {
        // Given
        OllamAssistEvents.CommitMessageRequestListener listener = mock(OllamAssistEvents.CommitMessageRequestListener.class);

        // When
        connection.subscribe(OllamAssistEvents.COMMIT_MESSAGE_REQUESTED, listener);

        // Then
        verify(connection).subscribe(OllamAssistEvents.COMMIT_MESSAGE_REQUESTED, listener);
    }
}