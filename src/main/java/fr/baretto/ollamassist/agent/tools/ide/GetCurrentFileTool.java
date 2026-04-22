package fr.baretto.ollamassist.agent.tools.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import fr.baretto.ollamassist.agent.tools.AgentTool;
import fr.baretto.ollamassist.agent.tools.ToolResult;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Returns the absolute path of the file currently open in the IDE editor.
 * Returns a failure if no file is open.
 *
 * <p>Params: none
 */
public final class GetCurrentFileTool implements AgentTool {

    private final Project project;

    public GetCurrentFileTool(Project project) {
        this.project = project;
    }

    @Override
    public String toolId() {
        return "GET_CURRENT_FILE";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        AtomicReference<ToolResult> result = new AtomicReference<>();
        ApplicationManager.getApplication().invokeAndWait(() -> {
            VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
            if (selectedFiles.length == 0) {
                result.set(ToolResult.failure("No file is currently open in the editor"));
            } else {
                result.set(ToolResult.success(selectedFiles[0].getPath()));
            }
        });
        return result.get();
    }
}
