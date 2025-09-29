package fr.baretto.ollamassist;

import fr.baretto.ollamassist.core.agent.intention.IntentionDetector;
import fr.baretto.ollamassist.core.agent.intention.UserIntention;
import fr.baretto.ollamassist.setting.agent.AgentModeSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de vérification du routage des messages vers l'agent
 */
@DisplayName("Agent Routing Tests")
class AgentRoutingTest {

    private final IntentionDetector intentionDetector = new IntentionDetector();

    @Test
    @DisplayName("Should detect questions correctly")
    void shouldDetectQuestionsCorrectly() {
        // Given - Messages de type question
        String question1 = "What is the purpose of this class?";
        String question2 = "How does this method work?";
        String question3 = "Explain the difference between these approaches";
        String question4 = "Can you help me understand this code?";

        // When - Détection d'intention
        UserIntention intention1 = intentionDetector.detectIntention(question1);
        UserIntention intention2 = intentionDetector.detectIntention(question2);
        UserIntention intention3 = intentionDetector.detectIntention(question3);
        UserIntention intention4 = intentionDetector.detectIntention(question4);

        // Then - Toutes doivent être détectées comme des questions
        assertThat(intention1.getType()).isEqualTo(UserIntention.Type.QUESTION);
        assertThat(intention2.getType()).isEqualTo(UserIntention.Type.QUESTION);
        assertThat(intention3.getType()).isEqualTo(UserIntention.Type.QUESTION);
        assertThat(intention4.getType()).isEqualTo(UserIntention.Type.QUESTION);
    }

    @Test
    @DisplayName("Should detect actions correctly")
    void shouldDetectActionsCorrectly() {
        // Given - Messages de type action
        String action1 = "Create a new test file for this class";
        String action2 = "Refactor this method to improve performance";
        String action3 = "Add a method to handle user input";
        String action4 = "Fix the bug in the authentication logic";
        String action5 = "Generate unit tests for this service";

        // When - Détection d'intention
        UserIntention intention1 = intentionDetector.detectIntention(action1);
        UserIntention intention2 = intentionDetector.detectIntention(action2);
        UserIntention intention3 = intentionDetector.detectIntention(action3);
        UserIntention intention4 = intentionDetector.detectIntention(action4);
        UserIntention intention5 = intentionDetector.detectIntention(action5);

        // Then - Toutes doivent être détectées comme des actions
        assertThat(intention1.getType()).isEqualTo(UserIntention.Type.ACTION);
        assertThat(intention2.getType()).isEqualTo(UserIntention.Type.ACTION);
        assertThat(intention3.getType()).isEqualTo(UserIntention.Type.ACTION);
        assertThat(intention4.getType()).isEqualTo(UserIntention.Type.ACTION);
        assertThat(intention5.getType()).isEqualTo(UserIntention.Type.ACTION);

        // Vérifier les types d'actions spécifiques
        assertThat(intention1.getActionType()).isEqualTo(UserIntention.ActionType.FILE_OPERATION);
        assertThat(intention2.getActionType()).isEqualTo(UserIntention.ActionType.CODE_MODIFICATION);
        assertThat(intention3.getActionType()).isEqualTo(UserIntention.ActionType.CODE_MODIFICATION);
        assertThat(intention4.getActionType()).isEqualTo(UserIntention.ActionType.CODE_MODIFICATION);
        assertThat(intention5.getActionType()).isEqualTo(UserIntention.ActionType.FILE_OPERATION); // "generate" + "test" -> FILE_OPERATION
    }

    @Test
    @DisplayName("Should detect action types correctly")
    void shouldDetectActionTypesCorrectly() {
        // Given - Actions spécifiques par type
        String fileAction = "Delete the unused configuration file";
        String gitAction = "Commit these changes to the repository";
        String buildAction = "Run the test suite";
        String codeAction = "Optimize this algorithm";

        // When - Détection d'intention
        UserIntention fileIntention = intentionDetector.detectIntention(fileAction);
        UserIntention gitIntention = intentionDetector.detectIntention(gitAction);
        UserIntention buildIntention = intentionDetector.detectIntention(buildAction);
        UserIntention codeIntention = intentionDetector.detectIntention(codeAction);

        // Then - Types d'actions correctement identifiés
        assertThat(fileIntention.getActionType()).isEqualTo(UserIntention.ActionType.FILE_OPERATION);
        assertThat(gitIntention.getActionType()).isEqualTo(UserIntention.ActionType.GIT_OPERATION);
        assertThat(buildIntention.getActionType()).isEqualTo(UserIntention.ActionType.BUILD_OPERATION);
        assertThat(codeIntention.getActionType()).isEqualTo(UserIntention.ActionType.CODE_MODIFICATION);
    }

    @Test
    @DisplayName("Should handle ambiguous messages safely")
    void shouldHandleAmbiguousMessagesSafely() {
        // Given - Messages ambigus
        String ambiguous1 = "Maybe we should change this";
        String ambiguous2 = "I think this could be better";
        String ambiguous3 = "Perhaps optimize this?";

        // When - Détection d'intention
        UserIntention intention1 = intentionDetector.detectIntention(ambiguous1);
        UserIntention intention2 = intentionDetector.detectIntention(ambiguous2);
        UserIntention intention3 = intentionDetector.detectIntention(ambiguous3);

        // Then - Messages ambigus traités comme questions (sécurité)
        // Ou si détectés comme actions, confiance faible
        if (intention1.getType() == UserIntention.Type.ACTION) {
            assertThat(intention1.getConfidence()).isLessThan(0.6);
        }
        if (intention2.getType() == UserIntention.Type.ACTION) {
            assertThat(intention2.getConfidence()).isLessThan(0.6);
        }
        // intention3 finit par "?" donc devrait être question
        assertThat(intention3.getType()).isEqualTo(UserIntention.Type.QUESTION);
    }

    @Test
    @DisplayName("Should verify agent mode settings work correctly")
    void shouldVerifyAgentModeSettingsWorkCorrectly() {
        // Given - Paramètres du mode agent
        AgentModeSettings.State state = new AgentModeSettings.State();

        // When - Mode agent activé (par défaut maintenant)
        // Then - Configuration doit être valide
        assertThat(state.agentModeEnabled).isTrue();
        assertThat(state.securityLevel).isEqualTo(AgentModeSettings.AgentSecurityLevel.STANDARD);
        assertThat(state.maxTasksPerSession).isEqualTo(10);
        assertThat(state.snapshotEnabled).isTrue();
        assertThat(state.taskProgressUIEnabled).isTrue();
    }

    @Test
    @DisplayName("Should verify security level logic works")
    void shouldVerifySecurityLevelLogicWorks() {
        // Given - Types d'actions avec différents niveaux de risque
        AgentModeSettings.AgentActionType safeAction = AgentModeSettings.AgentActionType.READ_FILE;
        AgentModeSettings.AgentActionType riskyAction = AgentModeSettings.AgentActionType.WRITE_FILE;
        AgentModeSettings.AgentActionType dangerousAction = AgentModeSettings.AgentActionType.DELETE_FILE;

        // When/Then - Vérifier les niveaux de risque
        assertThat(safeAction.isRisky()).isFalse();
        assertThat(safeAction.isHighRisk()).isFalse();

        assertThat(riskyAction.isRisky()).isTrue();
        assertThat(riskyAction.isHighRisk()).isFalse();

        assertThat(dangerousAction.isRisky()).isTrue();
        assertThat(dangerousAction.isHighRisk()).isTrue();
    }
}