package fr.baretto.ollamassist.core.agent;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.core.agent.execution.ExecutionEngine;
import fr.baretto.ollamassist.core.agent.rollback.ActionSnapshot;
import fr.baretto.ollamassist.core.agent.rollback.RollbackManager;
import fr.baretto.ollamassist.core.agent.state.StateManager;
import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Agent Coordinator - Chef d'orchestre principal du mode agent
 * <p>
 * Responsabilités:
 * - Coordination des différents composants de l'agent
 * - Gestion du cycle de vie des tâches
 * - Interface principale pour les interactions agent
 * - Gestion de l'état global de l'agent
 */
@Slf4j
@Service(Service.Level.PROJECT)
public final class AgentCoordinator implements Disposable {

    private final Project project;
    private final AgentService agentService;
    private final ExecutionEngine executionEngine;
    private final StateManager stateManager;
    private final RollbackManager rollbackManager;
    private final ConcurrentMap<String, Task> activeTasks = new ConcurrentHashMap<>();
    private fr.baretto.ollamassist.chat.ui.MessagesPanel messagesPanel;
    private volatile AgentState currentState = AgentState.IDLE;

    public AgentCoordinator(Project project) {
        this.project = project;
        this.agentService = new AgentService(project);
        this.executionEngine = new ExecutionEngine(project);
        this.stateManager = new StateManager(project);
        this.rollbackManager = new RollbackManager(project);

        log.info("AgentCoordinator initialized with LangChain4J agent for project: {}", project.getName());
    }

    /**
     * Configure le MessagesPanel pour afficher les propositions d'actions
     */
    public void setMessagesPanel(fr.baretto.ollamassist.chat.ui.MessagesPanel messagesPanel) {
        this.messagesPanel = messagesPanel;
        log.debug("MessagesPanel configured for action proposals");
    }

    /**
     * Exécute une requête utilisateur en mode agent avec LangChain4J
     */
    public CompletableFuture<TaskResult> executeUserRequest(String userRequest) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Processing user request with LangChain4J agent: {}", userRequest);

                // Changer l'état à PROCESSING
                setState(AgentState.PROCESSING);

                // Notifier le démarrage de l'exécution
                project.getMessageBus()
                        .syncPublisher(AgentTaskNotifier.TOPIC)
                        .agentProcessingStarted(userRequest);

                // Exécuter via l'agent LangChain4J avec tools
                log.error("⭐ COORDINATOR: About to call agentService.executeUserRequest()");
                String agentResponse = agentService.executeUserRequest(userRequest).get();
                log.error("⭐ COORDINATOR: AgentService returned: {}", agentResponse);

                // Notifier la completion
                project.getMessageBus()
                        .syncPublisher(AgentTaskNotifier.TOPIC)
                        .agentProcessingCompleted(userRequest, agentResponse);

                log.info("Agent execution completed successfully");
                return TaskResult.success(agentResponse);

            } catch (Exception e) {
                log.error("Error executing user request with agent", e);

                // Notifier l'erreur
                project.getMessageBus()
                        .syncPublisher(AgentTaskNotifier.TOPIC)
                        .agentProcessingFailed(userRequest, e.getMessage());

                return TaskResult.failure("Erreur lors de l'exécution de l'agent: " + e.getMessage());
            } finally {
                setState(AgentState.IDLE);
            }
        });
    }

    /**
     * Annule toutes les tâches en cours
     */
    public void cancelAllTasks() {
        log.info("Cancelling all active tasks. Count: {}", activeTasks.size());

        activeTasks.values().forEach(task -> {
            try {
                task.cancel();
                // Notifier l'annulation de la tâche
                project.getMessageBus()
                        .syncPublisher(AgentTaskNotifier.TOPIC)
                        .taskCancelled(task);
            } catch (Exception e) {
                log.warn("Error cancelling task: {}", task.getId(), e);
            }
        });

        activeTasks.clear();
        setState(AgentState.IDLE);
    }

    /**
     * Obtient l'état actuel de l'agent
     */
    public AgentState getState() {
        return currentState;
    }

    private void setState(AgentState newState) {
        AgentState oldState = this.currentState;
        this.currentState = newState;

        log.debug("Agent state changed: {} -> {}", oldState, newState);

        // Notifier les listeners du changement d'état
        project.getMessageBus()
                .syncPublisher(AgentStateNotifier.TOPIC)
                .stateChanged(oldState, newState);
    }

    /**
     * Obtient la liste des tâches actives
     */
    public List<Task> getActiveTasks() {
        return List.copyOf(activeTasks.values());
    }

    /**
     * Vérifie si l'agent est occupé
     */
    public boolean isBusy() {
        return currentState != AgentState.IDLE;
    }

    /**
     * Obtient les statistiques de l'agent
     */
    public AgentStats getStats() {
        return AgentStats.builder()
                .activeTasksCount(activeTasks.size())
                .currentState(currentState)
                .totalTasksExecuted(stateManager.getTotalTasksExecuted())
                .successRate(stateManager.getSuccessRate())
                .build();
    }

    // Méthodes de rollback

    /**
     * Annule une action spécifique
     */
    public fr.baretto.ollamassist.core.agent.rollback.RollbackResult rollbackAction(String actionId) {
        return rollbackManager.rollbackAction(actionId);
    }

    /**
     * Annule toutes les actions d'une tâche
     */
    public fr.baretto.ollamassist.core.agent.rollback.RollbackResult rollbackTask(String taskId) {
        return rollbackManager.rollbackTask(taskId);
    }

    /**
     * Obtient les snapshots d'une tâche
     */
    public List<ActionSnapshot> getTaskSnapshots(String taskId) {
        return rollbackManager.getTaskSnapshots(taskId);
    }

    /**
     * Nettoie les snapshots d'une tâche terminée
     */
    public void cleanupTaskSnapshots(String taskId) {
        rollbackManager.cleanupTaskSnapshots(taskId);
    }

    // Méthodes compatibles avec les tests TDD

    /**
     * Planifie les tâches pour une demande utilisateur (compatibilité)
     * Note: En mode LangChain4J, les tasks sont exécutées directement par l'agent
     */
    public List<Task> planTasks(String userRequest) {
        log.warn("planTasks() called in LangChain4J agent mode - tasks are executed directly");
        return List.of(); // Retourne une liste vide car les tools sont exécutés directement
    }

    /**
     * Exécute une liste de tâches
     */
    public CompletableFuture<TaskResult> executeTasks(List<Task> tasks) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                setState(AgentState.EXECUTING);

                // Afficher la proposition d'action avec validation utilisateur
                if (messagesPanel != null) {
                    fr.baretto.ollamassist.core.agent.ui.AgentActionValidator validator =
                            new fr.baretto.ollamassist.core.agent.ui.AgentActionValidator(project, this);
                    messagesPanel.displayActionProposal(tasks, validator);
                    log.info("Action proposal displayed for {} tasks, waiting for user validation", tasks.size());
                    return TaskResult.success("Proposition d'actions affichée");
                }

                // Fallback si pas de MessagesPanel configuré - exécution directe
                TaskResult finalResult = TaskResult.success("Tâches exécutées avec succès");

                for (Task task : tasks) {
                    String taskId = UUID.randomUUID().toString();
                    activeTasks.put(taskId, task);

                    try {
                        TaskResult result = executionEngine.executeTask(task);
                        if (!result.isSuccess()) {
                            finalResult = TaskResult.failure("Échec de la tâche: " + result.getErrorMessage());
                            break;
                        }
                        stateManager.saveTaskResult(taskId, result);
                    } finally {
                        activeTasks.remove(taskId);
                    }
                }

                return finalResult;
            } finally {
                setState(AgentState.IDLE);
            }
        });
    }

    /**
     * Exécute les tâches sans afficher de proposition (appelé après validation)
     */
    public CompletableFuture<TaskResult> executeTasksWithoutValidation(List<Task> tasks) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                setState(AgentState.EXECUTING);

                TaskResult finalResult = TaskResult.success("Tâches exécutées avec succès");

                for (Task task : tasks) {
                    String taskId = UUID.randomUUID().toString();
                    activeTasks.put(taskId, task);

                    try {
                        TaskResult result = executionEngine.executeTask(task);
                        if (!result.isSuccess()) {
                            finalResult = TaskResult.failure("Échec de la tâche: " + result.getErrorMessage());
                            break;
                        }
                        stateManager.saveTaskResult(taskId, result);
                    } finally {
                        activeTasks.remove(taskId);
                    }
                }

                return finalResult;
            } finally {
                setState(AgentState.IDLE);
            }
        });
    }

    /**
     * Nettoie les ressources de l'AgentCoordinator
     */
    @Override
    public void dispose() {
        if (executionEngine != null) {
            executionEngine.dispose();
        }
        log.info("🧹 AgentCoordinator disposed");
    }

    /**
     * États possibles de l'agent
     */
    public enum AgentState {
        IDLE,           // Agent inactif, prêt à recevoir des tâches
        PROCESSING,     // Agent en train de traiter une requête
        EXECUTING,      // Agent en train d'exécuter des tâches
        ERROR           // Agent en erreur
    }
}