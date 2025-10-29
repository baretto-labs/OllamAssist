package fr.baretto.ollamassist.integration;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Base class for integration tests that require Ollama.
 * Uses Testcontainers to spin up an Ollama instance automatically.
 *
 * NOTE: Due to BasePlatformTestCase constraints, container is started lazily
 * on first test execution rather than using @BeforeAll
 */
@Slf4j
public abstract class OllamaIntegrationTestBase extends BasePlatformTestCase {

    protected static GenericContainer<?> ollamaContainer;
    protected static String ollamaUrl;
    protected static boolean ollamaAvailable = false;
    protected static boolean ollamaInitialized = false;

    protected static synchronized void setUpOllama() {
        if (ollamaInitialized) {
            return;
        }
        try {
            log.info("Starting Ollama container with Testcontainers...");

            ollamaContainer = new GenericContainer<>(DockerImageName.parse("ollama/ollama:latest"))
                    .withExposedPorts(11434)
                    .waitingFor(Wait.forHttp("/api/tags")
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(2)));

            ollamaContainer.start();

            String host = ollamaContainer.getHost();
            Integer port = ollamaContainer.getMappedPort(11434);
            ollamaUrl = String.format("http://%s:%d", host, port);

            log.info("Ollama container started successfully at: {}", ollamaUrl);

            // Pull a lightweight model for testing
            pullModel("tinyllama");

            ollamaAvailable = true;
            ollamaInitialized = true;

        } catch (Exception e) {
            log.error("Failed to start Ollama container: {}", e.getMessage());
            log.warn("️ Integration tests requiring Ollama will be skipped");
            ollamaAvailable = false;
        }
    }

    @AfterAll
    public static void tearDownOllama() {
        if (ollamaContainer != null && ollamaContainer.isRunning()) {
            log.info("Stopping Ollama container...");
            ollamaContainer.stop();
            log.info("Ollama container stopped");
        }
    }

    @Override
    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();

        if (ollamaAvailable) {
            // Configure Ollama settings to use the container
            OllamAssistSettings settings = OllamAssistSettings.getInstance();
            settings.setCompletionOllamaUrl(ollamaUrl);
            settings.setChatOllamaUrl(ollamaUrl);

            log.debug("Configured Ollama URL: {}", ollamaUrl);
        }
    }

    @Override
    @AfterEach
    protected void tearDown() throws Exception {
        try {
            // Cleanup test-specific resources
        } finally {
            super.tearDown();
        }
    }

    /**
     * Pull a model from Ollama library
     * Uses tinyllama by default for fast testing
     */
    private static void pullModel(String modelName) {
        if (!ollamaAvailable) {
            return;
        }

        try {
            log.info("Pulling model '{}' (this may take a few minutes on first run)...", modelName);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMinutes(5))
                    .build();

            String requestBody = String.format("{\"name\": \"%s\", \"stream\": false}", modelName);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaUrl + "/api/pull"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofMinutes(5))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("Model '{}' pulled successfully", modelName);
            } else {
                log.warn("️ Failed to pull model '{}': HTTP {}", modelName, response.statusCode());
            }

        } catch (IOException | InterruptedException e) {
            log.warn("️ Could not pull model '{}': {}", modelName, e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Check if Ollama is available for testing
     */
    protected boolean isOllamaAvailable() {
        return ollamaAvailable;
    }

    /**
     * Get the Ollama URL for the running container
     */
    protected String getOllamaUrl() {
        return ollamaUrl;
    }

    /**
     * Skip test if Ollama is not available
     */
    protected void assumeOllamaAvailable() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
            ollamaAvailable,
            "Ollama container is not available - test skipped"
        );
    }
}
