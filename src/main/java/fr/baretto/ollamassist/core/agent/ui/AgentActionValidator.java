package fr.baretto.ollamassist.core.agent.ui;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.core.agent.AgentCoordinator;
import fr.baretto.ollamassist.core.agent.task.Task;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Implémentation concrète de l'ActionValidator pour valider/rejeter/modifier les actions proposées par l'agent
 */
@Slf4j
public class AgentActionValidator implements ActionProposalCard.ActionValidator {

    private final Project project;
    private final AgentCoordinator agentCoordinator;

    public AgentActionValidator(Project project, AgentCoordinator agentCoordinator) {
        this.project = project;
        this.agentCoordinator = agentCoordinator;
    }

    @Override
    public void approveActions(List<Task> tasks) {
        log.info("Approving {} actions", tasks.size());

        try {
            // Marquer les tâches comme approuvées et les exécuter sans nouvelle validation
            tasks.forEach(task -> task.setStatus(Task.TaskStatus.PENDING));
            agentCoordinator.executeTasksWithoutValidation(tasks);

            log.debug("Successfully approved and executed {} tasks", tasks.size());
        } catch (Exception e) {
            log.error("Error approving actions", e);
        }
    }

    @Override
    public void rejectActions(List<Task> tasks) {
        log.info("Rejecting {} actions", tasks.size());

        try {
            // Marquer les tâches comme rejetées
            tasks.forEach(task -> task.setStatus(Task.TaskStatus.CANCELLED));

            log.debug("Successfully rejected {} tasks", tasks.size());
        } catch (Exception e) {
            log.error("Error rejecting actions", e);
        }
    }

    @Override
    public void modifyActions(List<Task> tasks) {
        log.info("Modifying {} actions", tasks.size());

        try {
            // Pour l'instant, marquer les tâches comme en attente de modification
            // TODO: Implémenter l'interface de modification des tâches
            tasks.forEach(task -> task.setStatus(Task.TaskStatus.PENDING));

            log.debug("Successfully marked {} tasks for modification", tasks.size());
        } catch (Exception e) {
            log.error("Error modifying actions", e);
        }
    }
}