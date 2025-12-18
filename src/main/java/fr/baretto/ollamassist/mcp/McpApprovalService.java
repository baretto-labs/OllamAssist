package fr.baretto.ollamassist.mcp;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.events.McpToolApprovalNotifier;
import fr.baretto.ollamassist.setting.McpSettings;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Service responsible for managing MCP tool execution approvals.
 * Provides human-in-the-loop control over MCP tool calls.
 */
@Slf4j
@Service(Service.Level.PROJECT)
public final class McpApprovalService {

    private final Project project;
    private final Set<String> alwaysApprovedTools = ConcurrentHashMap.newKeySet();

    public McpApprovalService(Project project) {
        this.project = project;
    }

    public static McpApprovalService getInstance(Project project) {
        return project.getService(McpApprovalService.class);
    }

    /**
     * Request approval for MCP tool execution.
     * Returns a CompletableFuture that completes with true if approved, false if denied.
     *
     * @param serverName Name of the MCP server
     * @param toolName Name of the tool to execute
     * @param arguments Tool arguments
     * @return CompletableFuture<Boolean> that resolves to approval decision
     */
    public CompletableFuture<Boolean> requestApproval(
            String serverName,
            String toolName,
            Map<String, Object> arguments
    ) {
        McpSettings settings = McpSettings.getInstance(project);

        // Check if approval is globally disabled
        if (!settings.isMcpApprovalRequired()) {
            log.debug("MCP approval bypassed (disabled in settings) for tool: {}", toolName);
            return CompletableFuture.completedFuture(true);
        }

        // Check if this specific tool is in the always-approved list (session-based)
        String toolKey = serverName + ":" + toolName;
        if (alwaysApprovedTools.contains(toolKey)) {
            log.debug("Tool {} from server {} is in always-approved list", toolName, serverName);
            return CompletableFuture.completedFuture(true);
        }

        // Request approval from UI via MessageBus
        CompletableFuture<Boolean> responseFuture = new CompletableFuture<>();

        SwingUtilities.invokeLater(() -> {
            McpToolApprovalNotifier.ApprovalRequest request = McpToolApprovalNotifier.ApprovalRequest.builder()
                .serverName(serverName)
                .toolName(toolName)
                .arguments(arguments)
                .responseFuture(responseFuture)
                .build();

            project.getMessageBus()
                .syncPublisher(McpToolApprovalNotifier.TOPIC)
                .requestApproval(request);
        });

        // Apply timeout from settings
        int timeoutSeconds = settings.getMcpApprovalTimeoutSeconds();
        return responseFuture
            .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .exceptionally(throwable -> {
                if (throwable instanceof TimeoutException) {
                    log.warn("MCP tool approval timeout ({} seconds) for: {}", timeoutSeconds, toolName);
                } else {
                    log.error("Error during MCP tool approval for: {}", toolName, throwable);
                }
                return false; // Deny on timeout or error
            });
    }

    /**
     * Add a tool to the always-approved list for this session.
     * This list is not persisted and resets when the project is closed.
     */
    public void addToAlwaysApproved(String serverName, String toolName) {
        String toolKey = serverName + ":" + toolName;
        alwaysApprovedTools.add(toolKey);
        log.info("Added tool {} from server {} to always-approved list", toolName, serverName);
    }

    /**
     * Clear the always-approved tools list.
     */
    public void clearAlwaysApprovedTools() {
        alwaysApprovedTools.clear();
        log.info("Cleared always-approved tools list");
    }
}
