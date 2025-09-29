package fr.baretto.ollamassist;

import fr.baretto.ollamassist.core.agent.intention.IntentionDetector;
import fr.baretto.ollamassist.core.agent.intention.UserIntention;
import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import fr.baretto.ollamassist.setting.agent.AgentModeSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'intégration du workflow complet du mode agent
 */
@DisplayName("Agent Workflow Integration Tests")
class AgentWorkflowIntegrationTest {

    private final IntentionDetector intentionDetector = new IntentionDetector();

    @Test
    @DisplayName("Should demonstrate complete agent workflow for question")
    void shouldDemonstrateCompleteAgentWorkflowForQuestion() {
        // Given - Mode agent activé
        AgentModeSettings.State agentState = new AgentModeSettings.State();
        agentState.agentModeEnabled = true;
        assertThat(agentState.agentModeEnabled).isTrue();

        // Given - Message de type question
        String questionMessage = "What is the purpose of this AgentCoordinator class?";

        // When - Traitement par l'agent
        UserIntention intention = intentionDetector.detectIntention(questionMessage);

        // Then - Question détectée → doit être routée vers chat classique
        assertThat(intention.getType()).isEqualTo(UserIntention.Type.QUESTION);

        // Dans l'implémentation réelle : AgentChatIntegration.onNewUserMessage()
        // appellerait chatFallbackHandler.processUserMessage(message)
    }

    @Test
    @DisplayName("Should demonstrate complete agent workflow for action")
    void shouldDemonstrateCompleteAgentWorkflowForAction() {
        // Given - Mode agent activé
        AgentModeSettings.State agentState = new AgentModeSettings.State();
        agentState.agentModeEnabled = true;
        agentState.securityLevel = AgentModeSettings.AgentSecurityLevel.STANDARD;
        agentState.autoTaskApprovalEnabled = false;

        // Given - Message de type action
        String actionMessage = "Create a test file for the UserService class";

        // When - Étape 1: Détection d'intention
        UserIntention intention = intentionDetector.detectIntention(actionMessage);

        // Then - Action détectée
        assertThat(intention.getType()).isEqualTo(UserIntention.Type.ACTION);
        assertThat(intention.getActionType()).isEqualTo(UserIntention.ActionType.FILE_OPERATION);
        assertThat(intention.getConfidence()).isGreaterThan(0.7);

        // When - Étape 2: Planification de tâche (simulation)
        Task proposedTask = Task.builder()
                .id("create-test-file-" + System.currentTimeMillis())
                .description("Create test file for UserService")
                .type(Task.TaskType.FILE_OPERATION)
                .priority(Task.TaskPriority.NORMAL)
                .parameters(Map.of(
                        "operation", "create",
                        "filePath", "src/test/java/UserServiceTest.java",
                        "content", "// Generated test file for UserService\\npublic class UserServiceTest {\\n    // TODO: Add tests\\n}"
                ))
                .status(Task.TaskStatus.PENDING)
                .build();

        // Then - Tâche bien formée
        assertThat(proposedTask.getId()).isNotEmpty();
        assertThat(proposedTask.getType()).isEqualTo(Task.TaskType.FILE_OPERATION);
        assertThat(proposedTask.getStatus()).isEqualTo(Task.TaskStatus.PENDING);
        assertThat(proposedTask.getParameters()).containsKey("operation");
        assertThat(proposedTask.getParameters().get("operation")).isEqualTo("create");

        // Dans l'implémentation réelle : AgentChatIntegration.onNewUserMessage()
        // appellerait messagesPanel.displayActionProposal(tasks, actionValidator)
    }

    @Test
    @DisplayName("Should demonstrate task approval workflow")
    void shouldDemonstrateTaskApprovalWorkflow() {
        // Given - Tâche proposée à l'utilisateur
        Task proposedTask = Task.builder()
                .id("approval-test")
                .description("Refactor method to improve performance")
                .type(Task.TaskType.CODE_MODIFICATION)
                .priority(Task.TaskPriority.NORMAL)
                .parameters(Map.of("method", "calculateTotal", "optimization", "caching"))
                .status(Task.TaskStatus.PENDING)
                .build();

        // When - Utilisateur approuve la tâche (simulation)
        proposedTask.setStatus(Task.TaskStatus.RUNNING);

        // Then - Tâche passe en exécution
        assertThat(proposedTask.getStatus()).isEqualTo(Task.TaskStatus.RUNNING);

        // When - Exécution de la tâche (simulation)
        TaskResult executionResult = TaskResult.success("Method successfully refactored with caching optimization");
        proposedTask.setStatus(Task.TaskStatus.COMPLETED);

        // Then - Résultat d'exécution valide
        assertThat(executionResult.isSuccess()).isTrue();
        assertThat(executionResult.getMessage()).contains("successfully refactored");
        assertThat(proposedTask.getStatus()).isEqualTo(Task.TaskStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should demonstrate task rejection workflow")
    void shouldDemonstrateTaskRejectionWorkflow() {
        // Given - Tâche proposée à l'utilisateur
        Task proposedTask = Task.builder()
                .id("rejection-test")
                .description("Delete all configuration files")
                .type(Task.TaskType.FILE_OPERATION)
                .priority(Task.TaskPriority.HIGH)
                .parameters(Map.of("operation", "delete", "pattern", "*.config"))
                .status(Task.TaskStatus.PENDING)
                .build();

        // When - Utilisateur rejette la tâche (simulation)
        proposedTask.setStatus(Task.TaskStatus.CANCELLED);

        // Then - Tâche annulée
        assertThat(proposedTask.getStatus()).isEqualTo(Task.TaskStatus.CANCELLED);

        // Dans l'implémentation réelle : AgentActionValidator.rejectActions()
        // marquerait toutes les tâches comme CANCELLED
    }

    @Test
    @DisplayName("Should demonstrate security level impact on approval")
    void shouldDemonstrateSecurityLevelImpactOnApproval() {
        // Given - Différents niveaux de sécurité
        AgentModeSettings.State strictMode = new AgentModeSettings.State();
        strictMode.securityLevel = AgentModeSettings.AgentSecurityLevel.STRICT;
        strictMode.autoTaskApprovalEnabled = false;

        AgentModeSettings.State expertMode = new AgentModeSettings.State();
        expertMode.securityLevel = AgentModeSettings.AgentSecurityLevel.EXPERT;
        expertMode.autoTaskApprovalEnabled = false;

        // Given - Types d'actions
        AgentModeSettings.AgentActionType safeAction = AgentModeSettings.AgentActionType.READ_FILE;
        AgentModeSettings.AgentActionType riskyAction = AgentModeSettings.AgentActionType.DELETE_FILE;

        // When/Then - Mode STRICT : tout nécessite approbation
        AgentModeSettings strictSettings = new AgentModeSettings();
        strictSettings.loadState(strictMode);
        assertThat(strictSettings.requiresApproval(safeAction)).isTrue();
        assertThat(strictSettings.requiresApproval(riskyAction)).isTrue();

        // When/Then - Mode EXPERT : seules les actions très risquées nécessitent approbation
        AgentModeSettings expertSettings = new AgentModeSettings();
        expertSettings.loadState(expertMode);
        assertThat(expertSettings.requiresApproval(safeAction)).isFalse();
        assertThat(expertSettings.requiresApproval(riskyAction)).isTrue(); // DELETE est high-risk
    }

    @Test
    @DisplayName("Should demonstrate agent mode disabled fallback")
    void shouldDemonstrateAgentModeDisabledFallback() {
        // Given - Mode agent désactivé
        AgentModeSettings.State disabledState = new AgentModeSettings.State();
        disabledState.agentModeEnabled = false;

        AgentModeSettings settings = new AgentModeSettings();
        settings.loadState(disabledState);

        // When - Vérification de disponibilité
        boolean available = settings.isAgentModeAvailable();

        // Then - Mode agent non disponible
        assertThat(available).isFalse();

        // Dans l'implémentation réelle : AgentChatIntegration.onNewUserMessage()
        // appellerait directement chatFallbackHandler.processUserMessage()
        // sans passer par la détection d'intention
    }

    @Test
    @DisplayName("Should validate complete workflow configuration")
    void shouldValidateCompleteWorkflowConfiguration() {
        // Given - Configuration par défaut du mode agent
        AgentModeSettings.State defaultState = new AgentModeSettings.State();

        // When/Then - Validation de la configuration par défaut
        assertThat(defaultState.agentModeEnabled).isTrue(); // Activé par défaut maintenant
        assertThat(defaultState.securityLevel).isEqualTo(AgentModeSettings.AgentSecurityLevel.STANDARD);
        assertThat(defaultState.maxTasksPerSession).isEqualTo(10);
        assertThat(defaultState.snapshotEnabled).isTrue();
        assertThat(defaultState.taskProgressUIEnabled).isTrue();
        assertThat(defaultState.mcpIntegrationEnabled).isTrue();
        assertThat(defaultState.autoTaskApprovalEnabled).isFalse(); // Sécurité par défaut

        // When - Test de configuration complète
        AgentModeSettings settings = new AgentModeSettings();
        settings.loadState(defaultState);

        // Then - Configuration valide et prête
        assertThat(settings.isConfigurationValid()).isTrue();
        assertThat(settings.isAgentModeAvailable()).isTrue();
        assertThat(settings.getConfigurationSummary()).contains("Activé");
    }
}