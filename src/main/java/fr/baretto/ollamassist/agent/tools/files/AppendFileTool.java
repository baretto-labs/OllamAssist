package fr.baretto.ollamassist.agent.tools.files;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import fr.baretto.ollamassist.agent.tools.AgentTool;
import fr.baretto.ollamassist.agent.tools.SecretDetector;
import fr.baretto.ollamassist.agent.tools.ToolApprovalHelper;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Appends text to an existing file without touching the rest of its content.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code path}    — relative path from the project root (required)</li>
 *   <li>{@code content} — text to append (required)</li>
 *   <li>{@code newline} — when {@code true} (default), prepend a newline separator
 *                         before the appended content if the file does not already end with one</li>
 * </ul>
 *
 * <p>Requires user approval (MUTATING tier — modifies an existing file).
 * Respects the file's existing charset (same detection logic as {@link EditFileTool}).
 */
@Slf4j
public final class AppendFileTool implements AgentTool {

    private final Project project;
    private final ToolApprovalHelper approvalHelper;

    public AppendFileTool(Project project) {
        this.project = project;
        this.approvalHelper = new ToolApprovalHelper(project);
    }

    @Override
    public String toolId() {
        return "FILE_APPEND";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String path    = (String) params.get("path");
        String content = (String) params.get("content");
        boolean addNewline = !Boolean.FALSE.equals(params.get("newline"));

        if (path == null || path.isBlank()) {
            return ToolResult.failure("Parameter 'path' is required");
        }
        if (path.contains("<<")) {
            return ToolResult.failure(
                    "Parameter 'path' contains an unresolved placeholder: '" + path
                    + "'. Use <<var.NAME>> and ensure the step declaring outputVar:\"NAME\" precedes this one.");
        }
        if (content == null || content.isEmpty()) {
            return ToolResult.failure("Parameter 'content' is required and must not be empty");
        }

        String secretLabel = SecretDetector.detect(content);
        if (secretLabel != null) {
            log.warn("Blocked FILE_APPEND to '{}': possible secret in content ({})", path, secretLabel);
            return ToolResult.failure(
                    "File append blocked: the proposed content appears to contain a secret (" + secretLabel + "). "
                    + "Remove the secret before appending, or add '// ollamassist-nocheck' on the line if it is a test placeholder.");
        }

        VirtualFile file;
        try {
            Path resolved = FilePathGuard.resolveConfined(path, project);
            VirtualFile found = LocalFileSystem.getInstance().refreshAndFindFileByPath(resolved.toString());
            if (found == null || !found.exists()) {
                return ToolResult.failure("File not found: " + path + ". Use FILE_WRITE to create a new file.");
            }
            if (found.isDirectory()) {
                return ToolResult.failure("Path is a directory, not a file: " + path);
            }
            file = found;
        } catch (FilePathGuard.PathTraversalException e) {
            log.warn("Path traversal attempt blocked: {}", e.getMessage());
            return ToolResult.failure(e.getMessage());
        } catch (IllegalStateException e) {
            return ToolResult.failure(e.getMessage());
        }

        try {
            byte[] rawBytes = file.contentsToByteArray();
            Charset charset = EditFileTool.detectCharset(rawBytes);
            String original = new String(rawBytes, charset);

            // Prepend a newline separator if the file does not end with one
            String appendText = content;
            if (addNewline && !original.isEmpty() && !original.endsWith("\n")) {
                appendText = "\n" + content;
            }
            String modified = original + appendText;

            String diff = "File: " + path + "\n\n+++ APPENDING:\n" + truncate(content, 600);
            var decision = approvalHelper.requestApproval("Append to file?", path, diff);
            if (!decision.approved()) {
                return ToolResult.failure(decision.toRejectionMessage("User rejected file append: " + path));
            }

            final String finalModified = modified;
            final Charset writeCharset = charset;
            String groupId = (String) params.get("__correlationId");
            AtomicReference<ToolResult> result = new AtomicReference<>();
            WriteCommandAction.runWriteCommandAction(project, "Agent: append " + path, groupId, () -> {
                try {
                    file.setBinaryContent(finalModified.getBytes(writeCharset));
                    file.refresh(false, false);
                    log.info("File appended: {}", path);
                    result.set(ToolResult.success("Content appended to: " + path));
                } catch (IOException e) {
                    log.error("Failed to write appended file: {}", path, e);
                    result.set(ToolResult.failure("Failed to save append: " + e.getMessage()));
                }
            });
            return result.get() != null ? result.get() : ToolResult.failure("Write command action produced no result");
        } catch (IOException e) {
            log.error("Failed to read file for appending: {}", path, e);
            return ToolResult.failure("Failed to read file: " + e.getMessage());
        }
    }

    private static String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars) + "\n... [truncated]";
    }
}
