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

    private static final String ERROR_PREFIX = "Error: ";
    private static final String ERROR_EMPTY_PATH = "Error: File path cannot be empty";
    private static final String ERROR_BASE_PATH_UNAVAILABLE = "Error: Project base path is not available";
    private static final String ERROR_FILE_EXISTS_FORMAT = "Error: File already exists at path: %s";
    private static final String ERROR_CREATING_FILE_FORMAT = "Error creating file: %s";
    private static final String ERROR_PARENT_DIR = "Error: Could not create parent directories";
    private static final String ERROR_IO_FORMAT = "Error: %s";
    private static final String FILE_CREATED_SUCCESS_FORMAT = "File created successfully: %s";
    private static final String FILE_CREATION_TITLE = "File created";
    private static final String FILE_CREATION_SUCCESS_FORMAT = "Successfully created: %s";
    private static final String FILE_CREATION_CANCELLED = "File creation cancelled by user";
    private static final String FILE_CREATION_REQUEST_TITLE = "File Creation Request";
    private static final String FILE_CREATED_AUTO_TITLE = "File Created Automatically";
    private static final String WARNING_TOOL_CALL_DETECTED = "⚠️ Tool Call Detected (via text parsing)";
    private static final String MAX_DEPTH_ERROR_FORMAT = "Maximum directory nesting depth exceeded (%d)";

    private final Project project;

    public FileCreator(Project project) {
        this.project = project;
    }

    @Tool(name = "CreateFile", value = """
            Creates a new file in the workspace only when explicitly requested by the user.
            Do not call this tool automatically. Before calling it, the assistant must verify that:
            - The user clearly asked to create a new file OR explicitly confirmed the need for file creation.
            - The intent is to generate a full standalone file, not just provide code samples or explanations.
            
            Parameters:
            - filePath: Relative path from project root (e.g., "src/main/java/MyClass.java")
            - content: Full content of the file
            
            Returns: Success or error message
            
            Additional rules:
            - NEVER call this tool unless the user has directly expressed the need to create or write a file.
            - Prefer answering with code blocks or explanations unless file creation is truly required.
            - Always use forward slashes (/) in paths, even on Windows.
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
            return String.format(ERROR_CREATING_FILE_FORMAT, e.getMessage());
        }
    }

    private String normalizeContent(String content) {
        return content == null ? "" : content;
    }

    private String validateFileCreationRequest(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return ERROR_EMPTY_PATH;
        }

        String projectBasePath = project.getBasePath();
        if (projectBasePath == null) {
            log.error("Project base path is null");
            return ERROR_BASE_PATH_UNAVAILABLE;
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
            return String.format(ERROR_FILE_EXISTS_FORMAT, filePath);
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
            return FILE_CREATION_CANCELLED;
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
            .title(FILE_CREATED_AUTO_TITLE)
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
            .title(FILE_CREATION_REQUEST_TITLE)
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
                        return ERROR_PARENT_DIR;
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
                            FILE_CREATION_TITLE,
                            String.format(FILE_CREATION_SUCCESS_FORMAT, filePath),
                            NotificationType.INFORMATION
                        ),
                        project
                    );

                    return String.format(FILE_CREATED_SUCCESS_FORMAT, filePath);

                } catch (IOException e) {
                    log.error("IO error creating file: {}", filePath, e);
                    return String.format(ERROR_IO_FORMAT, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Error in write action: {}", filePath, e);
            return String.format(ERROR_IO_FORMAT, e.getMessage());
        }
    }

    private static final int MAX_DIRECTORY_DEPTH = 100;

    private VirtualFile createParentDirectories(Path parentPath) throws IOException {
        return createParentDirectories(parentPath, 0);
    }

    private VirtualFile createParentDirectories(Path parentPath, int depth) throws IOException {
        if (depth > MAX_DIRECTORY_DEPTH) {
            log.error("Maximum directory depth exceeded: {}", depth);
            throw new IOException(String.format(MAX_DEPTH_ERROR_FORMAT, MAX_DIRECTORY_DEPTH));
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
