package fr.baretto.ollamassist.core;

import fr.baretto.ollamassist.core.session.ChatSession;
import fr.baretto.ollamassist.core.session.SessionManager;
import fr.baretto.ollamassist.core.session.SessionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SessionManager - TDD approach for session and conversation management.
 * Tests the unified session system with context tracking and cleanup.
 */
class SessionManagerTest {

    private SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
    }

    @Test
    @DisplayName("Should create SessionManager without error")
    void should_create_session_manager_without_error() {
        // Then
        assertNotNull(sessionManager);
        assertTrue(sessionManager.getActiveSessions().isEmpty());
    }

    @Test
    @DisplayName("Should create new chat session")
    void should_create_new_chat_session() {
        // When
        ChatSession session = sessionManager.createChatSession("Test chat session");

        // Then
        assertNotNull(session);
        assertEquals("Test chat session", session.getTitle());
        assertEquals(SessionType.CHAT, session.getType());
        assertTrue(session.isActive());
        assertEquals(1, sessionManager.getActiveSessions().size());
        assertTrue(sessionManager.getActiveSessions().contains(session));
    }

    @Test
    @DisplayName("Should create new completion session")
    void should_create_new_completion_session() {
        // When
        ChatSession session = sessionManager.createCompletionSession("completion-context.java");

        // Then
        assertNotNull(session);
        assertEquals("completion-context.java", session.getTitle());
        assertEquals(SessionType.COMPLETION, session.getType());
        assertTrue(session.isActive());
    }

    @Test
    @DisplayName("Should add messages to session")
    void should_add_messages_to_session() {
        // Given
        ChatSession session = sessionManager.createChatSession("Test session");

        // When
        session.addMessage("user", "Hello, how are you?");
        session.addMessage("assistant", "I'm doing well, thank you!");

        // Then
        List<ChatSession.Message> messages = session.getMessages();
        assertEquals(2, messages.size());

        assertEquals("user", messages.get(0).getRole());
        assertEquals("Hello, how are you?", messages.get(0).getContent());

        assertEquals("assistant", messages.get(1).getRole());
        assertEquals("I'm doing well, thank you!", messages.get(1).getContent());
    }

    @Test
    @DisplayName("Should limit session message history")
    void should_limit_session_message_history() {
        // Given
        ChatSession session = sessionManager.createChatSession("Test session");
        session.setMaxMessages(3);

        // When - add more messages than limit
        session.addMessage("user", "Message 1");
        session.addMessage("assistant", "Response 1");
        session.addMessage("user", "Message 2");
        session.addMessage("assistant", "Response 2");
        session.addMessage("user", "Message 3");

        // Then
        List<ChatSession.Message> messages = session.getMessages();
        assertEquals(3, messages.size());
        assertEquals("Message 2", messages.get(0).getContent());
        assertEquals("Response 2", messages.get(1).getContent());
        assertEquals("Message 3", messages.get(2).getContent());
    }

    @Test
    @DisplayName("Should find sessions by ID")
    void should_find_sessions_by_id() {
        // Given
        ChatSession session1 = sessionManager.createChatSession("Session 1");
        ChatSession session2 = sessionManager.createChatSession("Session 2");

        // When
        ChatSession found1 = sessionManager.getSessionById(session1.getId());
        ChatSession found2 = sessionManager.getSessionById(session2.getId());
        ChatSession notFound = sessionManager.getSessionById("non-existent");

        // Then
        assertEquals(session1, found1);
        assertEquals(session2, found2);
        assertNull(notFound);
    }

    @Test
    @DisplayName("Should close sessions")
    void should_close_sessions() {
        // Given
        ChatSession session = sessionManager.createChatSession("Test session");
        assertTrue(session.isActive());
        assertEquals(1, sessionManager.getActiveSessions().size());

        // When
        sessionManager.closeSession(session.getId());

        // Then
        assertFalse(session.isActive());
        assertEquals(0, sessionManager.getActiveSessions().size());
    }

    @Test
    @DisplayName("Should get sessions by type")
    void should_get_sessions_by_type() {
        // Given
        ChatSession chatSession1 = sessionManager.createChatSession("Chat 1");
        ChatSession chatSession2 = sessionManager.createChatSession("Chat 2");
        ChatSession completionSession = sessionManager.createCompletionSession("completion.java");

        // When
        List<ChatSession> chatSessions = sessionManager.getSessionsByType(SessionType.CHAT);
        List<ChatSession> completionSessions = sessionManager.getSessionsByType(SessionType.COMPLETION);

        // Then
        assertEquals(2, chatSessions.size());
        assertTrue(chatSessions.contains(chatSession1));
        assertTrue(chatSessions.contains(chatSession2));

        assertEquals(1, completionSessions.size());
        assertTrue(completionSessions.contains(completionSession));
    }

    @Test
    @DisplayName("Should cleanup expired sessions automatically")
    void should_cleanup_expired_sessions_automatically() {
        // Given
        sessionManager.setSessionTimeout(Duration.ofMillis(100));
        ChatSession session = sessionManager.createChatSession("Test session");

        assertEquals(1, sessionManager.getActiveSessions().size());

        // When - wait for session to expire
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        sessionManager.cleanupExpiredSessions();

        // Then
        assertEquals(0, sessionManager.getActiveSessions().size());
        assertFalse(session.isActive());
    }

    @Test
    @DisplayName("Should not cleanup active sessions")
    void should_not_cleanup_active_sessions() {
        // Given
        sessionManager.setSessionTimeout(Duration.ofMillis(100));
        ChatSession session = sessionManager.createChatSession("Test session");

        // When - update activity before expiry
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        session.addMessage("user", "Keep alive message");

        try {
            Thread.sleep(75);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        sessionManager.cleanupExpiredSessions();

        // Then
        assertEquals(1, sessionManager.getActiveSessions().size());
        assertTrue(session.isActive());
    }

    @Test
    @DisplayName("Should provide session statistics")
    void should_provide_session_statistics() {
        // Given
        ChatSession chatSession = sessionManager.createChatSession("Chat session");
        ChatSession completionSession = sessionManager.createCompletionSession("completion.java");

        chatSession.addMessage("user", "Hello");
        chatSession.addMessage("assistant", "Hi there!");
        completionSession.addMessage("context", "public class");

        // When
        SessionManager.SessionStats stats = sessionManager.getSessionStats();

        // Then
        assertNotNull(stats);
        assertEquals(2, stats.getTotalActiveSessions());
        assertEquals(1, stats.getActiveSessionsByType(SessionType.CHAT));
        assertEquals(1, stats.getActiveSessionsByType(SessionType.COMPLETION));
        assertEquals(3, stats.getTotalMessages());
    }

    @Test
    @DisplayName("Should clear all sessions")
    void should_clear_all_sessions() {
        // Given
        sessionManager.createChatSession("Session 1");
        sessionManager.createChatSession("Session 2");
        sessionManager.createCompletionSession("completion.java");

        assertEquals(3, sessionManager.getActiveSessions().size());

        // When
        sessionManager.clearAllSessions();

        // Then
        assertEquals(0, sessionManager.getActiveSessions().size());
        assertEquals(0, sessionManager.getSessionStats().getTotalActiveSessions());
    }

    @Test
    @DisplayName("Should get session context for AI processing")
    void should_get_session_context_for_ai_processing() {
        // Given
        ChatSession session = sessionManager.createChatSession("Test session");
        session.addMessage("user", "What is Java?");
        session.addMessage("assistant", "Java is a programming language.");
        session.addMessage("user", "Tell me more about classes");

        // When
        String context = session.getContextForAI();

        // Then
        assertNotNull(context);
        assertTrue(context.contains("What is Java?"));
        assertTrue(context.contains("Java is a programming language."));
        assertTrue(context.contains("Tell me more about classes"));
    }

    @Test
    @DisplayName("Should handle concurrent session operations safely")
    void should_handle_concurrent_session_operations_safely() throws InterruptedException {
        // Given
        final int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        // When - create sessions concurrently
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                sessionManager.createChatSession("Session " + index);
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Then
        assertEquals(threadCount, sessionManager.getActiveSessions().size());
    }
}