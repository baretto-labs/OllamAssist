package fr.baretto.ollamassist.conversation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.time.Instant;

@Value
public class ConversationMessage {

    public enum Role {USER, ASSISTANT}

    Role role;
    String content;
    long timestamp;

    @JsonCreator
    public ConversationMessage(
            @JsonProperty("role") Role role,
            @JsonProperty("content") String content,
            @JsonProperty("timestamp") long timestamp) {
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
    }

    public static ConversationMessage user(String content) {
        return new ConversationMessage(Role.USER, content, Instant.now().toEpochMilli());
    }

    public static ConversationMessage assistant(String content) {
        return new ConversationMessage(Role.ASSISTANT, content, Instant.now().toEpochMilli());
    }
}
