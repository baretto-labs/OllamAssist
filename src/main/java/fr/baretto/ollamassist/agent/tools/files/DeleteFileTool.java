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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

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

        Path absolutePath = resolveAbsolute(path);
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

        try {
            return WriteAction.computeAndWait(() -> {
                try {
                    file.delete(this);
                    log.info("File deleted: {}", path);
                    return ToolResult.success("File deleted: " + path);
                } catch (IOException e) {
                    log.error("Failed to delete file: {}", path, e);
                    return ToolResult.failure("Failed to delete file: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("WriteAction failed for delete: {}", path, e);
            return ToolResult.failure("Delete action failed: " + e.getMessage());
        }
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
