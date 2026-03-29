package fr.baretto.ollamassist.agent.tools.files;

import com.intellij.openapi.application.WriteAction;
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
import java.nio.file.Paths;
import java.util.Map;

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

        if (path == null || path.isBlank()) {
            return ToolResult.failure("Parameter 'path' is required");
        }
        if (search == null) {
            return ToolResult.failure("Parameter 'search' is required");
        }
        if (replace == null) {
            return ToolResult.failure("Parameter 'replace' is required");
        }

        Path absolutePath = resolveAbsolute(path);
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(absolutePath.toString());

        if (file == null || !file.exists()) {
            return ToolResult.failure("File not found: " + path);
        }

        try {
            String original = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
            if (!original.contains(search)) {
                return ToolResult.failure("Search string not found in file: " + path);
            }

            String modified = original.replace(search, replace);
            final String finalModified = modified;

            String diff = buildDiff(path, search, replace, countOccurrences(original, search));
            boolean approved = approvalHelper.requestApproval(
                    "Edit file?",
                    path,
                    diff
            );
            if (!approved) {
                return ToolResult.failure("User rejected file edit: " + path);
            }

            return WriteAction.computeAndWait(() -> {
                try {
                    file.setBinaryContent(finalModified.getBytes(StandardCharsets.UTF_8));
                    file.refresh(false, false);
                    log.info("File edited: {}", path);
                    return ToolResult.success("File edited: " + path);
                } catch (IOException e) {
                    log.error("Failed to write edited file: {}", path, e);
                    return ToolResult.failure("Failed to save edit: " + e.getMessage());
                }
            });
        } catch (IOException e) {
            log.error("Failed to read file for editing: {}", path, e);
            return ToolResult.failure("Failed to read file: " + e.getMessage());
        } catch (Exception e) {
            log.error("WriteAction failed for: {}", path, e);
            return ToolResult.failure("Write action failed: " + e.getMessage());
        }
    }

    private static String buildDiff(String path, String search, String replace, int occurrences) {
        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(path).append("\n");
        if (occurrences > 1) {
            sb.append("⚠ WARNING: ").append(occurrences).append(" occurrences will ALL be replaced\n");
        }
        sb.append("\n--- BEFORE:\n");
        appendTruncated(sb, search, 800);
        sb.append("\n+++ AFTER:\n");
        appendTruncated(sb, replace, 800);
        return sb.toString();
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

    private Path resolveAbsolute(String path) {
        Path p = Paths.get(path);
        if (p.isAbsolute()) {
            return p.normalize();
        }
        String base = project.getBasePath();
        if (base == null) {
            throw new IllegalStateException("Project base path is not available");
        }
        return Paths.get(base, path).normalize();
    }
}
