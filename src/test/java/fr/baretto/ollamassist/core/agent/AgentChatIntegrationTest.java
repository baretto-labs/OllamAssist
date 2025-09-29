package fr.baretto.ollamassist.core.agent;

import fr.baretto.ollamassist.core.agent.intention.IntentionDetector;
import fr.baretto.ollamassist.core.agent.intention.UserIntention;
import fr.baretto.ollamassist.core.agent.task.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests simplifiés pour l'intégration Agent/Chat - Test des intentions sans dépendances IntelliJ
 */
class AgentChatIntegrationTest {

    private IntentionDetector intentionDetector;

    @BeforeEach
    void setUp() {
        intentionDetector = new IntentionDetector();
    }

    @Test
    @DisplayName("Should detect action intention correctly")
    void should_detect_action_intention_correctly() {
        // Given
        String actionMessage = "Refactor this method to use streams";

        // When
        UserIntention intention = intentionDetector.detectIntention(actionMessage);

        // Then
        assertThat(intention.getType()).isEqualTo(UserIntention.Type.ACTION);
        assertThat(intention.getActionType()).isEqualTo(UserIntention.ActionType.CODE_MODIFICATION);
        assertThat(intention.getConfidence()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Should detect question intention correctly")
    void should_detect_question_intention_correctly() {
        // Given
        String questionMessage = "What is the difference between ArrayList and LinkedList?";

        // When
        UserIntention intention = intentionDetector.detectIntention(questionMessage);

        // Then
        assertThat(intention.getType()).isEqualTo(UserIntention.Type.QUESTION);
        assertThat(intention.getConfidence()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Should detect file operation action correctly")
    void should_detect_file_operation_action_correctly() {
        // Given
        String fileOperationMessage = "Create a new service class and add unit tests";

        // When
        UserIntention intention = intentionDetector.detectIntention(fileOperationMessage);

        // Then
        assertThat(intention.getType()).isEqualTo(UserIntention.Type.ACTION);
        assertThat(intention.getActionType()).isEqualTo(UserIntention.ActionType.FILE_OPERATION);
        assertThat(intention.getConfidence()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Should handle complex requests appropriately")
    void should_handle_complex_requests_appropriately() {
        // Given
        String complexMessage = "Delete this file and create a backup";

        // When
        UserIntention intention = intentionDetector.detectIntention(complexMessage);

        // Then
        assertThat(intention.getType()).isEqualTo(UserIntention.Type.ACTION);
        assertThat(intention.getActionType()).isIn(
                UserIntention.ActionType.FILE_OPERATION,
                UserIntention.ActionType.CODE_MODIFICATION
        );
        assertThat(intention.getConfidence()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Should create valid task structure")
    void should_create_valid_task_structure() {
        // Given/When
        Task task = Task.builder()
                .id("test-task-001")
                .type(Task.TaskType.FILE_OPERATION)
                .description("Create new service class")
                .status(Task.TaskStatus.PENDING)
                .build();

        // Then
        assertThat(task.getType()).isEqualTo(Task.TaskType.FILE_OPERATION);
        assertThat(task.getDescription()).isEqualTo("Create new service class");
        assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.PENDING);
        assertThat(task.getId()).isEqualTo("test-task-001");
    }
}