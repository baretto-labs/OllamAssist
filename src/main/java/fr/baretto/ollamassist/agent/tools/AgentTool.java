package fr.baretto.ollamassist.agent.tools;

import java.util.Map;

/**
 * A tool that the agent can invoke during plan execution.
 * Named AgentTool to avoid clash with LangChain4j's @Tool annotation.
 */
public interface AgentTool {

    /** The unique identifier matching toolId values used in AgentPlan steps. */
    String toolId();

    ToolResult execute(Map<String, Object> params);
}
