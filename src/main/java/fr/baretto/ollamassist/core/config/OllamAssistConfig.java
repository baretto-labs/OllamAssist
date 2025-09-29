package fr.baretto.ollamassist.core.config;

import com.intellij.openapi.components.Service;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

/**
 * Centralized configuration for OllamAssist plugin.
 * Replaces scattered configuration settings with a single, testable configuration class.
 * IntelliJ Application Service - singleton configuration across the entire application.
 */
@Getter
@Setter
@Service
public final class OllamAssistConfig {

    private static final String DEFAULT_OLLAMA_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "codegemma";
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_AGENT_TIMEOUT = Duration.ofMinutes(5);
    private static final int DEFAULT_MAX_HISTORY = 100;
    private static final int DEFAULT_MAX_RETRIES = 3;

    private String ollamaBaseUrl = DEFAULT_OLLAMA_URL;
    private String defaultModel = DEFAULT_MODEL;
    private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
    private boolean debugMode = false;
    private int maxHistorySize = DEFAULT_MAX_HISTORY;
    private boolean agentModeEnabled = false;
    private Duration agentModeTimeout = DEFAULT_AGENT_TIMEOUT;
    private int maxAgentRetries = DEFAULT_MAX_RETRIES;

    public void setOllamaBaseUrl(@NotNull String ollamaBaseUrl) {
        validateUrl(ollamaBaseUrl);
        this.ollamaBaseUrl = ollamaBaseUrl;
    }

    public void setDefaultModel(@NotNull String defaultModel) {
        validateNotEmpty(defaultModel, "Model name cannot be empty");
        this.defaultModel = defaultModel;
    }

    public void setRequestTimeout(@NotNull Duration requestTimeout) {
        validatePositiveDuration(requestTimeout, "Request timeout must be positive");
        this.requestTimeout = requestTimeout;
    }

    public void setMaxHistorySize(int maxHistorySize) {
        validatePositive(maxHistorySize, "Max history size must be positive");
        this.maxHistorySize = maxHistorySize;
    }

    public void setAgentModeTimeout(@NotNull Duration agentModeTimeout) {
        validatePositiveDuration(agentModeTimeout, "Agent mode timeout must be positive");
        this.agentModeTimeout = agentModeTimeout;
    }

    public void setMaxAgentRetries(int maxAgentRetries) {
        validatePositive(maxAgentRetries, "Max agent retries must be positive");
        this.maxAgentRetries = maxAgentRetries;
    }

    private void validateUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL format: " + url, e);
        }
    }

    private void validateNotEmpty(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    private void validatePositiveDuration(Duration duration, String message) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(message);
        }
    }

    private void validatePositive(int value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}