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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
            String normalizedContent = normalizeContent(content);
            String validationError = validateFileCreationRequest(filePath);
            if (validationError != null) {
                return validationError;
            }

            Path absolutePath = resolveAndValidatePath(filePath);

            String fileExistsError = checkFileNotExists(absolutePath, filePath);
            if (fileExistsError != null) {
                return fileExistsError;
            }

            return handleFileCreationWithApproval(filePath, normalizedContent, absolutePath);

        } catch (Exception e) {
            log.error("Error creating file: {}", filePath, e);
            stopLLMStreaming();
            return "Error creating file: " + e.getMessage();
        }
    }

    private String normalizeContent(String content) {
        return content == null ? "" : content;
    }

    private String validateFileCreationRequest(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "Error: File path cannot be empty";
        }

        String projectBasePath = project.getBasePath();
        if (projectBasePath == null) {
            log.error("Project base path is null");
            return "Error: Project base path is not available";
        }

        return null;
    }

    private Path resolveAndValidatePath(String filePath) {
        String projectBasePath = project.getBasePath();
        if (projectBasePath == null) {
            throw new IllegalStateException("Project base path is not available");
        }

        Path absolutePath = Paths.get(projectBasePath, filePath).normalize();

        if (!absolutePath.startsWith(projectBasePath)) {
            throw new IllegalArgumentException("File path must be within the project directory");
        }

        return absolutePath;
    }

    private String checkFileNotExists(Path absolutePath, String filePath) {
        VirtualFile existingFile = LocalFileSystem.getInstance().findFileByPath(absolutePath.toString());
        if (existingFile != null && existingFile.exists()) {
            return "Error: File already exists at path: " + filePath;
        }
        return null;
    }

    private String handleFileCreationWithApproval(String filePath, String content, Path absolutePath) {
        boolean autoApprove = ActionsSettings.getInstance().isAutoApproveFileCreation();

        if (autoApprove) {
            return handleAutoApprovalMode(filePath, content, absolutePath);
        } else {
            return handleManualApprovalMode(filePath, content, absolutePath);
        }
    }

    private String handleAutoApprovalMode(String filePath, String content, Path absolutePath) {
        log.info("Auto-approval enabled, creating file directly");
        String result = executeFileCreation(absolutePath, content, filePath);
        showAutoCreatedFileInChat(filePath, content);
        stopLLMStreaming();
        return result;
    }

    private String handleManualApprovalMode(String filePath, String content, Path absolutePath) {
        CompletableFuture<Boolean> approvalFuture = new CompletableFuture<>();
        requestApproval(filePath, content, approvalFuture);

        boolean approved = waitForApproval(approvalFuture, filePath);
        if (!approved) {
            log.info("File creation rejected by user");
            stopLLMStreaming();
            return "File creation cancelled by user";
        }

        String result = executeFileCreation(absolutePath, content, filePath);
        stopLLMStreaming();
        return result;
    }

    private boolean waitForApproval(CompletableFuture<Boolean> approvalFuture, String filePath) {
        try {
            return approvalFuture.get(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("File creation approval interrupted for: {}", filePath, e);
            stopLLMStreaming();
            throw new IllegalStateException("File creation approval interrupted", e);
        } catch (ExecutionException e) {
            log.warn("File creation approval execution error for: {}", filePath, e);
            stopLLMStreaming();
            throw new IllegalStateException("File creation approval execution error", e);
        } catch (TimeoutException e) {
            log.warn("File creation approval timeout for: {}", filePath, e);
            stopLLMStreaming();
            throw new IllegalStateException("File creation approval timeout", e);
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
                            "File created",
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
