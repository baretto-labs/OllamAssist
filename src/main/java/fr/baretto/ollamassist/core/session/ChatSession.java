package fr.baretto.ollamassist.core.session;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Represents a chat session with message history and context management.
 */
@Slf4j
@Getter
public class ChatSession {

    private final String id;
    private final String title;
    private final SessionType type;
    private final LocalDateTime createdAt;
    private final List<Message> messages = new ArrayList<>();

    @Setter
    private int maxMessages = 50;
    private volatile LocalDateTime lastActivity;
    private volatile boolean active = true;

    public ChatSession(String title, SessionType type) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.type = type;
        this.createdAt = LocalDateTime.now();
        this.lastActivity = LocalDateTime.now();

        log.debug("Created session: {} [{}] - {}", id, type, title);
    }

    /**
     * Adds a message to the session and updates last activity.
     *
     * @param role    The role (user, assistant, system, context)
     * @param content The message content
     */
    public synchronized void addMessage(String role, String content) {
        if (!active) {
            log.warn("Attempted to add message to inactive session: {}", id);
            return;
        }

        Message message = new Message(role, content);
        messages.add(message);

        // Limit message history
        while (messages.size() > maxMessages) {
            messages.remove(0);
        }

        lastActivity = LocalDateTime.now();
        log.debug("Added message to session {}: {} chars from {}", id, content.length(), role);
    }

    /**
     * Gets immutable list of messages.
     *
     * @return List of messages
     */
    public List<Message> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    /**
     * Gets formatted context string for AI processing.
     *
     * @return Context string with conversation history
     */
    public String getContextForAI() {
        StringBuilder context = new StringBuilder();

        for (Message message : messages) {
            context.append(message.role)
                    .append(": ")
                    .append(message.content)
                    .append("\n");
        }

        return context.toString().trim();
    }

    /**
     * Checks if session has expired based on last activity.
     *
     * @param timeout Timeout duration
     * @return true if session is expired
     */
    public boolean isExpired(Duration timeout) {
        return LocalDateTime.now().isAfter(lastActivity.plus(timeout));
    }

    /**
     * Closes the session and marks it as inactive.
     */
    public void close() {
        this.active = false;
        log.debug("Closed session: {} [{}]", id, type);
    }

    /**
     * Clears all messages from the session.
     */
    public synchronized void clearMessages() {
        messages.clear();
        lastActivity = LocalDateTime.now();
        log.debug("Cleared messages for session: {}", id);
    }

    /**
     * Represents a single message in the session.
     */
    @Getter
    public static class Message {
        private final String role;
        private final String content;
        private final LocalDateTime timestamp;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
            this.timestamp = LocalDateTime.now();
        }

        @Override
        public String toString() {
            return String.format("[%s] %s: %s", timestamp, role, content);
        }
    }
}