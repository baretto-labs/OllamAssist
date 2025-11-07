package fr.baretto.ollamassist.agent.tool;

import fr.baretto.ollamassist.agent.model.AgentType;
import fr.baretto.ollamassist.agent.observability.StepTrace;

import java.util.List;
import java.util.Map;

/**
 * Interface for agent tools.
 * Tools are executable actions with @Tool annotation.
 * This interface provides metadata and custom observability.
 */
public interface AgentTool {
    /**
     * Returns the unique tool identifier.
     */
    String getId();

    /**
     * Returns the tool name.
     */
    String getName();

    /**
     * Returns the tool description.
     */
    String getDescription();

    /**
     * Returns the list of tool parameters.
     */
    List<ToolParameter> getParameters();

    /**
     * Executes the tool.
     * The actual @Tool method should call this with observability support.
     */
    ToolResult execute(Map<String, Object> parameters, StepTrace trace);

    /**
     * Whether this tool requires user approval (for destructive actions).
     */
    boolean requiresUserApproval();

    /**
     * Agent that owns this tool.
     */
    AgentType getOwnerAgent();
}
