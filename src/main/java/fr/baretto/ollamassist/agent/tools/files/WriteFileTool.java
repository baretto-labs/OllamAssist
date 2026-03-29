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
public final class WriteFileTool implements AgentTool {

    private final Project project;
    private final ToolApprovalHelper approvalHelper;

    public WriteFileTool(Project project) {
        this.project = project;
        this.approvalHelper = new ToolApprovalHelper(project);
    }

    @Override
    public String toolId() {
        return "FILE_WRITE";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String path = (String) params.get("path");
        String content = (String) params.get("content");

        if (path == null || path.isBlank()) {
            return ToolResult.failure("Parameter 'path' is required");
        }
        if (content == null) {
            content = "";
        }

        Path absolutePath = resolveAbsolute(path);
        VirtualFile existing = LocalFileSystem.getInstance().refreshAndFindFileByPath(absolutePath.toString());
        if (existing != null && existing.exists()) {
            return ToolResult.failure("File already exists: " + path + ". Use FILE_EDIT to modify it.");
        }

        boolean approved = approvalHelper.requestApproval(
                "Create file?",
                path,
                content
        );
        if (!approved) {
            return ToolResult.failure("User rejected file creation: " + path);
        }

        final String finalContent = content;
        try {
            return WriteAction.computeAndWait(() -> {
                try {
                    VirtualFile parentDir = getOrCreateParent(absolutePath.getParent());
                    if (parentDir == null) {
                        return ToolResult.failure("Could not create parent directory for: " + path);
                    }
                    VirtualFile newFile = parentDir.createChildData(this, absolutePath.getFileName().toString());
                    newFile.setBinaryContent(finalContent.getBytes(StandardCharsets.UTF_8));
                    newFile.refresh(false, false);
                    log.info("File created: {}", path);
                    return ToolResult.success("File created: " + path);
                } catch (IOException e) {
                    log.error("Failed to write file: {}", path, e);
                    return ToolResult.failure("Failed to write file: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("WriteAction failed for: {}", path, e);
            return ToolResult.failure("Write action failed: " + e.getMessage());
        }
    }

    private VirtualFile getOrCreateParent(Path parentPath) throws IOException {
        VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByPath(parentPath.toString());
        if (dir != null && dir.exists()) {
            return dir;
        }
        // Recurse to ensure grandparent exists
        if (parentPath.getParent() != null) {
            VirtualFile grandParent = getOrCreateParent(parentPath.getParent());
            if (grandParent != null) {
                return grandParent.createChildDirectory(this, parentPath.getFileName().toString());
            }
        }
        return null;
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
