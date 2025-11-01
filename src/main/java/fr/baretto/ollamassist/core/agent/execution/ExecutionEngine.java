package fr.baretto.ollamassist.core.agent.execution;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.core.agent.notifications.AgentNotificationService;
import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Moteur d'exécution des tâches agent
 */
@Slf4j
public class ExecutionEngine {

    private final Project project;
    private final Map<Task.TaskType, TaskExecutor> executors;
    private final AgentNotificationService notificationService;

    // FIXED P1-4: Statistics collection with thread-safe atomic counters
    private final AtomicLong totalExecutions = new AtomicLong(0);
    private final AtomicLong successfulExecutions = new AtomicLong(0);
    private final AtomicLong failedExecutions = new AtomicLong(0);
    private final AtomicLong totalExecutionTimeMs = new AtomicLong(0);

    public ExecutionEngine(Project project) {
        this.project = project;
        this.notificationService = new AgentNotificationService(project);
        this.executors = initializeExecutors();
    }

    /**
     * Exécute une tâche
     */
    public TaskResult executeTask(Task task) {
        if (task.isCancelled()) {
            return TaskResult.failure("Tâche annulée avant exécution");
        }

        LocalDateTime startTime = LocalDateTime.now();
        task.markStarted();

        log.info("Executing task: {} - {}", task.getId(), task.getDescription());

        // Notifier le début de la tâche
        notificationService.notifyTaskStarted(task);

        try {
            TaskExecutor executor = executors.get(task.getType());
            if (executor == null) {
                String error = "Aucun exécuteur trouvé pour le type de tâche: " + task.getType();
                task.markFailed(error);
                return TaskResult.failure(error);
            }

            log.error("USING EXECUTOR: {} for task type: {}", executor.getExecutorName(), task.getType());

            // Exécuter la tâche
            TaskResult result = executor.execute(task);

            // Calculer le temps d'exécution
            Duration executionTime = Duration.between(startTime, LocalDateTime.now());

            // FIXED P1-4: Update statistics counters
            totalExecutions.incrementAndGet();
            totalExecutionTimeMs.addAndGet(executionTime.toMillis());

            if (result.isSuccess()) {
                task.markCompleted();
                successfulExecutions.incrementAndGet();
                log.info("Task completed successfully: {} in {}ms", task.getId(), executionTime.toMillis());

                // Notifier le succès
                notificationService.notifyTaskSuccess(task, result);
            } else {
                task.markFailed(result.getErrorMessage());
                failedExecutions.incrementAndGet();
                log.error("Task failed: {} - {}", task.getId(), result.getErrorMessage());

                // Notifier l'échec
                notificationService.notifyTaskFailure(task, result);
            }

            return TaskResult.builder()
                    .success(result.isSuccess())
                    .message(result.getMessage())
                    .errorMessage(result.getErrorMessage())
                    .data(result.getData())
                    .executionTime(executionTime)
                    .taskId(task.getId())
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            String error = "Erreur lors de l'exécution de la tâche: " + e.getMessage();
            task.markFailed(error);
            log.error("Task execution error: {}", task.getId(), e);

            Duration executionTime = Duration.between(startTime, LocalDateTime.now());

            // FIXED P1-4: Update statistics counters for exceptions
            totalExecutions.incrementAndGet();
            failedExecutions.incrementAndGet();
            totalExecutionTimeMs.addAndGet(executionTime.toMillis());

            TaskResult errorResult = TaskResult.builder()
                    .success(false)
                    .errorMessage(error)
                    .executionTime(executionTime)
                    .taskId(task.getId())
                    .timestamp(LocalDateTime.now())
                    .build();

            // Notifier l'erreur
            notificationService.notifyTaskFailure(task, errorResult);

            return errorResult;
        }
    }

    private Map<Task.TaskType, TaskExecutor> initializeExecutors() {
        Map<Task.TaskType, TaskExecutor> executors = new HashMap<>();

        // UTILISER LES VRAIS EXECUTORS MAINTENANT !
        DebugTaskExecutor debugExecutor = new DebugTaskExecutor(project);
        FileOperationExecutor fileExecutor = new FileOperationExecutor(project);
        CodeModificationExecutor codeExecutor = new CodeModificationExecutor(project);
        GitOperationExecutor gitExecutor = new GitOperationExecutor(project);
        BuildOperationExecutor buildExecutor = new BuildOperationExecutor(project);

        // VRAIS EXECUTORS - Production Ready ! 
        executors.put(Task.TaskType.FILE_OPERATION, fileExecutor);
        executors.put(Task.TaskType.CODE_MODIFICATION, codeExecutor);
        executors.put(Task.TaskType.GIT_OPERATION, gitExecutor);
        executors.put(Task.TaskType.BUILD_OPERATION, buildExecutor);

        // Seuls les opérations avancées gardent le debug
        executors.put(Task.TaskType.CODE_ANALYSIS, debugExecutor);
        executors.put(Task.TaskType.MCP_OPERATION, debugExecutor);
        executors.put(Task.TaskType.COMPOSITE, debugExecutor);

        log.info("Production executors initialized - FILE: {}, CODE: {}, GIT: {}, BUILD: {}, DEBUG: {}",
                fileExecutor.getExecutorName(), codeExecutor.getExecutorName(),
                gitExecutor.getExecutorName(), buildExecutor.getExecutorName(), debugExecutor.getExecutorName());
        log.info("Initialized {} task executors", executors.size());
        return executors;
    }

    /**
     * Obtient l'exécuteur pour une tâche donnée
     */
    public TaskExecutor getExecutorForTask(Task task) {
        return executors.get(task.getType());
    }

    /**
     * Obtient l'exécuteur de fichiers (pour les tests)
     */
    public FileOperationExecutor getFileOperationExecutor() {
        return (FileOperationExecutor) executors.get(Task.TaskType.FILE_OPERATION);
    }

    /**
     * Obtient l'exécuteur de code (pour les tests)
     */
    public CodeModificationExecutor getCodeModificationExecutor() {
        return (CodeModificationExecutor) executors.get(Task.TaskType.CODE_MODIFICATION);
    }

    /**
     * Obtient le service de notifications (pour les tests)
     */
    public AgentNotificationService getNotificationService() {
        return notificationService;
    }

    /**
     * Obtient les statistiques d'exécution
     * FIXED P1-4: Return actual collected statistics
     */
    public ExecutionStats getStats() {
        long total = totalExecutions.get();
        long avgTimeMs = total > 0 ? totalExecutionTimeMs.get() / total : 0;

        return ExecutionStats.builder()
                .totalExecutions(total)
                .successfulExecutions(successfulExecutions.get())
                .failedExecutions(failedExecutions.get())
                .averageExecutionTime(Duration.ofMillis(avgTimeMs))
                .build();
    }

    /**
     * Nettoie les ressources de l'ExecutionEngine
     */
    public void dispose() {
        if (notificationService != null) {
            notificationService.dispose();
        }
        log.info("ExecutionEngine disposed");
    }

    /**
     * Interface pour les exécuteurs de tâches
     */
    public interface TaskExecutor {
        TaskResult execute(Task task);

        boolean canExecute(Task task);

        String getExecutorName();
    }

    /**
     * Statistiques d'exécution
     */
    @lombok.Builder
    @lombok.Value
    public static class ExecutionStats {
        long totalExecutions;
        long successfulExecutions;
        long failedExecutions;
        Duration averageExecutionTime;

        public double getSuccessRate() {
            return totalExecutions > 0 ? (double) successfulExecutions / totalExecutions : 0.0;
        }
    }
}