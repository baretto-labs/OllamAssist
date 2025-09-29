package fr.baretto.ollamassist.core;

import fr.baretto.ollamassist.core.model.ChatRequest;
import fr.baretto.ollamassist.core.model.CompletionRequest;
import fr.baretto.ollamassist.core.service.ModelAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ModelAssistant interface - TDD approach for AI model abstraction.
 * Tests the interface that will replace LightModelAssistant static calls.
 */
class ModelAssistantTest {

    private ModelAssistant modelAssistant;

    @BeforeEach
    void setUp() {
        // Use test implementation that will be replaced by real implementation
        modelAssistant = new TestModelAssistant();
    }

    @Test
    @DisplayName("Should handle chat request asynchronously")
    void should_handle_chat_request_asynchronously() throws Exception {
        // Given
        ChatRequest request = ChatRequest.builder()
                .message("Hello, how are you?")
                .build();

        // When
        CompletableFuture<String> future = modelAssistant.chat(request);

        // Then
        assertNotNull(future);
        String response = future.get(5, TimeUnit.SECONDS);
        assertNotNull(response);
        assertFalse(response.trim().isEmpty());
    }

    @Test
    @DisplayName("Should handle completion request asynchronously")
    void should_handle_completion_request_asynchronously() throws Exception {
        // Given
        CompletionRequest request = CompletionRequest.builder()
                .context("public class Test {")
                .fileExtension("java")
                .build();

        // When
        CompletableFuture<String> future = modelAssistant.complete(request);

        // Then
        assertNotNull(future);
        String completion = future.get(5, TimeUnit.SECONDS);
        assertNotNull(completion);
        assertFalse(completion.trim().isEmpty());
    }

    @Test
    @DisplayName("Should handle commit message generation asynchronously")
    void should_handle_commit_message_generation_asynchronously() throws Exception {
        // Given
        String gitDiff = """
                diff --git a/src/main/java/Test.java b/src/main/java/Test.java
                new file mode 100644
                index 0000000..123abc4
                --- /dev/null
                +++ b/src/main/java/Test.java
                @@ -0,0 +1,3 @@
                +public class Test {
                +    // New test class
                +}
                """;

        // When
        CompletableFuture<String> future = modelAssistant.writeCommitMessage(gitDiff);

        // Then
        assertNotNull(future);
        String commitMessage = future.get(5, TimeUnit.SECONDS);
        assertNotNull(commitMessage);
        assertFalse(commitMessage.trim().isEmpty());
    }

    @Test
    @DisplayName("Should handle simple requests without throwing")
    void should_handle_simple_requests_without_throwing() {
        // Given
        ChatRequest request = ChatRequest.builder()
                .message("Simple test message")
                .build();

        // When/Then
        assertDoesNotThrow(() -> {
            CompletableFuture<String> future = modelAssistant.chat(request);
            assertNotNull(future);
        });
    }

    @Test
    @DisplayName("Should handle null or empty requests gracefully")
    void should_handle_null_or_empty_requests_gracefully() {
        // When/Then - should not throw for null requests
        assertDoesNotThrow(() -> {
            CompletableFuture<String> future1 = modelAssistant.writeCommitMessage(null);
            CompletableFuture<String> future2 = modelAssistant.writeCommitMessage("");

            // Both should complete without throwing
            assertNotNull(future1);
            assertNotNull(future2);
        });
    }

    @Test
    @DisplayName("Should provide request statistics")
    void should_provide_request_statistics() throws Exception {
        // Given - make some requests
        modelAssistant.chat(ChatRequest.builder().message("test1").build()).get(1, TimeUnit.SECONDS);
        modelAssistant.complete(CompletionRequest.builder().context("test").fileExtension("java").build()).get(1, TimeUnit.SECONDS);
        modelAssistant.writeCommitMessage("test diff").get(1, TimeUnit.SECONDS);

        // When
        ModelAssistant.RequestStats stats = modelAssistant.getRequestStats();

        // Then
        assertNotNull(stats);
        assertEquals(3, stats.getTotalRequests());
        assertTrue(stats.getChatRequests() >= 1);
        assertTrue(stats.getCompletionRequests() >= 1);
        assertTrue(stats.getCommitMessageRequests() >= 1);
    }

    // Test implementation of ModelAssistant
    private static class TestModelAssistant implements ModelAssistant {
        private int chatRequests = 0;
        private int completionRequests = 0;
        private int commitRequests = 0;

        @Override
        public CompletableFuture<String> chat(ChatRequest request) {
            chatRequests++;
            if (request == null) {
                return CompletableFuture.completedFuture("Error: null request");
            }
            return CompletableFuture.completedFuture("Test response to: " + request.getMessage());
        }

        @Override
        public CompletableFuture<String> complete(CompletionRequest request) {
            completionRequests++;
            if (request == null) {
                return CompletableFuture.completedFuture("Error: null request");
            }
            return CompletableFuture.completedFuture("    // Completion for " + request.getContext());
        }

        @Override
        public CompletableFuture<String> writeCommitMessage(String gitDiff) {
            commitRequests++;
            if (gitDiff == null || gitDiff.trim().isEmpty()) {
                return CompletableFuture.completedFuture("Add empty changes");
            }
            return CompletableFuture.completedFuture("Add test implementation");
        }

        @Override
        public String createWebSearchQuery(String userInput) {
            if (userInput == null || userInput.trim().isEmpty()) {
                return "test query";
            }
            return "search " + userInput.trim();
        }

        @Override
        public RequestStats getRequestStats() {
            return new RequestStats() {
                @Override
                public int getTotalRequests() {
                    return chatRequests + completionRequests + commitRequests;
                }

                @Override
                public int getChatRequests() {
                    return chatRequests;
                }

                @Override
                public int getCompletionRequests() {
                    return completionRequests;
                }

                @Override
                public int getCommitMessageRequests() {
                    return commitRequests;
                }
            };
        }
    }
}