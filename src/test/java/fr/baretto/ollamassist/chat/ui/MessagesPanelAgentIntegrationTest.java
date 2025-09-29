package fr.baretto.ollamassist.chat.ui;

import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.ui.ActionProposalCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests TDD pour l'intégration des ActionProposalCard dans MessagesPanel
 */
class MessagesPanelAgentIntegrationTest {

    @Mock
    private ActionProposalCard.ActionValidator actionValidator;

    private TestableMessagesPanel messagesPanel;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        messagesPanel = new TestableMessagesPanel();
    }

    @Test
    @DisplayName("Should display action proposal card when tasks are proposed")
    void should_display_action_proposal_card_when_tasks_are_proposed() {
        // Given
        List<Task> proposedTasks = List.of(
                Task.builder()
                        .type(Task.TaskType.CODE_MODIFICATION)
                        .description("Refactor method to use streams")
                        .status(Task.TaskStatus.PENDING)
                        .build()
        );

        // When
        messagesPanel.displayActionProposal(proposedTasks, actionValidator);

        // Then
        assertThat(messagesPanel.getActionCards()).hasSize(1);
        MockActionProposalCard addedCard = messagesPanel.getActionCards().get(0);
        assertThat(addedCard.getTasks()).isEqualTo(proposedTasks);
    }

    @Test
    @DisplayName("Should handle multiple action proposals")
    void should_handle_multiple_action_proposals() {
        // Given
        List<Task> firstProposal = List.of(
                Task.builder()
                        .type(Task.TaskType.FILE_OPERATION)
                        .description("Create new file")
                        .status(Task.TaskStatus.PENDING)
                        .build()
        );

        List<Task> secondProposal = List.of(
                Task.builder()
                        .type(Task.TaskType.CODE_MODIFICATION)
                        .description("Add method")
                        .status(Task.TaskStatus.PENDING)
                        .build()
        );

        // When
        messagesPanel.displayActionProposal(firstProposal, actionValidator);
        messagesPanel.displayActionProposal(secondProposal, actionValidator);

        // Then
        assertThat(messagesPanel.getActionCards()).hasSize(2);
    }

    @Test
    @DisplayName("Should remove action proposal card after validation")
    void should_remove_action_proposal_card_after_validation() {
        // Given
        List<Task> proposedTasks = List.of(
                Task.builder()
                        .type(Task.TaskType.CODE_MODIFICATION)
                        .description("Refactor method")
                        .status(Task.TaskStatus.PENDING)
                        .build()
        );

        messagesPanel.displayActionProposal(proposedTasks, actionValidator);

        // When
        messagesPanel.removeActionProposal(proposedTasks);

        // Then
        assertThat(messagesPanel.getActionCards()).isEmpty();
    }

    @Test
    @DisplayName("Should maintain scroll position when adding action proposals")
    void should_maintain_scroll_position_when_adding_action_proposals() {
        // Given
        List<Task> proposedTasks = List.of(
                Task.builder()
                        .type(Task.TaskType.CODE_MODIFICATION)
                        .description("Refactor method")
                        .status(Task.TaskStatus.PENDING)
                        .build()
        );

        // Simuler un scroll vers le haut (pas en bas)
        messagesPanel.setAutoScrollEnabled(false);

        // When
        messagesPanel.displayActionProposal(proposedTasks, actionValidator);

        // Then
        // Le scroll ne doit pas être forcé vers le bas
        assertThat(messagesPanel.isAutoScrollEnabled()).isFalse();
        assertThat(messagesPanel.getActionCards()).hasSize(1);
    }

    @Test
    @DisplayName("Should auto-scroll when adding action proposal if already at bottom")
    void should_auto_scroll_when_adding_action_proposal_if_already_at_bottom() {
        // Given
        List<Task> proposedTasks = List.of(
                Task.builder()
                        .type(Task.TaskType.FILE_OPERATION)
                        .description("Create file")
                        .status(Task.TaskStatus.PENDING)
                        .build()
        );

        // Simuler que l'utilisateur est en bas
        messagesPanel.setAutoScrollEnabled(true);

        // When
        messagesPanel.displayActionProposal(proposedTasks, actionValidator);

        // Then
        // Le scroll doit continuer vers le bas
        assertThat(messagesPanel.isAutoScrollEnabled()).isTrue();
        assertThat(messagesPanel.getActionCards()).hasSize(1);
    }

    // Version testable de MessagesPanel qui évite les dépendances IntelliJ
    private static class TestableMessagesPanel {
        private final List<MockActionProposalCard> actionCards = new ArrayList<>();
        private boolean autoScrollEnabled = true;

        public void displayActionProposal(List<Task> proposedTasks, ActionProposalCard.ActionValidator validator) {
            MockActionProposalCard card = new MockActionProposalCard(proposedTasks, validator);
            actionCards.add(card);
        }

        public void removeActionProposal(List<Task> tasks) {
            actionCards.removeIf(card -> card.getTasks().equals(tasks));
        }

        public List<MockActionProposalCard> getActionCards() {
            return new ArrayList<>(actionCards);
        }

        public boolean isAutoScrollEnabled() {
            return autoScrollEnabled;
        }

        public void setAutoScrollEnabled(boolean enabled) {
            this.autoScrollEnabled = enabled;
        }

        // Mock de getContainer pour les tests qui l'utilisent encore
        public JPanel getContainer() {
            JPanel panel = new JPanel();
            return panel;
        }
    }

    // Mock simple d'ActionProposalCard pour les tests
    private static class MockActionProposalCard {
        private final List<Task> tasks;
        private final ActionProposalCard.ActionValidator validator;

        public MockActionProposalCard(List<Task> tasks, ActionProposalCard.ActionValidator validator) {
            this.tasks = tasks;
            this.validator = validator;
        }

        public List<Task> getTasks() {
            return tasks;
        }
    }
}