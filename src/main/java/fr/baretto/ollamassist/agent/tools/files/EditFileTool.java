package fr.baretto.ollamassist.agent.tools.files;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import fr.baretto.ollamassist.agent.tools.AgentTool;
import fr.baretto.ollamassist.agent.tools.ToolApprovalHelper;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public final class EditFileTool implements AgentTool {

    private final Project project;
    private final ToolApprovalHelper approvalHelper;

    public EditFileTool(Project project) {
        this.project = project;
        this.approvalHelper = new ToolApprovalHelper(project);
    }

    @Override
    public String toolId() {
        return "FILE_EDIT";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String path = (String) params.get("path");
        String search = (String) params.get("search");
        String replace = (String) params.get("replace");
        boolean replaceAll = Boolean.TRUE.equals(params.get("replaceAll"));

        if (path == null || path.isBlank()) {
            return ToolResult.failure("Parameter 'path' is required");
        }
        if (search == null) {
            return ToolResult.failure("Parameter 'search' is required");
        }
        if (replace == null) {
            return ToolResult.failure("Parameter 'replace' is required");
        }

        Path absolutePath;
        try {
            absolutePath = FilePathGuard.resolveConfined(path, project);
        } catch (FilePathGuard.PathTraversalException e) {
            log.warn("Path traversal attempt blocked: {}", e.getMessage());
            return ToolResult.failure(e.getMessage());
        } catch (IllegalStateException e) {
            return ToolResult.failure(e.getMessage());
        }
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(absolutePath.toString());

        if (file == null || !file.exists()) {
            return ToolResult.failure("File not found: " + path);
        }

        try {
            byte[] rawBytes = file.contentsToByteArray();
            // Detect encoding so edits on Latin-1/ISO-8859-1 files don't corrupt content (Q-1).
            Charset charset = detectCharset(rawBytes);
            String original = new String(rawBytes, charset);
            if (!original.contains(search)) {
                return ToolResult.failure("Search string not found in file: " + path);
            }

            int occurrences = countOccurrences(original, search);
            String modified = replaceAll
                    ? original.replace(search, replace)
                    : replaceFirstOccurrence(original, search, replace);
            final String finalModified = modified;

            String diff = buildDiff(path, search, replace, occurrences, replaceAll);
            boolean approved = approvalHelper.requestApproval(
                    "Edit file?",
                    path,
                    diff
            );
            if (!approved) {
                return ToolResult.failure("User rejected file edit: " + path);
            }

            final Charset writeCharset = charset;
            String groupId = (String) params.get("__correlationId");
            AtomicReference<ToolResult> result = new AtomicReference<>();
            WriteCommandAction.runWriteCommandAction(project, "Agent: edit " + path, groupId, () -> {
                try {
                    file.setBinaryContent(finalModified.getBytes(writeCharset));
                    file.refresh(false, false);
                    log.info("File edited: {}", path);
                    result.set(ToolResult.success("File edited: " + path));
                } catch (IOException e) {
                    log.error("Failed to write edited file: {}", path, e);
                    result.set(ToolResult.failure("Failed to save edit: " + e.getMessage()));
                }
            });
            return result.get() != null ? result.get() : ToolResult.failure("Write command action produced no result");
        } catch (IOException e) {
            log.error("Failed to read file for editing: {}", path, e);
            return ToolResult.failure("Failed to read file: " + e.getMessage());
        } catch (Exception e) {
            log.error("WriteCommandAction failed for: {}", path, e);
            return ToolResult.failure("Write action failed: " + e.getMessage());
        }
    }

    private static String buildDiff(String path, String search, String replace, int occurrences, boolean replaceAll) {
        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(path).append("\n");
        if (occurrences > 1) {
            if (replaceAll) {
                sb.append("WARNING: ").append(occurrences).append(" occurrences will ALL be replaced\n");
            } else {
                sb.append("Note: ").append(occurrences)
                        .append(" occurrences found — only the FIRST will be replaced (replaceAll=false)\n");
            }
        }
        sb.append("\n--- BEFORE:\n");
        appendTruncated(sb, search, 800);
        sb.append("\n+++ AFTER:\n");
        appendTruncated(sb, replace, 800);
        return sb.toString();
    }

    private static String replaceFirstOccurrence(String original, String search, String replace) {
        int idx = original.indexOf(search);
        if (idx < 0) return original;
        return original.substring(0, idx) + replace + original.substring(idx + search.length());
    }

    private static void appendTruncated(StringBuilder sb, String text, int maxChars) {
        if (text.length() <= maxChars) {
            sb.append(text);
        } else {
            sb.append(text, 0, maxChars / 2)
                    .append("\n... [").append(text.length() - maxChars).append(" chars truncated] ...\n")
                    .append(text, text.length() - maxChars / 2, text.length());
        }
    }

    private static int countOccurrences(String text, String search) {
        if (search.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(search, idx)) != -1) {
            count++;
            idx += search.length();
        }
        return count;
    }

    /**
     * Returns the charset to use for reading and writing {@code bytes}.
     * Tries UTF-8 in strict mode first; falls back to ISO-8859-1 if the bytes
     * are not valid UTF-8, preserving the original encoding on write-back (Q-1).
     */
    static Charset detectCharset(byte[] bytes) {
        java.nio.charset.CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
        try {
            utf8.decode(java.nio.ByteBuffer.wrap(bytes));
            return StandardCharsets.UTF_8;
        } catch (java.nio.charset.CharacterCodingException e) {
            return StandardCharsets.ISO_8859_1;
        }
    }
}
