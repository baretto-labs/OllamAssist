package fr.baretto.ollamassist.core.agent.state;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gestionnaire d'état de l'agent
 * Responsable de la persistance et de la gestion de l'état des tâches
 */
@Slf4j
public class StateManager {

    private final Project project;
    private final ConcurrentMap<String, TaskResult> taskResults = new ConcurrentHashMap<>();
    private final AtomicLong totalTasks = new AtomicLong(0);
    private final AtomicLong successfulTasks = new AtomicLong(0);
    private final AtomicLong failedTasks = new AtomicLong(0);

    public StateManager(Project project) {
        this.project = project;
        log.info("StateManager initialized for project: {}", project.getName());
    }

    /**
     * Sauvegarde le résultat d'une tâche
     */
    public void saveTaskResult(String taskId, TaskResult result) {
        taskResults.put(taskId, result);
        totalTasks.incrementAndGet();

        if (result.isSuccess()) {
            successfulTasks.incrementAndGet();
        } else {
            failedTasks.incrementAndGet();
        }

        log.debug("Task result saved: {} - Success: {}", taskId, result.isSuccess());
    }

    /**
     * Récupère le résultat d'une tâche
     */
    public TaskResult getTaskResult(String taskId) {
        return taskResults.get(taskId);
    }

    /**
     * Obtient le nombre total de tâches exécutées
     */
    public long getTotalTasksExecuted() {
        return totalTasks.get();
    }

    /**
     * Obtient le taux de succès
     */
    public double getSuccessRate() {
        long total = totalTasks.get();
        if (total == 0) return 0.0;
        return (double) successfulTasks.get() / total;
    }

    /**
     * Obtient le nombre de tâches réussies
     */
    public long getSuccessfulTasksCount() {
        return successfulTasks.get();
    }

    /**
     * Obtient le nombre de tâches échouées
     */
    public long getFailedTasksCount() {
        return failedTasks.get();
    }

    /**
     * Nettoie les anciens résultats (pour éviter la fuite mémoire)
     */
    public void cleanup() {
        log.info("Cleaning up state manager. Current task results: {}", taskResults.size());

        // TODO: Implémenter une stratégie de nettoyage plus sophistiquée
        // Par exemple, ne garder que les N derniers résultats ou les résultats récents

        if (taskResults.size() > 1000) {
            taskResults.clear();
            log.info("Task results cleared due to size limit");
        }
    }

    /**
     * Réinitialise les statistiques
     */
    public void resetStats() {
        totalTasks.set(0);
        successfulTasks.set(0);
        failedTasks.set(0);
        taskResults.clear();
        log.info("Agent statistics reset");
    }
}