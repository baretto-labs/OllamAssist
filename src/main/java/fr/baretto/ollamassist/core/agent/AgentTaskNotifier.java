package fr.baretto.ollamassist.core.agent;

import com.intellij.util.messages.Topic;
import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;

import java.util.List;

/**
 * Notificateur pour les événements de tâches de l'agent
 */
public interface AgentTaskNotifier {

    Topic<AgentTaskNotifier> TOPIC = Topic.create(
            "AgentTaskNotifier",
            AgentTaskNotifier.class
    );

    /**
     * Appelé quand une tâche démarre
     */
    void taskStarted(Task task);

    /**
     * Appelé quand une tâche se termine avec succès ou échec
     */
    void taskCompleted(Task task, TaskResult result);

    /**
     * Appelé pour signaler le progrès d'une tâche
     */
    void taskProgress(Task task, String progressMessage);

    /**
     * Appelé quand une tâche est annulée
     */
    void taskCancelled(Task task);

    /**
     * Appelé quand l'agent LangChain4J démarre le traitement
     */
    void agentProcessingStarted(String userRequest);

    /**
     * Appelé quand l'agent LangChain4J termine avec succès
     */
    void agentProcessingCompleted(String userRequest, String response);

    /**
     * Appelé quand l'agent LangChain4J échoue
     */
    void agentProcessingFailed(String userRequest, String errorMessage);

    /**
     * Appelé pour chaque token de streaming reçu
     */
    void agentStreamingToken(String token);

    /**
     * Appelé quand l'agent propose des actions qui nécessitent validation
     */
    void agentProposalRequested(String userRequest, List<Task> proposedTasks, fr.baretto.ollamassist.core.agent.ui.ActionProposalCard.ActionValidator validator);
}