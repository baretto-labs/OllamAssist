package fr.baretto.ollamassist.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.project.Project;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import fr.baretto.ollamassist.events.StopStreamingNotifier;
import fr.baretto.ollamassist.setting.McpServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * ToolExecutor wrapper that requests user approval before executing MCP tools.
 * This executor intercepts tool execution requests and shows an approval dialog to the user.
 * If approved, it delegates to the underlying executor. If denied, it returns a cancellation message.
 */
@Slf4j
public class ApprovalToolExecutor implements ToolExecutor {

    private static final long APPROVAL_TIMEOUT_SECONDS = 300; // 5 minutes
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ToolExecutor delegate;
    private final McpApprovalService approvalService;
    private final Project project;
    private final String serverName;
    private final Map<String, String> prefixToServerName;
    private final List<McpServerConfig> serverConfigs;

    /**
     * Create a new ApprovalToolExecutor with a fixed server name.
     *
     * @param delegate The underlying executor to delegate to if approved
     * @param approvalService The service to request user approval
     * @param project The IntelliJ project (for message bus access)
     * @param serverName The name of the MCP server (for logging and approval tracking)
     */
    public ApprovalToolExecutor(@NotNull ToolExecutor delegate,
                                @NotNull McpApprovalService approvalService,
                                @NotNull Project project,
                                @NotNull String serverName) {
        this.delegate = delegate;
        this.approvalService = approvalService;
        this.project = project;
        this.serverName = serverName;
        this.prefixToServerName = null;
        this.serverConfigs = null;
    }

    /**
     * Create a new ApprovalToolExecutor that determines server name from tool name prefix.
     *
     * @param delegate The underlying executor to delegate to if approved
     * @param approvalService The service to request user approval
     * @param project The IntelliJ project (for message bus access)
     * @param prefixToServerName Map of tool name prefix -> server name
     * @param serverConfigs List of server configurations to extract names from
     */
    public ApprovalToolExecutor(@NotNull ToolExecutor delegate,
                                @NotNull McpApprovalService approvalService,
                                @NotNull Project project,
                                @NotNull Map<String, String> prefixToServerName,
                                @NotNull List<McpServerConfig> serverConfigs) {
        this.delegate = delegate;
        this.approvalService = approvalService;
        this.project = project;
        this.serverName = null; // Will be determined dynamically
        this.prefixToServerName = prefixToServerName;
        this.serverConfigs = serverConfigs;
    }

    @Override
    public String execute(ToolExecutionRequest executionRequest, Object memoryId) {
        // Default implementation delegates to executeWithContext
        ToolExecutionResult result = executeWithContext(executionRequest, null);
        return result.resultText();
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest executionRequest,
                                                   InvocationContext invocationContext) {
        String toolName = executionRequest.name();
        String arguments = executionRequest.arguments();

        // Determine server name (either fixed or dynamic)
        String effectiveServerName = determineServerName(toolName);

        log.debug("Requesting approval for tool execution: {} on server: {} with arguments: {}",
                toolName, effectiveServerName, arguments);

        try {
            // Parse JSON arguments to Map
            Map<String, Object> argsMap = parseArgumentsToMap(arguments);

            // Request approval from user
            CompletableFuture<Boolean> approvalFuture = approvalService.requestApproval(effectiveServerName, toolName, argsMap);

            // Wait for approval with timeout
            Boolean approved = approvalFuture.get(APPROVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (approved != null && approved) {
                log.info("Tool execution approved by user: {}", toolName);
                // Execute the tool using the delegate
                return delegate.executeWithContext(executionRequest, invocationContext);
            } else {
                log.info("Tool execution denied by user: {}", toolName);
                // Stop LLM streaming
                stopStreaming();
                return ToolExecutionResult.builder()
                        .resultText("Tool execution was cancelled by the user")
                        .build();
            }

        } catch (TimeoutException e) {
            log.warn("Approval request timed out for tool: {}", toolName, e);
            stopStreaming();
            return ToolExecutionResult.builder()
                    .resultText("Tool execution request timed out waiting for user approval")
                    .build();

        } catch (InterruptedException e) {
            log.warn("Approval request was interrupted for tool: {}", toolName, e);
            Thread.currentThread().interrupt(); // Restore interrupt status
            stopStreaming();
            return ToolExecutionResult.builder()
                    .resultText("Tool execution request was interrupted")
                    .build();

        } catch (ExecutionException e) {
            // Check if the cause is a TimeoutException (from McpApprovalService.orTimeout())
            if (e.getCause() instanceof TimeoutException) {
                log.warn("Approval request timed out for tool: {}", toolName, e.getCause());
                stopStreaming();
                return ToolExecutionResult.builder()
                        .resultText("Tool execution request timed out waiting for user approval")
                        .build();
            }

            log.error("Error during approval request for tool: {}", toolName, e);
            stopStreaming();
            return ToolExecutionResult.builder()
                    .resultText("Error requesting approval: " + e.getMessage())
                    .isError(true)
                    .build();

        } catch (Exception e) {
            log.error("Error executing tool: {}", toolName, e);
            return ToolExecutionResult.builder()
                    .resultText("Error executing tool: " + e.getMessage())
                    .isError(true)
                    .build();
        }
    }

    /**
     * Stop the LLM streaming by publishing a stop notification.
     */
    private void stopStreaming() {
        try {
            project.getMessageBus()
                    .syncPublisher(StopStreamingNotifier.TOPIC)
                    .stopStreaming();
            log.debug("Published stop streaming notification");
        } catch (Exception e) {
            log.error("Failed to publish stop streaming notification", e);
        }
    }

    /**
     * Parse JSON arguments string into a Map.
     */
    private static Map<String, Object> parseArgumentsToMap(String arguments) {
        if (arguments == null || arguments.trim().isEmpty()) {
            return Map.of();
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> argsMap = OBJECT_MAPPER.readValue(arguments, Map.class);
            return argsMap;
        } catch (Exception e) {
            log.debug("Failed to parse arguments as JSON, using raw string", e);
            return Map.of("raw_arguments", arguments);
        }
    }

    /**
     * Parse JSON arguments into a human-readable format.
     * Converts JSON object to key-value pairs, one per line.
     *
     * @param arguments The JSON arguments string
     * @return Formatted string with one parameter per line, or empty string if null/empty
     */
    public static String parseArguments(String arguments) {
        if (arguments == null || arguments.trim().isEmpty()) {
            return "";
        }

        try {
            // Try to parse as JSON
            @SuppressWarnings("unchecked")
            Map<String, Object> argsMap = OBJECT_MAPPER.readValue(arguments, Map.class);

            // Build formatted output
            StringBuilder formatted = new StringBuilder();
            argsMap.forEach((key, value) -> {
                if (!formatted.isEmpty()) {
                    formatted.append("\n");
                }
                formatted.append(key).append(": ").append(formatValue(value));
            });

            return formatted.toString();
        } catch (Exception e) {
            log.debug("Failed to parse arguments as JSON, returning original string", e);
            return arguments;
        }
    }

    /**
     * Format a value for display (handles nested objects and arrays).
     */
    private static String formatValue(Object value) {
        if (value == null) {
            return "null";
        }

        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            StringBuilder sb = new StringBuilder();
            map.forEach((k, v) -> {
                if (!sb.isEmpty()) {
                    sb.append(", ");
                }
                sb.append(k).append(": ").append(formatValue(v));
            });
            return sb.toString();
        }

        if (value instanceof Iterable) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(formatValue(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }

        return value.toString();
    }

    /**
     * Determine the server name for a given tool name.
     * If a fixed serverName is configured, use that.
     * Otherwise, try to extract from tool name prefix.
     */
    private String determineServerName(String toolName) {
        // If we have a fixed server name, use it
        if (serverName != null) {
            return serverName;
        }

        // Try to determine from prefix
        if (prefixToServerName != null && !prefixToServerName.isEmpty()) {
            // Check if tool name has a prefix (format: "prefix_toolname")
            int underscoreIndex = toolName.indexOf('_');
            if (underscoreIndex > 0) {
                String prefix = toolName.substring(0, underscoreIndex);
                String mappedServerName = prefixToServerName.get(prefix);
                if (mappedServerName != null) {
                    log.debug("Determined server name from prefix '{}': {}", prefix, mappedServerName);
                    return mappedServerName;
                }
            }

            // If no prefix match, try the empty prefix (servers without prefix)
            String defaultServerName = prefixToServerName.get("");
            if (defaultServerName != null) {
                log.debug("Using default server name (no prefix): {}", defaultServerName);
                return defaultServerName;
            }
        }

        // Fallback: try to find any server name from configs
        if (serverConfigs != null && !serverConfigs.isEmpty()) {
            String fallbackName = serverConfigs.get(0).getName();
            log.warn("Could not determine server name for tool '{}', using fallback: {}", toolName, fallbackName);
            return fallbackName;
        }

        // Ultimate fallback
        log.error("Could not determine server name for tool: {}", toolName);
        return "UNKNOWN";
    }
}
