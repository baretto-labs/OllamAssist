package fr.baretto.ollamassist.core.session;

import com.intellij.openapi.components.Service;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages chat sessions and conversation contexts for the plugin.
 * Thread-safe implementation with automatic cleanup of expired sessions.
 * IntelliJ Project Service - one instance per project with project-scoped sessions.
 */
@Slf4j
@Service(Service.Level.PROJECT)
public final class SessionManager {

    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();
    private Duration sessionTimeout = Duration.ofMinutes(30);

    /**
     * Creates a new chat session.
     *
     * @param title The session title
     * @return New chat session
     */
    public ChatSession createChatSession(@NotNull String title) {
        ChatSession session = new ChatSession(title, SessionType.CHAT);
        sessions.put(session.getId(), session);
        log.debug("Created chat session: {} - {}", session.getId(), title);
        return session;
    }

    /**
     * Creates a new code completion session.
     *
     * @param context The completion context (e.g., filename)
     * @return New completion session
     */
    public ChatSession createCompletionSession(@NotNull String context) {
        ChatSession session = new ChatSession(context, SessionType.COMPLETION);
        sessions.put(session.getId(), session);
        log.debug("Created completion session: {} - {}", session.getId(), context);
        return session;
    }

    /**
     * Creates a new agent session.
     *
     * @param title The agent session title
     * @return New agent session
     */
    public ChatSession createAgentSession(@NotNull String title) {
        ChatSession session = new ChatSession(title, SessionType.AGENT);
        sessions.put(session.getId(), session);
        log.debug("Created agent session: {} - {}", session.getId(), title);
        return session;
    }

    /**
     * Gets a session by ID.
     *
     * @param sessionId The session ID
     * @return Session or null if not found
     */
    @Nullable
    public ChatSession getSessionById(@NotNull String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Gets all active sessions.
     *
     * @return List of active sessions
     */
    public List<ChatSession> getActiveSessions() {
        return sessions.values().stream()
                .filter(ChatSession::isActive)
                .collect(Collectors.toList());
    }

    /**
     * Gets active sessions by type.
     *
     * @param type The session type
     * @return List of sessions of the specified type
     */
    public List<ChatSession> getSessionsByType(@NotNull SessionType type) {
        return sessions.values().stream()
                .filter(ChatSession::isActive)
                .filter(session -> session.getType() == type)
                .collect(Collectors.toList());
    }

    /**
     * Closes a session by ID.
     *
     * @param sessionId The session ID to close
     */
    public void closeSession(@NotNull String sessionId) {
        ChatSession session = sessions.get(sessionId);
        if (session != null) {
            session.close();
            log.debug("Closed session: {}", sessionId);
        }
    }

    /**
     * Clears all sessions.
     */
    public void clearAllSessions() {
        sessions.values().forEach(ChatSession::close);
        sessions.clear();
        log.debug("Cleared all sessions");
    }

    /**
     * Sets the session timeout duration.
     *
     * @param timeout Timeout duration
     */
    public void setSessionTimeout(@NotNull Duration timeout) {
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("Session timeout cannot be negative");
        }
        this.sessionTimeout = timeout;
        log.debug("Set session timeout to: {}", timeout);
    }

    /**
     * Cleans up expired sessions.
     * Should be called periodically to free memory.
     */
    public void cleanupExpiredSessions() {
        List<String> expiredSessionIds = new ArrayList<>();

        for (ChatSession session : sessions.values()) {
            if (session.isExpired(sessionTimeout)) {
                session.close();
                expiredSessionIds.add(session.getId());
            }
        }

        for (String sessionId : expiredSessionIds) {
            sessions.remove(sessionId);
        }

        if (!expiredSessionIds.isEmpty()) {
            log.debug("Cleaned up {} expired sessions", expiredSessionIds.size());
        }
    }

    /**
     * Gets session statistics.
     *
     * @return Session statistics
     */
    public SessionStats getSessionStats() {
        Map<SessionType, Long> sessionsByType = sessions.values().stream()
                .filter(ChatSession::isActive)
                .collect(Collectors.groupingBy(ChatSession::getType, Collectors.counting()));

        int totalMessages = sessions.values().stream()
                .filter(ChatSession::isActive)
                .mapToInt(session -> session.getMessages().size())
                .sum();

        return new SessionStats(
                sessions.size(),
                (int) sessions.values().stream().filter(ChatSession::isActive).count(),
                sessionsByType,
                totalMessages
        );
    }

    /**
     * Session statistics.
     */
    public static class SessionStats {
        private final int totalSessions;
        private final int activeSessions;
        private final Map<SessionType, Long> sessionsByType;
        private final int totalMessages;

        public SessionStats(int totalSessions, int activeSessions, Map<SessionType, Long> sessionsByType, int totalMessages) {
            this.totalSessions = totalSessions;
            this.activeSessions = activeSessions;
            this.sessionsByType = new EnumMap<>(SessionType.class);
            if (!sessionsByType.isEmpty()) {
                this.sessionsByType.putAll(sessionsByType);
            }
            this.totalMessages = totalMessages;
        }

        public int getTotalSessions() {
            return totalSessions;
        }

        public int getTotalActiveSessions() {
            return activeSessions;
        }

        public int getActiveSessionsByType(SessionType type) {
            return sessionsByType.getOrDefault(type, 0L).intValue();
        }

        public int getTotalMessages() {
            return totalMessages;
        }

        @Override
        public String toString() {
            return String.format("SessionStats{total=%d, active=%d, messages=%d}",
                    totalSessions, activeSessions, totalMessages);
        }
    }
}