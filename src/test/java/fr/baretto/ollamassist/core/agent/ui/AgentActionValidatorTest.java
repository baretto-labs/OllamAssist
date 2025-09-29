package fr.baretto.ollamassist.core.agent.ui;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.core.agent.AgentCoordinator;
import fr.baretto.ollamassist.core.agent.task.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.mockito.Mockito.*;

@DisplayName("AgentActionValidator Tests")
class AgentActionValidatorTest {

    @Mock
    private Project project;

    @Mock
    private AgentCoordinator agentCoordinator;

    @Mock
    private Task task1;

    @Mock
    private Task task2;

    private AgentActionValidator validator;
    private List<Task> tasks;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validator = new AgentActionValidator(project, agentCoordinator);
        tasks = List.of(task1, task2);
    }

    @Test
    @DisplayName("Should approve actions and execute tasks")
    void should_approve_actions_and_execute_tasks() {
        // Act
        validator.approveActions(tasks);

        // Assert
        verify(task1).setStatus(Task.TaskStatus.PENDING);
        verify(task2).setStatus(Task.TaskStatus.PENDING);
        verify(agentCoordinator).executeTasksWithoutValidation(tasks);
    }

    @Test
    @DisplayName("Should reject actions and mark tasks as cancelled")
    void should_reject_actions_and_mark_tasks_as_cancelled() {
        // Act
        validator.rejectActions(tasks);

        // Assert
        verify(task1).setStatus(Task.TaskStatus.CANCELLED);
        verify(task2).setStatus(Task.TaskStatus.CANCELLED);
        verifyNoInteractions(agentCoordinator);
    }

    @Test
    @DisplayName("Should modify actions and mark tasks for modification")
    void should_modify_actions_and_mark_tasks_for_modification() {
        // Act
        validator.modifyActions(tasks);

        // Assert
        verify(task1).setStatus(Task.TaskStatus.PENDING);
        verify(task2).setStatus(Task.TaskStatus.PENDING);
        verifyNoInteractions(agentCoordinator);
    }

    @Test
    @DisplayName("Should handle empty task list gracefully")
    void should_handle_empty_task_list_gracefully() {
        List<Task> emptyTasks = List.of();

        // Act & Assert - Should not throw exceptions
        validator.approveActions(emptyTasks);
        validator.rejectActions(emptyTasks);
        validator.modifyActions(emptyTasks);
    }

    @Test
    @DisplayName("Should handle exceptions during approval gracefully")
    void should_handle_exceptions_during_approval_gracefully() {
        // Arrange
        doThrow(new RuntimeException("Test exception")).when(agentCoordinator).executeTasks(tasks);

        // Act & Assert - Should not throw exceptions
        validator.approveActions(tasks);

        // Verify partial completion
        verify(task1).setStatus(Task.TaskStatus.PENDING);
        verify(task2).setStatus(Task.TaskStatus.PENDING);
    }

    @Test
    @DisplayName("ActionProposalCard should prevent multiple clicks")
    void actionProposalCard_should_prevent_multiple_clicks() throws InterruptedException {
        // Arrange
        ActionProposalCard.ActionValidator mockValidator = mock(ActionProposalCard.ActionValidator.class);
        ActionProposalCard card = new ActionProposalCard(tasks, mockValidator);

        // Act - Simulate multiple quick clicks on approve button
        for (int i = 0; i < 5; i++) {
            card.getApproveButton().doClick();
            // Small delay to ensure synchronization
            Thread.sleep(1);
        }

        // Wait for any pending Swing EDT operations
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> {
            });
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        // Assert - Should only execute once
        verify(mockValidator, times(1)).approveActions(tasks);
    }
}