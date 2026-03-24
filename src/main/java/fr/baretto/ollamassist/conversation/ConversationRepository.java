package fr.baretto.ollamassist.conversation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ConversationRepository {

    private static final String CONVERSATIONS_DIR = ".ollamassist/conversations";
    private static final String JSON_EXTENSION = ".json";

    private final Path conversationsPath;
    private final ObjectMapper objectMapper;

    public ConversationRepository(Path projectBasePath) {
        this.conversationsPath = projectBasePath.resolve(CONVERSATIONS_DIR);
        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public List<Conversation> loadAll() {
        List<Conversation> result = new ArrayList<>();
        if (!Files.exists(conversationsPath)) {
            return result;
        }
        try (var stream = Files.list(conversationsPath)) {
            stream.filter(p -> p.getFileName().toString().endsWith(JSON_EXTENSION))
                    .forEach(path -> {
                        try {
                            Conversation conversation = objectMapper.readValue(path.toFile(), Conversation.class);
                            result.add(conversation);
                        } catch (IOException e) {
                            log.warn("Failed to load conversation from {}: {}", path, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to list conversations directory: {}", e.getMessage());
        }
        return result;
    }

    public void save(Conversation conversation) {
        ensureDirectoryExists();
        Path filePath = conversationsPath.resolve(conversation.getId() + JSON_EXTENSION);
        try {
            objectMapper.writeValue(filePath.toFile(), conversation);
        } catch (IOException e) {
            log.error("Failed to save conversation {}: {}", conversation.getId(), e.getMessage());
        }
    }

    public void delete(String conversationId) {
        Path filePath = conversationsPath.resolve(conversationId + JSON_EXTENSION);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("Failed to delete conversation {}: {}", conversationId, e.getMessage());
        }
    }

    private void ensureDirectoryExists() {
        if (!Files.exists(conversationsPath)) {
            try {
                Files.createDirectories(conversationsPath);
            } catch (IOException e) {
                log.error("Failed to create conversations directory: {}", e.getMessage());
            }
        }
    }
}
