package fr.baretto.ollamassist.agent.tools;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.agent.tools.files.*;
import fr.baretto.ollamassist.agent.tools.git.GitDiffTool;
import fr.baretto.ollamassist.agent.tools.git.GitStatusTool;
import fr.baretto.ollamassist.agent.tools.ide.GetCurrentFileTool;
import fr.baretto.ollamassist.agent.tools.ide.OpenInEditorTool;
import fr.baretto.ollamassist.agent.tools.navigation.SearchCodeTool;
import fr.baretto.ollamassist.agent.tools.rag.SearchKnowledgeBaseTool;
import fr.baretto.ollamassist.agent.tools.terminal.RunCommandTool;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class ToolRegistry {

    private final Map<String, AgentTool> tools = new HashMap<>();

    public ToolRegistry(Project project) {
        register(new ReadFileTool(project));
        register(new WriteFileTool(project));
        register(new EditFileTool(project));
        register(new DeleteFileTool(project));
        register(new FindFilesTool(project));
        register(new SearchCodeTool(project));
        register(new RunCommandTool(project));
        register(new GitStatusTool(project));
        register(new GitDiffTool(project));
        register(new OpenInEditorTool(project));
        register(new GetCurrentFileTool(project));
        register(new SearchKnowledgeBaseTool(project));
    }

    private void register(AgentTool tool) {
        tools.put(tool.toolId(), tool);
    }

    @Nullable
    public AgentTool get(String toolId) {
        return tools.get(toolId);
    }

    public boolean supports(String toolId) {
        return tools.containsKey(toolId);
    }
}
