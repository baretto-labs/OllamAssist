package fr.baretto.ollamassist.events;

import com.intellij.util.messages.Topic;
import lombok.Builder;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;

public interface FileApprovalNotifier {

    Topic<FileApprovalNotifier> TOPIC = Topic.create("File Approval Request", FileApprovalNotifier.class);

    void requestApproval(ApprovalRequest request);

    @Getter
    @Builder
    class ApprovalRequest {
        private final String title;
        private final String filePath;
        private final String content;
        private final CompletableFuture<Boolean> responseFuture;
    }
}
