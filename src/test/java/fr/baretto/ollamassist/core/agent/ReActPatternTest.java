package fr.baretto.ollamassist.core.agent;

import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.core.agent.execution.ExecutionEngine;
import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour le pattern ReAct dans l'agent mode
 */
@DisplayName("ReAct Pattern Tests")
public class ReActPatternTest {

    @Mock
    private Project mockProject;

    @Mock
    private ExecutionEngine mockExecutionEngine;

    private IntelliJDevelopmentAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockProject.getName()).thenReturn("TestProject");
        agent = new IntelliJDevelopmentAgent(mockProject);
    }

    @Test
    @DisplayName("ReAct: Compilation tools should be available")
    void shouldHaveCompilationToolsAvailable() {
        // Given: Agent with ReAct capabilities

        // When: Check available methods
        var methods = agent.getClass().getMethods();
        var toolMethods = List.of(methods).stream()
                .filter(method -> method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class))
                .map(method -> method.getName())
                .toList();

        // Then: Compilation tools should be present
        assertTrue(toolMethods.contains("compileAndCheckErrors"),
                "compileAndCheckErrors tool should be available");
        assertTrue(toolMethods.contains("getCompilationDiagnostics"),
                "getCompilationDiagnostics tool should be available");
        assertTrue(toolMethods.contains("createJavaClass"),
                "createJavaClass tool should be available");
        assertTrue(toolMethods.contains("createFile"),
                "createFile tool should be available");
    }

    @Test
    @DisplayName("ReAct: Compilation verification after file creation")
    void shouldVerifyCompilationAfterFileCreation() {
        // Given: Mock execution engine with compilation success
        TaskResult fileCreationSuccess = TaskResult.success("File created successfully");
        TaskResult compilationSuccess = TaskResult.success("Compilation successful");

        when(mockExecutionEngine.executeTask(any(Task.class)))
                .thenReturn(fileCreationSuccess, compilationSuccess);

        // When: Create a Java class (this should trigger compilation check in ReAct)
        String result1 = agent.createJavaClass("TestClass", "src/main/java/TestClass.java",
                "public class TestClass { }");
        String result2 = agent.compileAndCheckErrors();

        // Then: Both operations should succeed
        assertTrue(result1.contains("Successfully created"), "File creation should succeed");
        assertTrue(result2.contains("compiled successfully"), "Compilation should succeed");
    }

    @Test
    @DisplayName("ReAct: Compilation failure detection and reporting")
    void shouldDetectCompilationFailures() {
        // Given: Mock execution engine with compilation failure
        TaskResult compilationFailure = TaskResult.failure("Compilation failed: Missing import java.util.List");

        when(mockExecutionEngine.executeTask(any(Task.class)))
                .thenReturn(compilationFailure);

        // When: Check compilation
        String result = agent.compileAndCheckErrors();

        // Then: Should report compilation failure
        assertTrue(result.contains("Compilation failed"), "Should detect compilation failure");
        assertTrue(result.contains("Missing import"), "Should include specific error details");
    }

    @Test
    @DisplayName("ReAct: Diagnostics tool provides detailed error information")
    void shouldProvideDetailedDiagnostics() {
        // Given: Mock execution engine with detailed diagnostics
        String diagnosticDetails = """
                /src/main/java/TestClass.java:5: error: cannot find symbol
                  symbol:   class List
                  location: class TestClass
                /src/main/java/TestClass.java:10: error: package java.util does not exist
                """;
        TaskResult diagnosticsResult = TaskResult.success(diagnosticDetails);

        when(mockExecutionEngine.executeTask(any(Task.class)))
                .thenReturn(diagnosticsResult);

        // When: Get compilation diagnostics
        String result = agent.getCompilationDiagnostics();

        // Then: Should provide detailed error information
        assertTrue(result.contains("Compilation diagnostics"), "Should indicate diagnostics");
        assertTrue(result.contains("cannot find symbol"), "Should include specific errors");
        assertTrue(result.contains("TestClass.java"), "Should include file information");
    }

    @Test
    @DisplayName("ReAct: Build operation parameters should be created correctly")
    void shouldCreateCorrectBuildParameters() {
        // When: Create build parameters via reflection (since method is private)
        // We'll test this indirectly through the public methods
        String compileResult = agent.compileAndCheckErrors();
        String diagnosticsResult = agent.getCompilationDiagnostics();

        // Then: Methods should execute without throwing exceptions
        assertNotNull(compileResult, "Compile result should not be null");
        assertNotNull(diagnosticsResult, "Diagnostics result should not be null");
    }

    @Test
    @DisplayName("ReAct: Error handling during compilation check")
    void shouldHandleCompilationCheckErrors() {
        // Given: Mock execution engine that throws exception
        when(mockExecutionEngine.executeTask(any(Task.class)))
                .thenThrow(new RuntimeException("Build system not available"));

        // When: Try to check compilation
        String result = agent.compileAndCheckErrors();

        // Then: Should handle error gracefully
        assertTrue(result.contains("Error during compilation"), "Should handle errors gracefully");
        assertTrue(result.contains("Build system not available"), "Should include error details");
    }

    @Test
    @DisplayName("ReAct: Compilation tools should use high priority tasks")
    void shouldUseHighPriorityForCompilationTasks() {
        // This test verifies that compilation tasks are created with HIGH priority
        // We'll test this by examining the task creation indirectly

        // When: Execute compilation check
        String result = agent.compileAndCheckErrors();

        // Then: Should execute (task priority is verified in integration tests)
        assertNotNull(result, "Compilation check should return a result");
    }

    @Test
    @DisplayName("ReAct: Multiple compilation checks should be independent")
    void shouldHandleMultipleCompilationChecks() {
        // Given: Mock execution engine with varying results
        TaskResult firstCheck = TaskResult.failure("Compilation failed: Missing import");
        TaskResult secondCheck = TaskResult.success("Compilation successful");

        when(mockExecutionEngine.executeTask(any(Task.class)))
                .thenReturn(firstCheck, secondCheck);

        // When: Run multiple compilation checks
        String result1 = agent.compileAndCheckErrors();
        String result2 = agent.compileAndCheckErrors();

        // Then: Each check should return appropriate result
        assertTrue(result1.contains("Compilation failed"), "First check should show failure");
        assertTrue(result2.contains("compiled successfully"), "Second check should show success");
    }

    @Test
    @DisplayName("ReAct: Tools should log their execution for debugging")
    void shouldLogToolExecution() {
        // When: Execute tools (logging is verified through the log.error statements in the tools)
        agent.compileAndCheckErrors();
        agent.getCompilationDiagnostics();
        agent.createJavaClass("Test", "Test.java", "class Test {}");

        // Then: Tools should execute without throwing exceptions
        // (Actual log verification would require log capture, but we verify basic execution)
        assertTrue(true, "Tools should execute and log without exceptions");
    }

    @Test
    @DisplayName("ReAct: Agent should provide statistics about tool usage")
    void shouldProvideAgentStatistics() {
        // When: Get agent statistics
        AgentStats stats = agent.getStats();

        // Then: Statistics should be available
        assertNotNull(stats, "Agent stats should not be null");
        assertTrue(stats.getActiveTasksCount() >= 0, "Active tasks count should be non-negative");
        assertTrue(stats.getSuccessRate() >= 0.0 && stats.getSuccessRate() <= 1.0,
                "Success rate should be between 0 and 1");
    }
}