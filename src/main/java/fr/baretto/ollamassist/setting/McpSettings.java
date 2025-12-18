package fr.baretto.ollamassist.setting;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings for Model Context Protocol (MCP) servers.
 */
@Service(Service.Level.PROJECT)
@State(
        name = "McpSettings",
        storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)}
)
public final class McpSettings implements PersistentStateComponent<McpSettings.State> {

    private State myState = new State();

    public static McpSettings getInstance(Project project) {
        return project.getService(McpSettings.class);
    }

    @Override
    public State getState() {
        if (myState == null) {
            myState = new State();
        }
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }

    public List<McpServerConfig> getMcpServers() {
        return myState.mcpServers != null ? myState.mcpServers : new ArrayList<>();
    }

    public void setMcpServers(List<McpServerConfig> mcpServers) {
        myState.mcpServers = mcpServers != null ? new ArrayList<>(mcpServers) : new ArrayList<>();
    }

    public boolean isMcpEnabled() {
        return myState.mcpEnabled;
    }

    public void setMcpEnabled(boolean enabled) {
        myState.mcpEnabled = enabled;
    }

    public boolean isMcpApprovalRequired() {
        return myState.mcpApprovalRequired;
    }

    public void setMcpApprovalRequired(boolean required) {
        myState.mcpApprovalRequired = required;
    }

    public int getMcpApprovalTimeoutSeconds() {
        return myState.mcpApprovalTimeoutSeconds;
    }

    public void setMcpApprovalTimeoutSeconds(int timeout) {
        myState.mcpApprovalTimeoutSeconds = timeout;
    }

    @Getter
    @Setter
    public static class State {
        /**
         * Global enable/disable for MCP integration
         */
        public boolean mcpEnabled = false;

        /**
         * List of configured MCP servers
         */
        public List<McpServerConfig> mcpServers = new ArrayList<>();

        /**
         * Require approval for MCP tool calls
         */
        public boolean mcpApprovalRequired = true;

        /**
         * Approval timeout in seconds
         */
        public int mcpApprovalTimeoutSeconds = 300;
    }
}
