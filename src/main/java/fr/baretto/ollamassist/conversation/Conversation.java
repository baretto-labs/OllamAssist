package fr.baretto.ollamassist.conversation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Conversation {

    private static final String DEFAULT_TITLE = "New conversation";
    private static final int MAX_TITLE_LENGTH = 60;

    private final String id;
    private String title;
    private final long createdAt;
    private long updatedAt;
    private final List<ConversationMessage> messages;

    public Conversation() {
        this.id = UUID.randomUUID().toString();
        this.title = DEFAULT_TITLE;
        this.createdAt = Instant.now().toEpochMilli();
        this.updatedAt = this.createdAt;
        this.messages = new ArrayList<>();
    }

    @JsonCreator
    public Conversation(
            @JsonProperty("id") String id,
            @JsonProperty("title") String title,
            @JsonProperty("createdAt") long createdAt,
            @JsonProperty("updatedAt") long updatedAt,
            @JsonProperty("messages") List<ConversationMessage> messages) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
    }

    public synchronized void addMessage(ConversationMessage message) {
        if (messages.isEmpty()
                && message.getRole() == ConversationMessage.Role.USER
                && DEFAULT_TITLE.equals(title)) {
            generateTitle(message.getContent());
        }
        messages.add(message);
        updatedAt = Instant.now().toEpochMilli();
    }

    private void generateTitle(String content) {
        String trimmed = content.trim();
        title = trimmed.length() > MAX_TITLE_LENGTH
                ? trimmed.substring(0, MAX_TITLE_LENGTH).trim() + "..."
                : trimmed;
    }

    public synchronized List<ConversationMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return title;
    }
}
