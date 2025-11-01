package fr.baretto.ollamassist.core.agent;

import dev.langchain4j.model.ollama.OllamaModels;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import fr.baretto.ollamassist.setting.agent.AgentModeSettings;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;

/**
 * Checks availability of Ollama models
 * Specifically checks for agent model (gpt-oss) availability
 */
@Slf4j
public class ModelAvailabilityChecker {

    private final String ollamaUrl;
    private final Duration timeout;

    public ModelAvailabilityChecker() {
        AgentModeSettings agentSettings = AgentModeSettings.getInstance();

        // Use agent-specific Ollama URL if configured, otherwise use default
        this.ollamaUrl = agentSettings.getAgentOllamaUrl() != null
                ? agentSettings.getAgentOllamaUrl()
                : OllamAssistSettings.getInstance().getCompletionOllamaUrl();

        this.timeout = OllamAssistSettings.getInstance().getTimeoutDuration();
    }

    public ModelAvailabilityChecker(String ollamaUrl, Duration timeout) {
        this.ollamaUrl = ollamaUrl;
        this.timeout = timeout;
    }

    /**
     * Checks if the agent model (gpt-oss) is available
     */
    public ModelAvailabilityResult checkAgentModelAvailability() {
        String agentModel = AgentModeSettings.getInstance().getAgentModelName();

        if (agentModel == null || agentModel.trim().isEmpty()) {
            return ModelAvailabilityResult.notConfigured();
        }

        return checkModelAvailability(agentModel);
    }

    /**
     * Checks if a specific model is available in Ollama
     */
    public ModelAvailabilityResult checkModelAvailability(String modelName) {
        log.debug("Checking availability of model '{}' at {}", modelName, ollamaUrl);

        try {
            OllamaModels ollamaModels = OllamaModels.builder()
                    .baseUrl(ollamaUrl)
                    .timeout(timeout)
                    .build();

            log.debug("Fetching available models from Ollama...");
            List<dev.langchain4j.model.ollama.OllamaModel> availableModels = ollamaModels.availableModels()
                    .content();

            List<String> availableModelNames = availableModels.stream()
                    .map(dev.langchain4j.model.ollama.OllamaModel::getName)
                    .toList();
            log.debug("Available models: {}", availableModelNames);

            // FIX: Accept both exact match and tag-based match (e.g., 'gpt-oss' matches 'gpt-oss:20b')
            boolean isAvailable = availableModels.stream()
                    .anyMatch(model -> {
                        String availableName = model.getName();
                        // Exact match: gpt-oss:20b == gpt-oss:20b
                        if (availableName.equals(modelName)) {
                            return true;
                        }
                        // Tag match: gpt-oss matches gpt-oss:20b or gpt-oss:latest
                        if (availableName.startsWith(modelName + ":")) {
                            return true;
                        }
                        // Base name match: gpt-oss:20b matches gpt-oss
                        if (modelName.contains(":") && availableName.equals(modelName.split(":")[0])) {
                            return true;
                        }
                        return false;
                    });

            if (isAvailable) {
                log.info("Model '{}' is available (found in available models: {})", modelName, availableModelNames);
                return ModelAvailabilityResult.available(modelName);
            } else {
                log.warn("Model '{}' is NOT available. Available models: {}", modelName, availableModelNames);
                return ModelAvailabilityResult.notAvailable(modelName, availableModelNames);
            }

        } catch (Exception e) {
            log.error("Error checking model availability for '{}'", modelName, e);
            return ModelAvailabilityResult.error(modelName, e.getMessage());
        }
    }

    /**
     * Gets a list of all available models
     */
    public List<String> getAvailableModels() {
        try {
            OllamaModels ollamaModels = OllamaModels.builder()
                    .baseUrl(ollamaUrl)
                    .timeout(timeout)
                    .build();

            return ollamaModels.availableModels()
                    .content()
                    .stream()
                    .map(dev.langchain4j.model.ollama.OllamaModel::getName)
                    .toList();

        } catch (Exception e) {
            log.error("Error getting available models: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Checks if Ollama is running and reachable
     */
    public boolean isOllamaReachable() {
        try {
            OllamaModels ollamaModels = OllamaModels.builder()
                    .baseUrl(ollamaUrl)
                    .timeout(Duration.ofSeconds(5))
                    .build();

            ollamaModels.availableModels();
            return true;

        } catch (Exception e) {
            log.warn("Ollama not reachable at {}: {}", ollamaUrl, e.getMessage());
            return false;
        }
    }

    /**
     * Result of model availability check
     */
    public static class ModelAvailabilityResult {
        private final Status status;
        private final String modelName;
        private final String errorMessage;
        private final List<String> availableModels;

        private ModelAvailabilityResult(Status status, String modelName, String errorMessage,
                                       List<String> availableModels) {
            this.status = status;
            this.modelName = modelName;
            this.errorMessage = errorMessage;
            this.availableModels = availableModels;
        }

        public static ModelAvailabilityResult available(String modelName) {
            return new ModelAvailabilityResult(Status.AVAILABLE, modelName, null, null);
        }

        public static ModelAvailabilityResult notAvailable(String modelName, List<String> availableModels) {
            return new ModelAvailabilityResult(Status.NOT_AVAILABLE, modelName, null, availableModels);
        }

        public static ModelAvailabilityResult error(String modelName, String errorMessage) {
            return new ModelAvailabilityResult(Status.ERROR, modelName, errorMessage, null);
        }

        public static ModelAvailabilityResult notConfigured() {
            return new ModelAvailabilityResult(Status.NOT_CONFIGURED, null, null, null);
        }

        public boolean isAvailable() {
            return status == Status.AVAILABLE;
        }

        public boolean isNotAvailable() {
            return status == Status.NOT_AVAILABLE;
        }

        public boolean isError() {
            return status == Status.ERROR;
        }

        public boolean isNotConfigured() {
            return status == Status.NOT_CONFIGURED;
        }

        public Status getStatus() {
            return status;
        }

        public String getModelName() {
            return modelName;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public List<String> getAvailableModels() {
            return availableModels;
        }

        /**
         * Gets a user-friendly message
         */
        public String getUserMessage() {
            return switch (status) {
                case AVAILABLE -> String.format("Le modèle '%s' est disponible", modelName);
                case NOT_AVAILABLE -> String.format(
                        "Le modèle '%s' n'est pas disponible.\n\n" +
                        "Pour télécharger le modèle, exécutez:\n" +
                        "  ollama pull %s\n\n" +
                        "Modèles disponibles: %s",
                        modelName, modelName,
                        availableModels != null && !availableModels.isEmpty()
                                ? String.join(", ", availableModels)
                                : "aucun"
                );
                case ERROR -> String.format(
                        "Erreur lors de la vérification du modèle '%s':\n%s\n\n" +
                        "Vérifiez qu'Ollama est en cours d'exécution.",
                        modelName, errorMessage
                );
                case NOT_CONFIGURED ->
                        "️ Aucun modèle agent n'est configuré.\n\n" +
                        "Veuillez configurer le modèle dans les paramètres (recommandé: gpt-oss).";
            };
        }

        /**
         * Gets installation instructions
         */
        public String getInstallationInstructions() {
            if (status == Status.NOT_AVAILABLE && modelName != null) {
                return String.format(
                        "Pour installer le modèle '%s', exécutez la commande suivante dans votre terminal:\n\n" +
                        "  ollama pull %s\n\n" +
                        "Ensuite, redémarrez le plugin pour utiliser le mode agent.",
                        modelName, modelName
                );
            }
            return null;
        }

        public enum Status {
            AVAILABLE,
            NOT_AVAILABLE,
            ERROR,
            NOT_CONFIGURED
        }
    }
}
