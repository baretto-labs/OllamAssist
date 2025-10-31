package fr.baretto.ollamassist.core.agent.execution;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import lombok.extern.slf4j.Slf4j;

/**
 * TaskExecutor de debug qui log tout ce qui se passe
 */
@Slf4j
public class DebugTaskExecutor implements ExecutionEngine.TaskExecutor {

    private final Project project;

    public DebugTaskExecutor(Project project) {
        this.project = project;
    }

    @Override
    public TaskResult execute(Task task) {
        log.debug("=== DEBUG TASK EXECUTOR ===");
        log.debug("Task ID: {}", task.getId());
        log.debug("Task Type: {}", task.getType());
        log.debug("Task Description: {}", task.getDescription());
        log.debug("Task Priority: {}", task.getPriority());
        log.debug("Task Status: {}", task.getStatus());

        log.debug("=== PARAMETERS ===");
        if (task.getParameters() != null) {
            task.getParameters().forEach((key, value) ->
                    log.debug("Parameter '{}': '{}'", key, value));
        } else {
            log.debug("NO PARAMETERS FOUND!");
        }

        log.debug("=== SPECIFIC PARAMETER CHECKS ===");
        log.debug("request parameter: '{}'", task.getParameter("request", String.class));
        log.debug("operation parameter: '{}'", task.getParameter("operation", String.class));
        log.debug("filePath parameter: '{}'", task.getParameter("filePath", String.class));
        log.debug("content parameter: '{}'", task.getParameter("content", String.class));
        log.debug("original_request parameter: '{}'", task.getParameter("original_request", String.class));
        log.debug("llm_analyzed parameter: '{}'", task.getParameter("llm_analyzed", Boolean.class));

        log.debug("=== END DEBUG ===");

        // Toujours retourner succès pour le debug
        return TaskResult.success("Debug task executor - check logs for details");
    }

    @Override
    public boolean canExecute(Task task) {
        // Accepter TOUS les types de tâches pour le debug
        return true;
    }

    @Override
    public String getExecutorName() {
        return "DebugTaskExecutor";
    }
}