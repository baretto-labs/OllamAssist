package fr.baretto.ollamassist.agent.tools;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.events.FileApprovalNotifier;
import fr.baretto.ollamassist.events.FileApprovalNotifier.ApprovalDecision;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes a FileApprovalNotifier request and blocks the calling thread until
 * the user approves or denies it (or the timeout elapses).
 *
 * Must be called from a background thread — never from the EDT.
 */
@Slf4j
public final class ToolApprovalHelper {

    /** Fallback timeout when settings are not available. */
    private static final long DEFAULT_TIMEOUT_MINUTES = 5;

    private long timeoutMinutes() {
        try {
            return fr.baretto.ollamassist.setting.OllamaSettings.getInstance().getApprovalTimeoutMinutes();
        } catch (Exception e) {
            return DEFAULT_TIMEOUT_MINUTES;
        }
    }

    private final Project project;

    public ToolApprovalHelper(Project project) {
        this.project = project;
    }

    /**
     * Thrown when the user does not respond to an approval request within the timeout.
     * Callers (or {@link fr.baretto.ollamassist.agent.tools.ToolDispatcher}) should surface
     * this as a visible step failure rather than silently skipping the operation.
     */
    public static final class ApprovalTimeoutException extends RuntimeException {
        public ApprovalTimeoutException(String filePath) {
            super("No response to approval request for '" + filePath + "' — step aborted (approval timeout).");
        }
    }

    /**
     * Requests user approval and blocks until a decision is made.
     * Returns {@link ApprovalDecision#approved()} immediately when AUTO mode is configured.
     *
     * <p>On rejection the user may optionally provide a reason; this reason is carried in
     * {@link ApprovalDecision#rejectionReason()} so callers can include it in the LLM
     * error observation, enabling the model to adapt rather than repeat the same action.
     *
     * @param title    Dialog title shown in the approval panel
     * @param filePath Path shown as context (file or command)
     * @param content  Preview content (file content, diff, or command string)
     * @return the user's decision (approved flag + optional rejection reason)
     * @throws ApprovalTimeoutException if no response within the configured timeout
     */
    public ApprovalDecision requestApproval(String title, String filePath, String content) {
        try {
            if (fr.baretto.ollamassist.setting.OllamaSettings.getInstance().isAgentFileApprovalAuto()) {
                log.debug("AUTO approval mode — skipping confirmation for: {}", filePath);
                return ApprovalDecision.allow();
            }
        } catch (Exception e) {
            log.debug("OllamaSettings unavailable — defaulting to MANUAL approval");
        }
        CompletableFuture<ApprovalDecision> future = new CompletableFuture<>();

        FileApprovalNotifier.ApprovalRequest request = FileApprovalNotifier.ApprovalRequest.builder()
                .title(title)
                .filePath(filePath)
                .content(content)
                .responseFuture(future)
                .build();

        project.getMessageBus()
                .syncPublisher(FileApprovalNotifier.TOPIC)
                .requestApproval(request);

        long timeout = timeoutMinutes();
        try {
            return future.get(timeout, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Approval request timed out for: {}", filePath);
            throw new ApprovalTimeoutException(filePath);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Approval request interrupted for: {}", filePath);
            return ApprovalDecision.deny("Interrupted");
        } catch (Exception e) {
            log.error("Approval request failed for: {}", filePath, e);
            return ApprovalDecision.deny(null);
        }
    }
}
