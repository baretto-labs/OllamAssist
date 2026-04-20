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
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public final class DeleteFileTool implements AgentTool {

    private final Project project;
    private final ToolApprovalHelper approvalHelper;

    public DeleteFileTool(Project project) {
        this.project = project;
        this.approvalHelper = new ToolApprovalHelper(project);
    }

    @Override
    public String toolId() {
        return "FILE_DELETE";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String path = (String) params.get("path");
        if (path == null || path.isBlank()) {
            return ToolResult.failure("Parameter 'path' is required");
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

        boolean approved = approvalHelper.requestApproval(
                "Delete file?",
                path,
                "This action will permanently delete: " + path
        );
        if (!approved) {
            return ToolResult.failure("User rejected file deletion: " + path);
        }

        String groupId = (String) params.get("__correlationId");
        AtomicReference<ToolResult> result = new AtomicReference<>();
        try {
            WriteCommandAction.runWriteCommandAction(project, "Agent: delete " + path, groupId, () -> {
                try {
                    file.delete(this);
                    log.info("File deleted: {}", path);
                    result.set(ToolResult.success("File deleted: " + path));
                } catch (IOException e) {
                    log.error("Failed to delete file: {}", path, e);
                    result.set(ToolResult.failure("Failed to delete file: " + e.getMessage()));
                }
            });
        } catch (Exception e) {
            log.error("WriteCommandAction failed for delete: {}", path, e);
            return ToolResult.failure("Delete action failed: " + e.getMessage());
        }
        return result.get() != null ? result.get() : ToolResult.failure("Write command action produced no result");
    }

}
