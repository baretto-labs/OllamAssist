package fr.baretto.ollamassist.core.agent.execution;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import lombok.extern.slf4j.Slf4j;

/**
 * Exécuteur pour les tâches d'analyse de code
 */
@Slf4j
public class CodeAnalysisExecutor implements ExecutionEngine.TaskExecutor {

    private final Project project;

    public CodeAnalysisExecutor(Project project) {
        this.project = project;
    }

    @Override
    public TaskResult execute(Task task) {
        log.debug("Executing code analysis task: {}", task.getId());

        try {
            String request = task.getParameter("request", String.class);
            if (request == null) {
                return TaskResult.failure("Paramètre 'request' manquant");
            }

            // L'analyse de code automatique n'est pas encore implémentée
            log.warn("Code analysis not yet implemented for request: {}", request);

            return TaskResult.failure("L'analyse automatique de code n'est pas encore disponible. " +
                    "Veuillez utiliser les outils d'analyse intégrés d'IntelliJ IDEA " +
                    "(Code → Inspect Code) pour analyser votre projet.");

        } catch (Exception e) {
            log.error("Error in code analysis execution", e);
            return TaskResult.failure("Erreur lors de l'analyse", e);
        }
    }

    @Override
    public boolean canExecute(Task task) {
        return task.getType() == Task.TaskType.CODE_ANALYSIS;
    }

    @Override
    public String getExecutorName() {
        return "CodeAnalysisExecutor";
    }
}