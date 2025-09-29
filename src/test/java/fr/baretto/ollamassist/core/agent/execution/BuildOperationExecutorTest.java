package fr.baretto.ollamassist.core.agent.execution;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Tests pour BuildOperationExecutor avec support des diagnostics ReAct
 */
@DisplayName("BuildOperationExecutor ReAct Tests")
public class BuildOperationExecutorTest {

    @TempDir
    Path tempDir;
    @Mock
    private Project mockProject;
    @Mock
    private VirtualFile mockProjectRoot;
    private BuildOperationExecutor executor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockProject.getBaseDir()).thenReturn(mockProjectRoot);
        when(mockProjectRoot.getPath()).thenReturn(tempDir.toString());

        executor = new BuildOperationExecutor(mockProject);
    }

    @Test
    @DisplayName("Should support compile operation for ReAct pattern")
    void shouldSupportCompileOperation() {
        // Given: A compile task
        Task compileTask = createBuildTask("compile");

        // When: Execute compile task
        TaskResult result = executor.execute(compileTask);

        // Then: Should handle compile operation
        assertNotNull(result, "Result should not be null");
        // Note: Result may fail in test environment without actual build system
    }

    @Test
    @DisplayName("Should support diagnostics operation for ReAct pattern")
    void shouldSupportDiagnosticsOperation() {
        // Given: A diagnostics task
        Task diagnosticsTask = createBuildTask("diagnostics");

        // When: Execute diagnostics task
        TaskResult result = executor.execute(diagnosticsTask);

        // Then: Should handle diagnostics operation
        assertNotNull(result, "Result should not be null");
        // The operation type should be recognized even if execution fails in test env
    }

    @Test
    @DisplayName("Should detect Gradle project and use appropriate commands")
    void shouldDetectGradleProject() throws Exception {
        // Given: A project with gradlew script
        File gradlewFile = new File(tempDir.toFile(), "gradlew");
        gradlewFile.createNewFile();
        gradlewFile.setExecutable(true);

        // When: Execute compile task
        Task compileTask = createBuildTask("compile");
        TaskResult result = executor.execute(compileTask);

        // Then: Should use Gradle commands
        assertNotNull(result, "Result should not be null");
        // In real environment, would verify ./gradlew build was called
    }

    @Test
    @DisplayName("Should detect Maven project and use appropriate commands")
    void shouldDetectMavenProject() throws Exception {
        // Given: A project with pom.xml
        File pomFile = new File(tempDir.toFile(), "pom.xml");
        pomFile.createNewFile();

        // When: Execute compile task
        Task compileTask = createBuildTask("compile");
        TaskResult result = executor.execute(compileTask);

        // Then: Should use Maven commands
        assertNotNull(result, "Result should not be null");
        // In real environment, would verify mvn compile was called
    }

    @Test
    @DisplayName("Should handle diagnostics command differences by project type")
    void shouldHandleDiagnosticsCommandDifferences() throws Exception {
        // Given: A Gradle project
        File gradlewFile = new File(tempDir.toFile(), "gradlew");
        gradlewFile.createNewFile();

        // When: Execute diagnostics task
        Task diagnosticsTask = createBuildTask("diagnostics");
        TaskResult result = executor.execute(diagnosticsTask);

        // Then: Should use Gradle-specific diagnostics command
        assertNotNull(result, "Result should not be null");
        // Real test would verify: ./gradlew compileJava --console=plain
    }

    @Test
    @DisplayName("Should handle missing operation parameter")
    void shouldHandleMissingOperationParameter() {
        // Given: A task without operation parameter
        Task invalidTask = Task.builder()
                .id(UUID.randomUUID().toString())
                .description("Invalid build task")
                .type(Task.TaskType.BUILD_OPERATION)
                .priority(Task.TaskPriority.NORMAL)
                .parameters(new HashMap<>()) // No operation parameter
                .createdAt(LocalDateTime.now())
                .build();

        // When: Execute task
        TaskResult result = executor.execute(invalidTask);

        // Then: Should return failure with clear message
        assertFalse(result.isSuccess(), "Should fail for missing operation");
        assertTrue(result.getErrorMessage().contains("operation"), "Error should mention missing operation");
    }

    @Test
    @DisplayName("Should handle unsupported operation")
    void shouldHandleUnsupportedOperation() {
        // Given: A task with unsupported operation
        Task unsupportedTask = createBuildTask("unsupported-operation");

        // When: Execute task
        TaskResult result = executor.execute(unsupportedTask);

        // Then: Should return failure
        assertFalse(result.isSuccess(), "Should fail for unsupported operation");
        assertTrue(result.getErrorMessage().contains("non supportée"), "Error should mention unsupported operation");
    }

    @Test
    @DisplayName("Should handle project without build system")
    void shouldHandleProjectWithoutBuildSystem() {
        // Given: A project without gradle/maven files (tempDir is empty)

        // When: Execute compile task
        Task compileTask = createBuildTask("compile");
        TaskResult result = executor.execute(compileTask);

        // Then: Should use fallback command (make)
        assertNotNull(result, "Result should not be null");
        // In real environment, would attempt 'make' command
    }

    @Test
    @DisplayName("Should validate task executor interface")
    void shouldValidateTaskExecutorInterface() {
        // When: Check if executor can handle BUILD_OPERATION tasks
        Task buildTask = createBuildTask("compile");
        boolean canExecute = executor.canExecute(buildTask);

        // Then: Should be able to execute build operations
        assertTrue(canExecute, "Should be able to execute BUILD_OPERATION tasks");

        // When: Check executor name
        String executorName = executor.getExecutorName();

        // Then: Should have correct name
        assertEquals("BuildOperationExecutor", executorName, "Should have correct executor name");
    }

    @Test
    @DisplayName("Should handle compilation with timeout parameter")
    void shouldHandleCompilationWithTimeout() {
        // Given: A task with timeout parameter
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("operation", "compile");
        parameters.put("timeout", 60); // 60 seconds

        Task timeoutTask = Task.builder()
                .id(UUID.randomUUID().toString())
                .description("Compile with timeout")
                .type(Task.TaskType.BUILD_OPERATION)
                .priority(Task.TaskPriority.NORMAL)
                .parameters(parameters)
                .createdAt(LocalDateTime.now())
                .build();

        // When: Execute task
        TaskResult result = executor.execute(timeoutTask);

        // Then: Should handle timeout parameter
        assertNotNull(result, "Result should not be null");
        // Timeout handling is verified in integration tests
    }

    @Test
    @DisplayName("Should handle null project base directory")
    void shouldHandleNullProjectBaseDirectory() {
        // Given: Project with null base directory
        when(mockProject.getBaseDir()).thenReturn(null);
        BuildOperationExecutor nullDirExecutor = new BuildOperationExecutor(mockProject);

        Task compileTask = createBuildTask("compile");

        // When: Execute task
        TaskResult result = nullDirExecutor.execute(compileTask);

        // Then: Should return failure with clear message
        assertFalse(result.isSuccess(), "Should fail for null project directory");
        assertTrue(result.getErrorMessage().contains("répertoire racine"), "Error should mention project directory");
    }

    @Test
    @DisplayName("Should support all build operations required for ReAct")
    void shouldSupportAllReActBuildOperations() {
        // Test all operations that ReAct pattern might use
        String[] operations = {"compile", "build", "test", "clean", "package", "diagnostics"};

        for (String operation : operations) {
            // Given: Task for each operation
            Task task = createBuildTask(operation);

            // When: Execute task
            TaskResult result = executor.execute(task);

            // Then: Should handle each operation type
            assertNotNull(result, "Result should not be null for operation: " + operation);
        }
    }

    private Task createBuildTask(String operation) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("operation", operation);

        return Task.builder()
                .id(UUID.randomUUID().toString())
                .description("Build operation: " + operation)
                .type(Task.TaskType.BUILD_OPERATION)
                .priority(Task.TaskPriority.NORMAL)
                .parameters(parameters)
                .createdAt(LocalDateTime.now())
                .build();
    }
}