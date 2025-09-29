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
        log.error("=== DEBUG TASK EXECUTOR ===");
        log.error("Task ID: {}", task.getId());
        log.error("Task Type: {}", task.getType());
        log.error("Task Description: {}", task.getDescription());
        log.error("Task Priority: {}", task.getPriority());
        log.error("Task Status: {}", task.getStatus());

        log.error("=== PARAMETERS ===");
        if (task.getParameters() != null) {
            task.getParameters().forEach((key, value) ->
                    log.error("Parameter '{}': '{}'", key, value));
        } else {
            log.error("NO PARAMETERS FOUND!");
        }

        log.error("=== SPECIFIC PARAMETER CHECKS ===");
        log.error("request parameter: '{}'", task.getParameter("request", String.class));
        log.error("operation parameter: '{}'", task.getParameter("operation", String.class));
        log.error("filePath parameter: '{}'", task.getParameter("filePath", String.class));
        log.error("content parameter: '{}'", task.getParameter("content", String.class));
        log.error("original_request parameter: '{}'", task.getParameter("original_request", String.class));
        log.error("llm_analyzed parameter: '{}'", task.getParameter("llm_analyzed", Boolean.class));

        log.error("=== END DEBUG ===");

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