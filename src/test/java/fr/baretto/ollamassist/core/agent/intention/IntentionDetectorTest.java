package fr.baretto.ollamassist.core.agent.intention;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests TDD pour la détection d'intention utilisateur - différencier questions vs actions
 */
class IntentionDetectorTest {

    private IntentionDetector intentionDetector;

    @BeforeEach
    void setUp() {
        intentionDetector = new IntentionDetector();
    }

    @Test
    @DisplayName("Should detect action intention for refactoring commands")
    void should_detect_action_intention_for_refactoring_commands() {
        // Given
        String message = "Refactor this method to use streams";

        // When
        UserIntention intention = intentionDetector.detectIntention(message);

        // Then
        assertThat(intention.getType()).isEqualTo(UserIntention.Type.ACTION);
        assertThat(intention.getActionType()).isEqualTo(UserIntention.ActionType.CODE_MODIFICATION);
    }

    @Test
    @DisplayName("Should detect action intention for file operations")
    void should_detect_action_intention_for_file_operations() {
        // Given
        String message = "Create a new service class called UserService";

        // When
        UserIntention intention = intentionDetector.detectIntention(message);

        // Then
        assertThat(intention.getType()).isEqualTo(UserIntention.Type.ACTION);
        assertThat(intention.getActionType()).isEqualTo(UserIntention.ActionType.FILE_OPERATION);
    }

    @Test
    @DisplayName("Should detect action intention for git operations")
    void should_detect_action_intention_for_git_operations() {
        // Given
        String message = "Commit these changes with message 'Add new feature'";

        // When
        UserIntention intention = intentionDetector.detectIntention(message);

        // Then
        assertThat(intention.getType()).isEqualTo(UserIntention.Type.ACTION);
        assertThat(intention.getActionType()).isEqualTo(UserIntention.ActionType.GIT_OPERATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "What is the difference between ArrayList and LinkedList?",
            "How do I implement a binary search?",
            "Can you explain this code?",
            "Why is this method not working?",
            "What does this error mean?"
    })
    @DisplayName("Should detect question intention for information requests")
    void should_detect_question_intention_for_information_requests(String message) {
        // When
        UserIntention intention = intentionDetector.detectIntention(message);

        // Then
        assertThat(intention.getType()).isEqualTo(UserIntention.Type.QUESTION);
        assertThat(intention.getActionType()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Add error handling to this method",
            "Generate unit tests for this class",
            "Optimize this query",
            "Fix this bug",
            "Delete this file",
            "Rename this variable",
            "Extract this into a separate method"
    })
    @DisplayName("Should detect action intention for various code actions")
    void should_detect_action_intention_for_various_code_actions(String message) {
        // When
        UserIntention intention = intentionDetector.detectIntention(message);

        // Then
        assertThat(intention.getType()).isEqualTo(UserIntention.Type.ACTION);
        assertThat(intention.getActionType()).isIn(
                UserIntention.ActionType.CODE_MODIFICATION,
                UserIntention.ActionType.FILE_OPERATION
        );
    }

    @Test
    @DisplayName("Should detect build operation intention")
    void should_detect_build_operation_intention() {
        // Given
        String message = "Run the tests and build the project";

        // When
        UserIntention intention = intentionDetector.detectIntention(message);

        // Then
        assertThat(intention.getType()).isEqualTo(UserIntention.Type.ACTION);
        assertThat(intention.getActionType()).isEqualTo(UserIntention.ActionType.BUILD_OPERATION);
    }

    @Test
    @DisplayName("Should handle empty or null messages")
    void should_handle_empty_or_null_messages() {
        // Given/When/Then
        assertThat(intentionDetector.detectIntention(null).getType())
                .isEqualTo(UserIntention.Type.UNKNOWN);

        assertThat(intentionDetector.detectIntention("").getType())
                .isEqualTo(UserIntention.Type.UNKNOWN);

        assertThat(intentionDetector.detectIntention("   ").getType())
                .isEqualTo(UserIntention.Type.UNKNOWN);
    }

    @Test
    @DisplayName("Should handle ambiguous messages")
    void should_handle_ambiguous_messages() {
        // Given
        String message = "This code looks strange";

        // When
        UserIntention intention = intentionDetector.detectIntention(message);

        // Then
        assertThat(intention.getType()).isEqualTo(UserIntention.Type.QUESTION);
        // En cas d'ambiguïté, préférer la question pour éviter les actions non désirées
    }

    @Test
    @DisplayName("Should extract key elements from action intention")
    void should_extract_key_elements_from_action_intention() {
        // Given
        String message = "Create a new UserService class in the services package";

        // When
        UserIntention intention = intentionDetector.detectIntention(message);

        // Then
        assertThat(intention.getType()).isEqualTo(UserIntention.Type.ACTION);
        assertThat(intention.getKeyElements()).contains("UserService", "class", "services");
    }

    @Test
    @DisplayName("Should detect confidence level")
    void should_detect_confidence_level() {
        // Given
        String clearAction = "Delete file Test.java";
        String ambiguousMessage = "maybe we should change something here";

        // When
        UserIntention clearIntention = intentionDetector.detectIntention(clearAction);
        UserIntention ambiguousIntention = intentionDetector.detectIntention(ambiguousMessage);

        // Then
        assertThat(clearIntention.getConfidence()).isGreaterThan(0.8);
        assertThat(ambiguousIntention.getConfidence()).isLessThan(0.6);
    }
}