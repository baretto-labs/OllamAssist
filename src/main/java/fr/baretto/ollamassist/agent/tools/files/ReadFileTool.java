package fr.baretto.ollamassist.agent.tools.files;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import fr.baretto.ollamassist.agent.tools.AgentTool;
import fr.baretto.ollamassist.agent.tools.SecretDetector;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Slf4j
public final class ReadFileTool implements AgentTool {

    static final int MAX_FILE_SIZE_BYTES = 512 * 1024; // 512 KB

    private final Project project;

    public ReadFileTool(Project project) {
        this.project = project;
    }

    @Override
    public String toolId() {
        return "FILE_READ";
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
        if (file.isDirectory()) {
            return ToolResult.failure("Path is a directory, not a file: " + path);
        }

        if (file.getLength() > MAX_FILE_SIZE_BYTES) {
            return ToolResult.failure("File too large to read (" + file.getLength() / 1024 + " KB). Max allowed: " + MAX_FILE_SIZE_BYTES / 1024 + " KB. Use CODE_SEARCH or FILE_FIND to locate specific sections.");
        }

        try {
            String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
            String secretLabel = SecretDetector.detect(content);
            if (secretLabel != null) {
                log.warn("Blocked FILE_READ of '{}': possible secret detected ({})", path, secretLabel);
                return ToolResult.failure("File '" + path + "' was not returned because it appears to contain a secret (" + secretLabel + "). Inspect it manually in the IDE.");
            }
            return ToolResult.success(content);
        } catch (IOException e) {
            log.error("Failed to read file: {}", path, e);
            return ToolResult.failure("Failed to read file: " + e.getMessage());
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
