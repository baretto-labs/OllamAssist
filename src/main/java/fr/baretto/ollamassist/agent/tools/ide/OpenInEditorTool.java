package fr.baretto.ollamassist.agent.tools.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import fr.baretto.ollamassist.agent.tools.AgentTool;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Opens a file in the IDE editor.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code path} — file path, absolute or relative to project root (required)</li>
 * </ul>
 */
@Slf4j
public final class OpenInEditorTool implements AgentTool {

    private final Project project;

    public OpenInEditorTool(Project project) {
        this.project = project;
    }

    @Override
    public String toolId() {
        return "OPEN_IN_EDITOR";
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

        AtomicReference<ToolResult> result = new AtomicReference<>();
        ApplicationManager.getApplication().invokeAndWait(() -> {
            FileEditor[] editors = FileEditorManager.getInstance(project).openFile(file, true);
            if (editors.length > 0) {
                log.info("Opened in editor: {}", path);
                result.set(ToolResult.success("Opened in editor: " + path));
            } else {
                result.set(ToolResult.failure("Could not open file in editor: " + path));
            }
        });
        return result.get();
    }

    private Path resolveAbsolute(String path) {
        Path p = Paths.get(path);
        if (p.isAbsolute()) return p.normalize();
        String base = project.getBasePath();
        if (base == null) throw new IllegalStateException("Project base path is not available");
        return Paths.get(base, path).normalize();
    }
}
