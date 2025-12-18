package fr.baretto.ollamassist.mcp;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.setting.McpServerConfig;
import fr.baretto.ollamassist.setting.McpSettings;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Project-level service that manages temporary runtime activation state for MCP servers.
 * This state is separate from the permanent configuration in McpSettings.
 *
 * <p>Architecture:
 * <ul>
 *   <li>McpSettings.enabled - Permanent configuration (persisted in workspace.xml)</li>
 *   <li>McpRuntimeState.active - Temporary runtime state (not persisted, resets on project open)</li>
 * </ul>
 *
 * <p>A server is only active if: McpSettings.enabled AND McpRuntimeState.active
 */
@Service(Service.Level.PROJECT)
public final class McpRuntimeState {

    private final Map<String, Boolean> serverActiveStates = new ConcurrentHashMap<>();
    private final Project project;

    public McpRuntimeState(Project project) {
        this.project = project;
        initializeFromSettings();
    }

    /**
     * Initialize active states from settings.
     * All enabled servers are set to active by default.
     */
    private void initializeFromSettings() {
        serverActiveStates.clear();
        McpSettings settings = McpSettings.getInstance(project);
        for (McpServerConfig config : settings.getMcpServers()) {
            if (config.isEnabled()) {
                serverActiveStates.put(config.getName(), true);
            }
        }
    }

    /**
     * Check if a server is currently active in runtime.
     *
     * @param serverName the name of the server
     * @return true if the server is active, false otherwise
     */
    public boolean isServerActive(String serverName) {
        return serverActiveStates.getOrDefault(serverName, false);
    }

    /**
     * Set the runtime active state for a server.
     *
     * @param serverName the name of the server
     * @param active true to activate, false to deactivate
     */
    public void setServerActive(String serverName, boolean active) {
        serverActiveStates.put(serverName, active);
    }

    /**
     * Get all active states as a map.
     *
     * @return a copy of the active states map
     */
    public Map<String, Boolean> getAllActiveStates() {
        return new HashMap<>(serverActiveStates);
    }

    /**
     * Reset all runtime states to defaults (all enabled servers become active).
     */
    public void resetToDefaults() {
        initializeFromSettings();
    }

    /**
     * Get the McpRuntimeState instance for a project.
     *
     * @param project the project
     * @return the McpRuntimeState instance
     */
    public static McpRuntimeState getInstance(Project project) {
        return project.getService(McpRuntimeState.class);
    }
}
