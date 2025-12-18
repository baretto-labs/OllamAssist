package fr.baretto.ollamassist.events;

import com.intellij.util.messages.Topic;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Event notifier for MCP tool approval requests.
 * Follows the same pattern as FileApprovalNotifier for consistency.
 */
public interface McpToolApprovalNotifier {

    Topic<McpToolApprovalNotifier> TOPIC = Topic.create(
        "MCP Tool Approval Request",
        McpToolApprovalNotifier.class
    );

    void requestApproval(ApprovalRequest request);

    @Getter
    @Builder
    class ApprovalRequest {
        private final String serverName;
        private final String toolName;
        private final Map<String, Object> arguments;
        private final CompletableFuture<Boolean> responseFuture;
    }
}
