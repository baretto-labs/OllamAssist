package fr.baretto.ollamassist.agent.tools;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.agent.AuditLogger;
import fr.baretto.ollamassist.agent.plan.Step;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@Slf4j
public final class ToolDispatcher {

    private final ToolRegistry registry;
    @Nullable
    private final AuditLogger auditLogger;
    private final ToolRateLimiter rateLimiter = new ToolRateLimiter();

    public ToolDispatcher(ToolRegistry registry, Project project) {
        this.registry = registry;
        this.auditLogger = project != null ? project.getService(AuditLogger.class) : null;
    }

    /** Resets per-execution rate-limit counters. Call before each new agent execution. */
    public void resetRateLimits() {
        rateLimiter.reset();
    }

    /** Dispatches a step with no previous context. */
    public ToolResult dispatch(Step step) {
        return dispatch(step, "");
    }

    /**
     * Dispatches a step, resolving any {@code {{prev_output}}} / {@code {{prev_output_first_line}}}
     * placeholders in params using the output of the previous step.
     */
    public ToolResult dispatch(Step step, String previousOutput) {
        AgentTool tool = registry.get(step.getToolId());
        if (tool == null) {
            ToolResult failure = ToolResult.failure("Unknown tool: '" + step.getToolId() + "'");
            audit(step, Map.of(), failure);
            return failure;
        }
        if (!rateLimiter.tryAcquire(step.getToolId())) {
            ToolResult failure = ToolResult.failure(
                    "Tool '" + step.getToolId() + "' has exceeded its call limit for this execution. "
                    + "The agent may be stuck in a loop.");
            audit(step, Map.of(), failure);
            return failure;
        }
        try {
            Map<String, Object> resolvedParams = StepParamResolver.resolve(step.getParams(), previousOutput);
            log.debug("Dispatching step '{}' to tool '{}' with params {}",
                    step.getDescription(), step.getToolId(), resolvedParams);
            ToolResult result = tool.execute(resolvedParams);
            audit(step, resolvedParams, result);
            return result;
        } catch (StepParamResolver.UnresolvablePlaceholderException e) {
            log.warn("Unresolvable placeholder in step '{}': {}", step.getDescription(), e.getMessage());
            ToolResult failure = ToolResult.failure(e.getMessage());
            audit(step, Map.of(), failure);
            return failure;
        } catch (Exception e) {
            log.error("Tool '{}' threw an exception for step '{}'", step.getToolId(), step.getDescription(), e);
            ToolResult failure = ToolResult.failure("Tool '" + step.getToolId() + "' failed: " + e.getMessage());
            audit(step, Map.of(), failure);
            return failure;
        }
    }

    private void audit(Step step, Map<String, Object> params, ToolResult result) {
        if (auditLogger == null) return;
        auditLogger.record(
                step.getToolId(),
                step.getDescription(),
                params.keySet(),
                result.isSuccess(),
                result.isSuccess() ? null : result.getErrorMessage()
        );
    }
}
