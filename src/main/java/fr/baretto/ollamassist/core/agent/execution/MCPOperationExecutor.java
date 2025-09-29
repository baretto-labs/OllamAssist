package fr.baretto.ollamassist.core.agent.execution;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import fr.baretto.ollamassist.core.mcp.capability.MCPCapabilityProvider;
import fr.baretto.ollamassist.core.mcp.protocol.MCPResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Exécuteur pour les opérations MCP
 */
@Slf4j
public class MCPOperationExecutor implements ExecutionEngine.TaskExecutor {

    private final Project project;
    private final MCPCapabilityProvider capabilityProvider;

    public MCPOperationExecutor(Project project) {
        this.project = project;
        this.capabilityProvider = project.getService(MCPCapabilityProvider.class);
    }

    @Override
    public TaskResult execute(Task task) {
        log.debug("Executing MCP operation task: {}", task.getId());

        try {
            String capability = task.getParameter("capability", String.class);
            String serverId = task.getParameter("serverId", String.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> params = task.getParameter("params", Map.class);

            if (capability == null) {
                return TaskResult.failure("Paramètre 'capability' manquant");
            }

            if (params == null) {
                params = Map.of();
            }

            // Exécuter la capacité MCP
            MCPResponse response;
            if (serverId != null) {
                response = capabilityProvider.executeCapability(serverId, capability, params).join();
            } else {
                response = capabilityProvider.executeCapability(capability, params).join();
            }

            if (response.isSuccess()) {
                return TaskResult.success(
                        "Opération MCP exécutée avec succès",
                        Map.of("mcpResponse", response)
                );
            } else {
                return TaskResult.failure("Échec de l'opération MCP: " + response.getError().getMessage());
            }

        } catch (Exception e) {
            log.error("Error in MCP operation execution", e);
            return TaskResult.failure("Erreur lors de l'opération MCP", e);
        }
    }

    @Override
    public boolean canExecute(Task task) {
        return task.getType() == Task.TaskType.MCP_OPERATION;
    }

    @Override
    public String getExecutorName() {
        return "MCPOperationExecutor";
    }
}