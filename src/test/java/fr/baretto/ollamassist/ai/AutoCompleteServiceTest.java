package fr.baretto.ollamassist.ai;

import fr.baretto.ollamassist.ai.autocomplete.AutoCompleteService;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class AutoCompleteServiceTest {

    private static GenericContainer<?> ollama;
    private static AutoCompleteService autoCompleteService;

    @BeforeAll
    static void setup() throws IOException, InterruptedException {
        ollama = new GenericContainer<>("ollama/ollama:latest")
                .withExposedPorts(11434)
                .withCommand("serve");

        ollama.start();
        String ollamaHost = ollama.getHost();
        Integer ollamaPort = ollama.getMappedPort(11434);

        assertTrue(ollama.isRunning(), "The Ollama container did not start correctly.");

        var pullResult = ollama.execInContainer("ollama", "pull", "llama3.2:1b");
        assertEquals(0, pullResult.getExitCode(), "The model download failed: " + pullResult.getStderr());

        autoCompleteService = new AutoCompleteService(
                "http://" + ollamaHost + ":" + ollamaPort,
                "llama3.2:1b",
                0.2, 30, 0.7
        );
    }

    @AfterAll
    static void tearDown() {
        if (ollama != null) {
            ollama.stop();
        }
    }

    @Test
    void basic_autocomplete_test() {
        assertNotNull(autoCompleteService, "The AutoCompleteService has not been initialized correctly.");

        String response = autoCompleteService.getService()
                .complete("public List<String> naturalSortStrings(List<String> strings){", ".java");

        assertNotNull(response, "The response must not be null.");
        assertFalse(response.isEmpty(), "The response must not be empty.");
    }
}