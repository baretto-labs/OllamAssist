package fr.baretto.ollamassist.setting;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings for Model Context Protocol (MCP) servers.
 */
@Service
@State(
        name = "McpSettings",
        storages = {@Storage("OllamAssist.xml")}
)
public final class McpSettings implements PersistentStateComponent<McpSettings.State> {

    private State myState = new State();

    public static McpSettings getInstance() {
        return ApplicationManager.getApplication().getService(McpSettings.class);
    }

    @Nullable
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
    }
}
