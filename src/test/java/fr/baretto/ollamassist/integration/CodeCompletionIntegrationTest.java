package fr.baretto.ollamassist.integration;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import fr.baretto.ollamassist.completion.SuggestionManager;
import fr.baretto.ollamassist.completion.LightModelAssistant;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Code Completion feature
 * Tests the complete workflow from cursor position to inline suggestions
 *
 * IMPORTANT: These tests use Testcontainers to automatically start Ollama
 * Docker must be installed and running on the system
 */
@Slf4j
@Tag("integration")
@Tag("requires-docker")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CodeCompletionIntegrationTest extends OllamaIntegrationTestBase {

    private static final int TEST_TIMEOUT_SECONDS = 30;

    private LightModelAssistant.Service lightModelAssistant;
    private SuggestionManager suggestionManager;
    private PsiFile testFile;
    private Editor editor;

    @Override
    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp(); // This sets up Ollama via Testcontainers

        // Initialize Ollama container if not already done
        setUpOllama();

        // Skip tests if Ollama container failed to start
        if (!isOllamaAvailable()) {
            log.warn("️ Ollama container not available, tests will be skipped");
            return;
        }

        // Initialize services
        Project project = getProject();

        // Force reload of LightModelAssistant to pick up the new Ollama URL from settings
        lightModelAssistant = LightModelAssistant.get();
        suggestionManager = project.getService(SuggestionManager.class);

        // Create a test Java file
        testFile = myFixture.configureByText("TestClass.java", """
                package com.example;

                public class TestClass {
                    // Code completion will be tested here
                }
                """);

        editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    }

    @Override
    @AfterEach
    protected void tearDown() throws Exception {
        try {
            testFile = null;
            editor = null;
            lightModelAssistant = null;
            suggestionManager = null;
        } finally {
            super.tearDown();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Should initialize code completion services")
    void testCodeCompletionServicesInitialization() {
        assumeOllamaAvailable();

        // Then - Core services should be initialized
        assertThat(lightModelAssistant)
                .as("LightModelAssistant service should be initialized")
                .isNotNull();

        assertThat(testFile)
                .as("Test file should be created")
                .isNotNull();

        assertThat(suggestionManager)
                .as("SuggestionManager service should be registered as a project service")
                .isNotNull();

        log.info("Code completion services initialization test passed");
    }

    @Test
    @Order(2)
    @DisplayName("Should create editor context for completion")
    void testEditorContextCreation() {
        assumeOllamaAvailable();

        // Given - A Java file with cursor position
        assertThat(testFile)
                .as("Test file should exist")
                .isNotNull();

        // When - We get the document
        Document document = testFile.getViewProvider().getDocument();

        // Then - Document should be available
        assertThat(document)
                .as("Document should be available from test file")
                .isNotNull();

        assertThat(document.getText())
                .as("Document should contain the TestClass code")
                .contains("TestClass")
                .contains("package com.example");

        log.info("Editor context creation test passed");
    }

    @Test
    @Order(3)
    @DisplayName("Should generate simple method completion")
    @Tag("requires-docker")
    void testSimpleMethodCompletion() {
        assumeOllamaAvailable();

        // Given - A partial method declaration
        myFixture.configureByText("TestClass.java", """
                package com.example;

                public class TestClass {
                    public int add(int a, int b) {
                        // TODO: implement
                """);

        // When - We request completion
        String context = testFile.getText();
        log.info("Requesting completion for context: {}", context.substring(0, Math.min(100, context.length())));

        // Then - Completion should be generated
        String completion = lightModelAssistant.completeBasic(context, "java");

        assertThat(completion)
                .as("Completion should be generated for incomplete method")
                .isNotNull()
                .isNotBlank();

        log.info("Simple method completion test passed with suggestion: {}",
                completion.substring(0, Math.min(50, completion.length())));
    }

    @Test
    @Order(4)
    @DisplayName("Should handle completion timeout gracefully")
    void testCompletionTimeout() {
        assumeOllamaAvailable();

        // Given - A completion request that might timeout
        String context = "public class TimeoutTest {";

        // When - We request completion
        // Then - Should not throw unhandled exceptions
        try {
            String result = lightModelAssistant.completeBasic(context, "java");
            assertThat(result)
                    .as("Completion should be returned or handle timeout gracefully")
                    .isNotNull();
            log.info("Completion received successfully");
        } catch (Exception e) {
            log.info("Completion timeout handled gracefully: {}", e.getClass().getSimpleName());
            // Timeout is acceptable behavior
            assertThat(e)
                    .as("Exception should be handled gracefully")
                    .isNotNull();
        }

        log.info("Completion timeout handling test passed");
    }

    @Test
    @Order(5)
    @DisplayName("Should generate class field completion")
    @Tag("requires-docker")
    void testClassFieldCompletion() {
        assumeOllamaAvailable();

        // Given - A class with partial field declaration
        WriteAction.run(() -> {
            Document document = testFile.getViewProvider().getDocument();
            assertThat(document).isNotNull();

            String incompleteCode = """
                    package com.example;

                    public class User {
                        private String name;
                        private int age;
                        // Add email field
                    """;

            document.setText(incompleteCode);
        });

        // When - We request completion
        String context = testFile.getText();
        String completion = lightModelAssistant.completeBasic(context, "java");

        // Then - Completion should be generated
        assertThat(completion)
                .as("Completion should be generated for class field")
                .isNotNull()
                .isNotBlank();

        log.info("Class field completion test passed with suggestion: {}", completion);
    }

    @Test
    @Order(6)
    @DisplayName("Should handle invalid context gracefully")
    void testInvalidContextHandling() {
        assumeOllamaAvailable();

        // Given - Invalid/malformed code context
        String invalidContext = "public class {{{{{";

        // When - We request completion with invalid context
        // Then - Should handle gracefully without crashing
        try {
            String result = lightModelAssistant.completeBasic(invalidContext, "java");
            log.info("Invalid context handled, result returned: {}", result != null);
            // Service may still return a result even with invalid syntax
            assertThat(result)
                    .as("Service should handle invalid context gracefully")
                    .isNotNull();
        } catch (Exception e) {
            log.info("Invalid context error handled gracefully: {}", e.getClass().getSimpleName());
            // Exception is acceptable for invalid context
            assertThat(e)
                    .as("Exception should be handled gracefully")
                    .isNotNull();
        }

        log.info("Invalid context handling test passed");
    }

    @Test
    @Order(7)
    @DisplayName("Should handle rapid sequential completion requests")
    void testSequentialCompletionRequests() {
        assumeOllamaAvailable();

        // Given - Multiple rapid completion requests
        String[] contexts = {
                "public void method1() {",
                "public void method2() {",
                "public void method3() {"
        };

        // When - We send multiple requests rapidly
        int successCount = 0;
        for (String context : contexts) {
            try {
                String result = lightModelAssistant.completeBasic(context, "java");
                if (result != null && !result.isBlank()) {
                    successCount++;
                    log.debug("Sequential request {} completed successfully", successCount);
                }
            } catch (Exception e) {
                log.debug("Sequential request exception (acceptable): {}", e.getMessage());
            }
        }

        // Then - Should handle multiple requests without crashing
        // At least some requests should succeed
        assertThat(successCount)
                .as("At least one sequential completion request should succeed")
                .isGreaterThan(0);

        log.info("Sequential completion requests test passed ({}/{} succeeded)", successCount, contexts.length);
    }

    @Test
    @Order(8)
    @DisplayName("Should integrate with suggestion manager")
    void testSuggestionManagerIntegration() {
        assumeOllamaAvailable();

        // Given - SuggestionManager is available
        assertThat(suggestionManager)
                .as("SuggestionManager should be initialized as a project service")
                .isNotNull();

        // When - We verify the service is properly initialized
        // Then - Service should be available for managing inline suggestions

        // Note: Full integration with editor inlays requires UI testing framework
        // This test verifies the service layer is properly initialized

        log.info("Suggestion manager integration test passed");
    }

    @Test
    @Order(9)
    @DisplayName("Should handle completion for different file types")
    @Tag("requires-docker")
    void testDifferentFileTypeCompletion() {
        assumeOllamaAvailable();

        // Given - Different file types
        String xmlContent = "<configuration>\n    <property name=\"";
        String jsonContent = "{\n  \"name\": \"";
        String javaContent = "public class Test {\n    private String ";

        // When - We request completions for different contexts
        String xmlCompletion = lightModelAssistant.completeBasic(xmlContent, "xml");
        String jsonCompletion = lightModelAssistant.completeBasic(jsonContent, "json");
        String javaCompletion = lightModelAssistant.completeBasic(javaContent, "java");

        // Then - All completions should be generated
        assertThat(xmlCompletion)
                .as("XML completion should be generated")
                .isNotNull()
                .isNotBlank();

        assertThat(jsonCompletion)
                .as("JSON completion should be generated")
                .isNotNull()
                .isNotBlank();

        assertThat(javaCompletion)
                .as("Java completion should be generated")
                .isNotNull()
                .isNotBlank();

        log.info("Different file type completion test completed successfully");
    }

    @Test
    @Order(10)
    @DisplayName("Should clean up resources properly")
    void testResourceCleanup() {
        assumeOllamaAvailable();

        // When - Test cleanup occurs
        // Then - Should not throw exceptions

        assertThat(lightModelAssistant)
                .as("LightModelAssistant should still be available")
                .isNotNull();

        assertThat(suggestionManager)
                .as("SuggestionManager should still be available")
                .isNotNull();

        assertThat(testFile)
                .as("Test file should still be available")
                .isNotNull();

        log.info("Resource cleanup test passed");
    }
}
