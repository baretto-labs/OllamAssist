package fr.baretto.ollamassist.core.agent.execution;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import lombok.extern.slf4j.Slf4j;

/**
 * Exécuteur pour les tâches composites (qui peuvent contenir plusieurs sous-tâches)
 */
@Slf4j
public class CompositeTaskExecutor implements ExecutionEngine.TaskExecutor {

    private final Project project;
    private final ExecutionEngine executionEngine;

    public CompositeTaskExecutor(Project project, ExecutionEngine executionEngine) {
        this.project = project;
        this.executionEngine = executionEngine;
    }

    @Override
    public TaskResult execute(Task task) {
        log.debug("Executing composite task: {}", task.getId());

        try {
            String request = task.getParameter("request", String.class);
            if (request == null) {
                return TaskResult.failure("Paramètre 'request' manquant");
            }

            // Pour une tâche composite, on peut analyser la demande et créer des sous-tâches
            // TODO: Implémenter la logique de décomposition et d'exécution des sous-tâches

            return TaskResult.success("Tâche composite exécutée (TODO: implémentation réelle)");

        } catch (Exception e) {
            log.error("Error in composite task execution", e);
            return TaskResult.failure("Erreur lors de l'exécution de la tâche composite", e);
        }
    }

    @Override
    public boolean canExecute(Task task) {
        return task.getType() == Task.TaskType.COMPOSITE;
    }

    @Override
    public String getExecutorName() {
        return "CompositeTaskExecutor";
    }
}