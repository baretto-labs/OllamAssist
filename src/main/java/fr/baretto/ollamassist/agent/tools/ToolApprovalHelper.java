package fr.baretto.ollamassist.agent.tools;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.events.FileApprovalNotifier;
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
     *
     * @param title    Dialog title shown in the approval panel
     * @param filePath Path shown as context (file or command)
     * @param content  Preview content (file content, diff, or command string)
     * @return {@code true} if the user approved, {@code false} if rejected
     * @throws ApprovalTimeoutException if no response within {@value TIMEOUT_MINUTES} minutes
     */
    public boolean requestApproval(String title, String filePath, String content) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

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
            return false;
        } catch (Exception e) {
            log.error("Approval request failed for: {}", filePath, e);
            return false;
        }
    }
}
