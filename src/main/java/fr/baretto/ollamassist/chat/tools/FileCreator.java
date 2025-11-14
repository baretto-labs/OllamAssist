package fr.baretto.ollamassist.chat.tools;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import dev.langchain4j.agent.tool.Tool;
import fr.baretto.ollamassist.events.FileApprovalNotifier;
import fr.baretto.ollamassist.events.StopStreamingNotifier;
import fr.baretto.ollamassist.setting.ActionsSettings;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class FileCreator {

    private final Project project;

    public FileCreator(Project project) {
        this.project = project;
    }

    @Tool(name = "CreateFile", value = """
            Creates a new file in the workspace with the specified path and content.
            The user must approve the file creation before it is executed.

            Parameters:
            - filePath: Relative path from project root (e.g., "src/main/java/MyClass.java")
            - content: The complete content to write to the file

            Returns: Success or error message

            IMPORTANT: Always use forward slashes (/) in paths, even on Windows.
            """)
    public String createFile(String filePath, String content) {
        log.info("FileCreator.createFile called with path: {}", filePath);
        try {
            if (filePath == null || filePath.isBlank()) {
                return "Error: File path cannot be empty";
            }
            if (content == null) {
                content = "";
            }

            String projectBasePath = project.getBasePath();
            if (projectBasePath == null) {
                log.error("Project base path is null");
                return "Error: Project base path is not available";
            }

            Path absolutePath = Paths.get(projectBasePath, filePath).normalize();

            if (!absolutePath.startsWith(projectBasePath)) {
                return "Error: File path must be within the project directory";
            }

            VirtualFile existingFile = LocalFileSystem.getInstance().findFileByPath(absolutePath.toString());
            if (existingFile != null && existingFile.exists()) {
                return "Error: File already exists at path: " + filePath;
            }

            boolean autoApprove = ActionsSettings.getInstance().isAutoApproveFileCreation();

            if (autoApprove) {
                // Auto-approval mode: create file directly
                log.info("Auto-approval enabled, creating file directly");
                String result = executeFileCreation(absolutePath, content, filePath);

                // Show in chat that file was auto-created
                showAutoCreatedFileInChat(filePath, content);

                // Stop LLM streaming to avoid over-explanation
                stopLLMStreaming();

                return result;
            } else {
                // Manual approval mode: request user confirmation
                CompletableFuture<Boolean> approvalFuture = new CompletableFuture<>();
                requestApproval(filePath, content, approvalFuture);

                boolean approved;
                try {
                    approved = approvalFuture.get(5, TimeUnit.MINUTES);
                } catch (Exception e) {
                    log.warn("File creation approval timeout or interrupted for: {}", filePath, e);
                    stopLLMStreaming();
                    return "Error: File creation approval timeout or cancelled";
                }

                if (!approved) {
                    log.info("File creation rejected by user");
                    stopLLMStreaming();
                    return "File creation cancelled by user";
                }

                String result = executeFileCreation(absolutePath, content, filePath);
                stopLLMStreaming();
                return result;
            }

        } catch (Exception e) {
            log.error("Error creating file: {}", filePath, e);
            stopLLMStreaming();
            return "Error creating file: " + e.getMessage();
        }
    }

    private void stopLLMStreaming() {
        project.getMessageBus()
            .syncPublisher(StopStreamingNotifier.TOPIC)
            .stopStreaming();
    }

    private void showAutoCreatedFileInChat(String filePath, String content) {
        FileApprovalNotifier.ApprovalRequest request = FileApprovalNotifier.ApprovalRequest.builder()
            .title("File Created Automatically")
            .filePath(filePath)
            .content(content)
            .responseFuture(CompletableFuture.completedFuture(true))
            .build();

        project.getMessageBus()
            .syncPublisher(FileApprovalNotifier.TOPIC)
            .requestApproval(request);
    }

    private void requestApproval(String filePath, String content, CompletableFuture<Boolean> approvalFuture) {
        // Publish approval request to chat UI via MessageBus
        FileApprovalNotifier.ApprovalRequest request = FileApprovalNotifier.ApprovalRequest.builder()
            .title("File Creation Request")
            .filePath(filePath)
            .content(content)
            .responseFuture(approvalFuture)
            .build();

        project.getMessageBus()
            .syncPublisher(FileApprovalNotifier.TOPIC)
            .requestApproval(request);
    }

    private String executeFileCreation(Path absolutePath, String content, String filePath) {
        try {
            return WriteAction.computeAndWait(() -> {
                try {
                    // Create parent directories if needed
                    VirtualFile parentDir = createParentDirectories(absolutePath.getParent());
                    if (parentDir == null) {
                        return "Error: Could not create parent directories";
                    }

                    // Create file
                    VirtualFile newFile = parentDir.createChildData(this, absolutePath.getFileName().toString());
                    newFile.setBinaryContent(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));

                    // Refresh file system
                    newFile.refresh(false, false);

                    log.info("File created successfully: {}", filePath);

                    // Success notification
                    Notifications.Bus.notify(
                        new Notification(
                            "OllamAssist",
                            "File Created",
                            "Successfully created: " + filePath,
                            NotificationType.INFORMATION
                        ),
                        project
                    );

                    return "File created successfully: " + filePath;

                } catch (IOException e) {
                    log.error("IO error creating file: {}", filePath, e);
                    return "Error: " + e.getMessage();
                }
            });
        } catch (Exception e) {
            log.error("Error in write action: {}", filePath, e);
            return "Error: " + e.getMessage();
        }
    }

    private static final int MAX_DIRECTORY_DEPTH = 100;

    private VirtualFile createParentDirectories(Path parentPath) throws IOException {
        return createParentDirectories(parentPath, 0);
    }

    private VirtualFile createParentDirectories(Path parentPath, int depth) throws IOException {
        if (depth > MAX_DIRECTORY_DEPTH) {
            log.error("Maximum directory depth exceeded: {}", depth);
            throw new IOException("Maximum directory nesting depth exceeded (" + MAX_DIRECTORY_DEPTH + ")");
        }

        VirtualFile parent = LocalFileSystem.getInstance().findFileByPath(parentPath.toString());

        if (parent != null && parent.exists()) {
            return parent;
        }

        // Create parent directories recursively
        if (parentPath.getParent() != null) {
            VirtualFile grandParent = createParentDirectories(parentPath.getParent(), depth + 1);
            if (grandParent != null) {
                return grandParent.createChildDirectory(this, parentPath.getFileName().toString());
            }
        }

        return null;
    }
}
