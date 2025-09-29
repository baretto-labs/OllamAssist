package fr.baretto.ollamassist.core;

import fr.baretto.ollamassist.core.config.OllamAssistConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OllamAssistConfig - TDD approach for centralized configuration management.
 * Tests the unified configuration system that replaces scattered settings.
 */
class OllamAssistConfigTest {

    private OllamAssistConfig config;

    @BeforeEach
    void setUp() {
        config = new OllamAssistConfig();
    }

    @Test
    @DisplayName("Should create config with default values")
    void should_create_config_with_default_values() {
        // Then
        assertNotNull(config);
        assertEquals("http://localhost:11434", config.getOllamaBaseUrl());
        assertEquals("codegemma", config.getDefaultModel());
        assertEquals(Duration.ofSeconds(30), config.getRequestTimeout());
        assertFalse(config.isDebugMode());
        assertEquals(100, config.getMaxHistorySize());
    }

    @Test
    @DisplayName("Should set and get Ollama base URL")
    void should_set_and_get_ollama_base_url() {
        // Given
        String newUrl = "http://remote-server:11434";

        // When
        config.setOllamaBaseUrl(newUrl);

        // Then
        assertEquals(newUrl, config.getOllamaBaseUrl());
    }

    @Test
    @DisplayName("Should validate Ollama URL format")
    void should_validate_ollama_url_format() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                        config.setOllamaBaseUrl("invalid-url"),
                "Should reject invalid URL format"
        );

        assertThrows(IllegalArgumentException.class, () ->
                        config.setOllamaBaseUrl(""),
                "Should reject empty URL"
        );

        assertThrows(IllegalArgumentException.class, () ->
                        config.setOllamaBaseUrl(null),
                "Should reject null URL"
        );
    }

    @Test
    @DisplayName("Should set and get default model")
    void should_set_and_get_default_model() {
        // Given
        String newModel = "llama2";

        // When
        config.setDefaultModel(newModel);

        // Then
        assertEquals(newModel, config.getDefaultModel());
    }

    @Test
    @DisplayName("Should validate model name")
    void should_validate_model_name() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                        config.setDefaultModel(""),
                "Should reject empty model name"
        );

        assertThrows(IllegalArgumentException.class, () ->
                        config.setDefaultModel(null),
                "Should reject null model name"
        );
    }

    @Test
    @DisplayName("Should set and get request timeout")
    void should_set_and_get_request_timeout() {
        // Given
        Duration newTimeout = Duration.ofMinutes(2);

        // When
        config.setRequestTimeout(newTimeout);

        // Then
        assertEquals(newTimeout, config.getRequestTimeout());
    }

    @Test
    @DisplayName("Should validate request timeout")
    void should_validate_request_timeout() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                        config.setRequestTimeout(Duration.ofSeconds(0)),
                "Should reject zero timeout"
        );

        assertThrows(IllegalArgumentException.class, () ->
                        config.setRequestTimeout(Duration.ofSeconds(-1)),
                "Should reject negative timeout"
        );

        assertThrows(IllegalArgumentException.class, () ->
                        config.setRequestTimeout(null),
                "Should reject null timeout"
        );
    }

    @Test
    @DisplayName("Should toggle debug mode")
    void should_toggle_debug_mode() {
        // Given
        assertFalse(config.isDebugMode());

        // When
        config.setDebugMode(true);

        // Then
        assertTrue(config.isDebugMode());

        // When
        config.setDebugMode(false);

        // Then
        assertFalse(config.isDebugMode());
    }

    @Test
    @DisplayName("Should set and get max history size")
    void should_set_and_get_max_history_size() {
        // Given
        int newSize = 200;

        // When
        config.setMaxHistorySize(newSize);

        // Then
        assertEquals(newSize, config.getMaxHistorySize());
    }

    @Test
    @DisplayName("Should validate max history size")
    void should_validate_max_history_size() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                        config.setMaxHistorySize(0),
                "Should reject zero history size"
        );

        assertThrows(IllegalArgumentException.class, () ->
                        config.setMaxHistorySize(-1),
                "Should reject negative history size"
        );
    }

    @Test
    @DisplayName("Should provide agent mode settings")
    void should_provide_agent_mode_settings() {
        // Then
        assertFalse(config.isAgentModeEnabled());
        assertEquals(Duration.ofMinutes(5), config.getAgentModeTimeout());
        assertEquals(3, config.getMaxAgentRetries());
    }

    @Test
    @DisplayName("Should configure agent mode settings")
    void should_configure_agent_mode_settings() {
        // When
        config.setAgentModeEnabled(true);
        config.setAgentModeTimeout(Duration.ofMinutes(10));
        config.setMaxAgentRetries(5);

        // Then
        assertTrue(config.isAgentModeEnabled());
        assertEquals(Duration.ofMinutes(10), config.getAgentModeTimeout());
        assertEquals(5, config.getMaxAgentRetries());
    }

    @Test
    @DisplayName("Should validate agent mode settings")
    void should_validate_agent_mode_settings() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                        config.setAgentModeTimeout(Duration.ofSeconds(0)),
                "Should reject zero agent timeout"
        );

        assertThrows(IllegalArgumentException.class, () ->
                        config.setMaxAgentRetries(0),
                "Should reject zero agent retries"
        );

        assertThrows(IllegalArgumentException.class, () ->
                        config.setMaxAgentRetries(-1),
                "Should reject negative agent retries"
        );
    }

    // Builder pattern test removed - now using simple service initialization
}