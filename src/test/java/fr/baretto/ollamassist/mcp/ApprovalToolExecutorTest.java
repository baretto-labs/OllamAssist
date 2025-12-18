package fr.baretto.ollamassist.mcp;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import fr.baretto.ollamassist.events.StopStreamingNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ApprovalToolExecutor.
 * Tests the approval flow, delegation, timeout handling, and error scenarios.
 */
class ApprovalToolExecutorTest {

    @Mock
    private ToolExecutor delegate;

    @Mock
    private McpApprovalService approvalService;

    @Mock
    private Project project;

    @Mock
    private MessageBus messageBus;

    @Mock
    private StopStreamingNotifier stopStreamingPublisher;

    @Mock
    private ToolExecutionRequest executionRequest;

    @Mock
    private InvocationContext invocationContext;

    private ApprovalToolExecutor approvalToolExecutor;

    private static final String SERVER_NAME = "test-mcp-server";
    private static final String TOOL_NAME = "test-tool";
    private static final String ARGUMENTS_JSON = "{\"path\":\"/tmp/test.txt\",\"content\":\"Hello World\"}";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup project message bus
        when(project.getMessageBus()).thenReturn(messageBus);
        when(messageBus.syncPublisher(StopStreamingNotifier.TOPIC)).thenReturn(stopStreamingPublisher);

        // Setup execution request
        when(executionRequest.name()).thenReturn(TOOL_NAME);
        when(executionRequest.arguments()).thenReturn(ARGUMENTS_JSON);

        // Create executor under test
        approvalToolExecutor = new ApprovalToolExecutor(
                delegate,
                approvalService,
                project,
                SERVER_NAME
        );
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithContext_whenApproved_shouldDelegateExecution() {
        // Given: User approves the tool execution
        ToolExecutionResult delegateResult = ToolExecutionResult.builder()
                .resultText("File written successfully")
                .build();

        when(approvalService.requestApproval(eq(SERVER_NAME), eq(TOOL_NAME), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(true));
        when(delegate.executeWithContext(executionRequest, invocationContext))
                .thenReturn(delegateResult);

        // When: Execute with context
        ToolExecutionResult result = approvalToolExecutor.executeWithContext(executionRequest, invocationContext);

        // Then: Should delegate to underlying executor
        assertThat(result).isNotNull();
        assertThat(result.resultText()).isEqualTo("File written successfully");

        // Verify approval was requested
        ArgumentCaptor<Map<String, Object>> argsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(approvalService).requestApproval(eq(SERVER_NAME), eq(TOOL_NAME), argsCaptor.capture());

        Map<String, Object> capturedArgs = argsCaptor.getValue();
        assertThat(capturedArgs)
                .containsEntry("path", "/tmp/test.txt")
                .containsEntry("content", "Hello World");

        // Verify delegate was called
        verify(delegate).executeWithContext(executionRequest, invocationContext);

        // Verify stop streaming was NOT called (only called on denial/error)
        verify(stopStreamingPublisher, never()).stopStreaming();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithContext_whenDenied_shouldReturnCancellationMessage() {
        // Given: User denies the tool execution
        when(approvalService.requestApproval(eq(SERVER_NAME), eq(TOOL_NAME), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(false));

        // When: Execute with context
        ToolExecutionResult result = approvalToolExecutor.executeWithContext(executionRequest, invocationContext);

        // Then: Should return cancellation message
        assertThat(result).isNotNull();
        assertThat(result.resultText()).isEqualTo("Tool execution was cancelled by the user");

        // Verify delegate was NOT called
        verify(delegate, never()).executeWithContext(any(), any());

        // Verify stop streaming was called
        verify(stopStreamingPublisher).stopStreaming();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithContext_whenTimeout_shouldReturnTimeoutMessage() {
        // Given: Approval request times out
        CompletableFuture<Boolean> timeoutFuture = new CompletableFuture<>();
        timeoutFuture.completeExceptionally(new TimeoutException("Approval timeout"));

        when(approvalService.requestApproval(eq(SERVER_NAME), eq(TOOL_NAME), anyMap()))
                .thenReturn(timeoutFuture);

        // When: Execute with context
        ToolExecutionResult result = approvalToolExecutor.executeWithContext(executionRequest, invocationContext);

        // Then: Should return timeout message
        assertThat(result).isNotNull();
        assertThat(result.resultText()).contains("timed out");

        // Verify delegate was NOT called
        verify(delegate, never()).executeWithContext(any(), any());

        // Verify stop streaming was called
        verify(stopStreamingPublisher).stopStreaming();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithContext_whenInterrupted_shouldReturnInterruptedMessage() {
        // Given: Approval request is interrupted
        CompletableFuture<Boolean> interruptedFuture = new CompletableFuture<>();
        interruptedFuture.completeExceptionally(new InterruptedException("Thread interrupted"));

        when(approvalService.requestApproval(eq(SERVER_NAME), eq(TOOL_NAME), anyMap()))
                .thenReturn(interruptedFuture);

        // When: Execute with context
        ToolExecutionResult result = approvalToolExecutor.executeWithContext(executionRequest, invocationContext);

        // Then: Should return interrupted message
        assertThat(result).isNotNull();
        assertThat(result.resultText()).contains("interrupted");

        // Verify delegate was NOT called
        verify(delegate, never()).executeWithContext(any(), any());

        // Verify stop streaming was called
        verify(stopStreamingPublisher).stopStreaming();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithContext_whenExecutionException_shouldReturnErrorMessage() {
        // Given: Approval service throws ExecutionException
        CompletableFuture<Boolean> errorFuture = new CompletableFuture<>();
        errorFuture.completeExceptionally(new ExecutionException("Internal error", new RuntimeException("Root cause")));

        when(approvalService.requestApproval(eq(SERVER_NAME), eq(TOOL_NAME), anyMap()))
                .thenReturn(errorFuture);

        // When: Execute with context
        ToolExecutionResult result = approvalToolExecutor.executeWithContext(executionRequest, invocationContext);

        // Then: Should return error message
        assertThat(result).isNotNull();
        assertThat(result.resultText()).contains("Error requesting approval");

        // Verify delegate was NOT called
        verify(delegate, never()).executeWithContext(any(), any());

        // Verify stop streaming was called
        verify(stopStreamingPublisher).stopStreaming();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithContext_withEmptyArguments_shouldHandleGracefully() {
        // Given: Empty arguments
        when(executionRequest.arguments()).thenReturn("");
        when(approvalService.requestApproval(eq(SERVER_NAME), eq(TOOL_NAME), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(true));

        ToolExecutionResult delegateResult = ToolExecutionResult.builder()
                .resultText("Success")
                .build();
        when(delegate.executeWithContext(executionRequest, invocationContext))
                .thenReturn(delegateResult);

        // When: Execute with context
        ToolExecutionResult result = approvalToolExecutor.executeWithContext(executionRequest, invocationContext);

        // Then: Should handle empty args gracefully
        assertThat(result).isNotNull();
        assertThat(result.resultText()).isEqualTo("Success");

        // Verify approval was requested with empty map
        ArgumentCaptor<Map<String, Object>> argsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(approvalService).requestApproval(eq(SERVER_NAME), eq(TOOL_NAME), argsCaptor.capture());
        assertThat(argsCaptor.getValue()).isEmpty();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithContext_withNullArguments_shouldHandleGracefully() {
        // Given: Null arguments
        when(executionRequest.arguments()).thenReturn(null);
        when(approvalService.requestApproval(eq(SERVER_NAME), eq(TOOL_NAME), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(true));

        ToolExecutionResult delegateResult = ToolExecutionResult.builder()
                .resultText("Success")
                .build();
        when(delegate.executeWithContext(executionRequest, invocationContext))
                .thenReturn(delegateResult);

        // When: Execute with context
        ToolExecutionResult result = approvalToolExecutor.executeWithContext(executionRequest, invocationContext);

        // Then: Should handle null args gracefully
        assertThat(result).isNotNull();
        assertThat(result.resultText()).isEqualTo("Success");

        // Verify approval was requested with empty map
        ArgumentCaptor<Map<String, Object>> argsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(approvalService).requestApproval(eq(SERVER_NAME), eq(TOOL_NAME), argsCaptor.capture());
        assertThat(argsCaptor.getValue()).isEmpty();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteWithContext_withInvalidJSON_shouldHandleGracefully() {
        // Given: Invalid JSON arguments
        when(executionRequest.arguments()).thenReturn("{invalid json}");
        when(approvalService.requestApproval(eq(SERVER_NAME), eq(TOOL_NAME), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(true));

        ToolExecutionResult delegateResult = ToolExecutionResult.builder()
                .resultText("Success")
                .build();
        when(delegate.executeWithContext(executionRequest, invocationContext))
                .thenReturn(delegateResult);

        // When: Execute with context
        ToolExecutionResult result = approvalToolExecutor.executeWithContext(executionRequest, invocationContext);

        // Then: Should handle invalid JSON gracefully
        assertThat(result).isNotNull();
        assertThat(result.resultText()).isEqualTo("Success");

        // Verify approval was requested with raw_arguments fallback
        ArgumentCaptor<Map<String, Object>> argsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(approvalService).requestApproval(eq(SERVER_NAME), eq(TOOL_NAME), argsCaptor.capture());
        assertThat(argsCaptor.getValue()).containsEntry("raw_arguments", "{invalid json}");
    }

    @Test
    void testExecute_shouldDelegateToExecuteWithContext() {
        // Given: Approved execution
        when(approvalService.requestApproval(eq(SERVER_NAME), eq(TOOL_NAME), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(true));

        ToolExecutionResult delegateResult = ToolExecutionResult.builder()
                .resultText("Execution result")
                .build();
        when(delegate.executeWithContext(executionRequest, null))
                .thenReturn(delegateResult);

        // When: Call execute() method
        String result = approvalToolExecutor.execute(executionRequest, null);

        // Then: Should return result text
        assertThat(result).isEqualTo("Execution result");
    }

    @Test
    void testParseArguments_withValidJSON_shouldFormatReadably() {
        // Given: Valid JSON with multiple fields
        String json = "{\"path\":\"/tmp/file.txt\",\"content\":\"Hello\",\"mode\":\"write\"}";

        // When: Parse arguments
        String formatted = ApprovalToolExecutor.parseArguments(json);

        // Then: Should format as key-value pairs
        assertThat(formatted)
                .contains("path: /tmp/file.txt")
                .contains("content: Hello")
                .contains("mode: write");
    }

    @Test
    void testParseArguments_withEmptyJSON_shouldReturnEmptyString() {
        // When: Parse empty JSON
        String formatted = ApprovalToolExecutor.parseArguments("{}");

        // Then: Should return empty string
        assertThat(formatted).isEmpty();
    }

    @Test
    void testParseArguments_withNull_shouldReturnEmptyString() {
        // When: Parse null
        String formatted = ApprovalToolExecutor.parseArguments(null);

        // Then: Should return empty string
        assertThat(formatted).isEmpty();
    }

    @Test
    void testParseArguments_withInvalidJSON_shouldReturnOriginal() {
        // Given: Invalid JSON
        String invalidJson = "not a json";

        // When: Parse invalid JSON
        String formatted = ApprovalToolExecutor.parseArguments(invalidJson);

        // Then: Should return original string
        assertThat(formatted).isEqualTo(invalidJson);
    }

    @Test
    void testParseArguments_withNestedObjects_shouldFormatRecursively() {
        // Given: JSON with nested object
        String json = "{\"config\":{\"host\":\"localhost\",\"port\":8080}}";

        // When: Parse arguments
        String formatted = ApprovalToolExecutor.parseArguments(json);

        // Then: Should format nested structure
        assertThat(formatted)
                .contains("config:")
                .contains("host: localhost")
                .contains("port: 8080");
    }

    @Test
    void testParseArguments_withArrays_shouldFormatArray() {
        // Given: JSON with array
        String json = "{\"files\":[\"file1.txt\",\"file2.txt\"]}";

        // When: Parse arguments
        String formatted = ApprovalToolExecutor.parseArguments(json);

        // Then: Should format array
        assertThat(formatted)
                .contains("files:")
                .contains("[")
                .contains("file1.txt")
                .contains("file2.txt");
    }
}
