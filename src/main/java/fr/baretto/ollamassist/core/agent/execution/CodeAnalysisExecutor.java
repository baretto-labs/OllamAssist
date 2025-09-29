package fr.baretto.ollamassist.core.agent.execution;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

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

            // Simulation d'analyse de code
            // TODO: Intégrer avec le système d'analyse réel
            Map<String, Object> analysisResults = performAnalysis(request);

            return TaskResult.success(
                    "Analyse de code terminée",
                    analysisResults
            );

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

    private Map<String, Object> performAnalysis(String request) {
        Map<String, Object> results = new HashMap<>();

        // Simulation d'analyse
        results.put("projectName", project.getName());
        results.put("analysisType", "basic");
        results.put("fileCount", 0); // TODO: Compter les fichiers réels
        results.put("issuesFound", 0); // TODO: Analyser les problèmes réels
        results.put("recommendations", "Aucune recommandation pour le moment");

        log.debug("Code analysis completed for project: {}", project.getName());
        return results;
    }
}