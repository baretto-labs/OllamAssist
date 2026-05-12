package fr.baretto.ollamassist.events;

import com.intellij.util.messages.Topic;
import lombok.Builder;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;

public interface FileApprovalNotifier {

    Topic<FileApprovalNotifier> TOPIC = Topic.create("File Approval Request", FileApprovalNotifier.class);

    void requestApproval(ApprovalRequest request);

    /** Outcome of a human-in-the-loop approval request. */
    record ApprovalDecision(boolean approved, @org.jetbrains.annotations.Nullable String rejectionReason) {

        public static ApprovalDecision allow() {
            return new ApprovalDecision(true, null);
        }

        public static ApprovalDecision deny(@org.jetbrains.annotations.Nullable String reason) {
            String trimmed = (reason != null) ? reason.trim() : "";
            return new ApprovalDecision(false, trimmed.isEmpty() ? null : trimmed);
        }

        /**
         * Builds an error message for the LLM tool observation that includes the user's
         * rejection reason when provided, so the model can adapt its next action.
         */
        public String toRejectionMessage(String baseMessage) {
            if (rejectionReason != null && !rejectionReason.isBlank()) {
                return baseMessage + " User rejection reason: \"" + rejectionReason + "\". " +
                        "Adapt your approach based on this feedback.";
            }
            return baseMessage;
        }
    }

    @Getter
    @Builder
    class ApprovalRequest {
        private final String title;
        private final String filePath;
        private final String content;
        private final CompletableFuture<ApprovalDecision> responseFuture;
    }
}
