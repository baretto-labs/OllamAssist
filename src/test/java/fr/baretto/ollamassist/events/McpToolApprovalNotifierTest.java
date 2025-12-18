package fr.baretto.ollamassist.events;

import com.intellij.util.messages.Topic;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class for McpToolApprovalNotifier interface and its ApprovalRequest inner class.
 */
class McpToolApprovalNotifierTest {

    @Test
    void testTopicCreation() {
        // Given & When
        Topic<McpToolApprovalNotifier> topic = McpToolApprovalNotifier.TOPIC;

        // Then
        assertThat(topic).isNotNull();
        assertThat(topic.getDisplayName()).isEqualTo("MCP Tool Approval Request");
    }

    @Test
    void testApprovalRequestBuilderWithAllFields() {
        // Given
        String serverName = "test-server";
        String toolName = "test-tool";
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("param1", "value1");
        arguments.put("param2", 42);
        CompletableFuture<Boolean> responseFuture = new CompletableFuture<>();

        // When
        McpToolApprovalNotifier.ApprovalRequest request = McpToolApprovalNotifier.ApprovalRequest.builder()
            .serverName(serverName)
            .toolName(toolName)
            .arguments(arguments)
            .responseFuture(responseFuture)
            .build();

        // Then
        assertThat(request).isNotNull();
        assertThat(request.getServerName()).isEqualTo(serverName);
        assertThat(request.getToolName()).isEqualTo(toolName);
        assertThat(request.getArguments()).isEqualTo(arguments);
        assertThat(request.getResponseFuture()).isEqualTo(responseFuture);
    }

    @Test
    void testApprovalRequestWithNullArguments() {
        // Given
        String serverName = "test-server";
        String toolName = "test-tool";
        CompletableFuture<Boolean> responseFuture = new CompletableFuture<>();

        // When
        McpToolApprovalNotifier.ApprovalRequest request = McpToolApprovalNotifier.ApprovalRequest.builder()
            .serverName(serverName)
            .toolName(toolName)
            .arguments(null)
            .responseFuture(responseFuture)
            .build();

        // Then
        assertThat(request).isNotNull();
        assertThat(request.getServerName()).isEqualTo(serverName);
        assertThat(request.getToolName()).isEqualTo(toolName);
        assertThat(request.getArguments()).isNull();
        assertThat(request.getResponseFuture()).isEqualTo(responseFuture);
    }

    @Test
    void testApprovalRequestWithEmptyArgumentsMap() {
        // Given
        String serverName = "test-server";
        String toolName = "test-tool";
        Map<String, Object> emptyArguments = Collections.emptyMap();
        CompletableFuture<Boolean> responseFuture = new CompletableFuture<>();

        // When
        McpToolApprovalNotifier.ApprovalRequest request = McpToolApprovalNotifier.ApprovalRequest.builder()
            .serverName(serverName)
            .toolName(toolName)
            .arguments(emptyArguments)
            .responseFuture(responseFuture)
            .build();

        // Then
        assertThat(request).isNotNull();
        assertThat(request.getServerName()).isEqualTo(serverName);
        assertThat(request.getToolName()).isEqualTo(toolName);
        assertThat(request.getArguments()).isEmpty();
        assertThat(request.getResponseFuture()).isEqualTo(responseFuture);
    }

    @Test
    void testCompletableFutureCanBeCompletedWithTrue() throws ExecutionException, InterruptedException {
        // Given
        CompletableFuture<Boolean> responseFuture = new CompletableFuture<>();
        McpToolApprovalNotifier.ApprovalRequest request = McpToolApprovalNotifier.ApprovalRequest.builder()
            .serverName("test-server")
            .toolName("test-tool")
            .arguments(Collections.emptyMap())
            .responseFuture(responseFuture)
            .build();

        // When
        request.getResponseFuture().complete(true);

        // Then
        assertThat(request.getResponseFuture()).isCompleted();
        assertThat(request.getResponseFuture().get()).isTrue();
    }

    @Test
    void testCompletableFutureCanBeCompletedWithFalse() throws ExecutionException, InterruptedException {
        // Given
        CompletableFuture<Boolean> responseFuture = new CompletableFuture<>();
        McpToolApprovalNotifier.ApprovalRequest request = McpToolApprovalNotifier.ApprovalRequest.builder()
            .serverName("test-server")
            .toolName("test-tool")
            .arguments(Collections.emptyMap())
            .responseFuture(responseFuture)
            .build();

        // When
        request.getResponseFuture().complete(false);

        // Then
        assertThat(request.getResponseFuture()).isCompleted();
        assertThat(request.getResponseFuture().get()).isFalse();
    }

    @Test
    void testCompletableFutureCanBeCancelled() {
        // Given
        CompletableFuture<Boolean> responseFuture = new CompletableFuture<>();
        McpToolApprovalNotifier.ApprovalRequest request = McpToolApprovalNotifier.ApprovalRequest.builder()
            .serverName("test-server")
            .toolName("test-tool")
            .arguments(Collections.emptyMap())
            .responseFuture(responseFuture)
            .build();

        // When
        boolean cancelled = request.getResponseFuture().cancel(true);

        // Then
        assertThat(cancelled).isTrue();
        assertThat(request.getResponseFuture()).isCancelled();
    }

    @Test
    void testCompletableFutureWithTimeout() throws Exception {
        // Given
        CompletableFuture<Boolean> responseFuture = new CompletableFuture<>();
        McpToolApprovalNotifier.ApprovalRequest request = McpToolApprovalNotifier.ApprovalRequest.builder()
            .serverName("test-server")
            .toolName("test-tool")
            .arguments(Collections.emptyMap())
            .responseFuture(responseFuture)
            .build();

        // When - complete before timeout
        CompletableFuture<Boolean> futureWithTimeout = request.getResponseFuture()
            .orTimeout(1, TimeUnit.SECONDS);

        request.getResponseFuture().complete(true);

        // Then
        assertThat(futureWithTimeout.get()).isTrue();
    }

    @Test
    void testApprovalRequestWithComplexArguments() {
        // Given
        Map<String, Object> complexArguments = new HashMap<>();
        complexArguments.put("string", "value");
        complexArguments.put("integer", 42);
        complexArguments.put("double", 3.14);
        complexArguments.put("boolean", true);
        Map<String, String> nestedMap = new HashMap<>();
        nestedMap.put("nested", "value");
        complexArguments.put("map", nestedMap);

        // When
        McpToolApprovalNotifier.ApprovalRequest request = McpToolApprovalNotifier.ApprovalRequest.builder()
            .serverName("test-server")
            .toolName("test-tool")
            .arguments(complexArguments)
            .responseFuture(new CompletableFuture<>())
            .build();

        // Then
        assertThat(request.getArguments()).containsEntry("string", "value");
        assertThat(request.getArguments()).containsEntry("integer", 42);
        assertThat(request.getArguments()).containsEntry("double", 3.14);
        assertThat(request.getArguments()).containsEntry("boolean", true);
        assertThat(request.getArguments()).containsKey("map");
    }
}
