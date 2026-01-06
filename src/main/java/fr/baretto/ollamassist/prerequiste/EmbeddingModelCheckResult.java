package fr.baretto.ollamassist.prerequiste;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Result of embedding model availability check.
 * Handles both local DJL models and Ollama models, including fallback scenarios.
 *
 * @see PrerequisiteService#checkEmbeddingModelAsync(String, String)
 */
@Getter
@AllArgsConstructor
public class EmbeddingModelCheckResult {

    public enum Status {
        /**
         * Local DJL model (BgeSmallEnV15Quantized) loaded successfully.
         */
        LOCAL_OK,

        /**
         * Local DJL model failed to load, but Ollama fallback model is available.
         */
        LOCAL_FAILED_FALLBACK_OK,

        /**
         * Local DJL model failed to load and no Ollama fallback available.
         */
        LOCAL_FAILED_NO_FALLBACK,

        /**
         * Ollama embedding model is available.
         */
        OLLAMA_OK,

        /**
         * No embedding model configured or available.
         */
        NOT_AVAILABLE
    }

    private final Status status;
    private final String message;
    private final String suggestedAction;

    /**
     * Creates a result indicating local DJL model is available.
     */
    public static EmbeddingModelCheckResult localAvailable() {
        return new EmbeddingModelCheckResult(
                Status.LOCAL_OK,
                "Local embedding model available",
                null
        );
    }

    /**
     * Creates a result indicating local DJL model failed but fallback may be available.
     *
     * @param fallbackAvailable whether the Ollama fallback model (nomic-embed-text) is available
     */
    public static EmbeddingModelCheckResult localFailedWithFallback(boolean fallbackAvailable) {
        if (fallbackAvailable) {
            return new EmbeddingModelCheckResult(
                    Status.LOCAL_FAILED_FALLBACK_OK,
                    "Local model unavailable (using Ollama fallback)",
                    "Using nomic-embed-text as fallback"
            );
        } else {
            return new EmbeddingModelCheckResult(
                    Status.LOCAL_FAILED_NO_FALLBACK,
                    "Local model unavailable",
                    "Please run: ollama pull nomic-embed-text"
            );
        }
    }

    /**
     * Creates a result indicating Ollama embedding model is available.
     */
    public static EmbeddingModelCheckResult ollamaAvailable() {
        return new EmbeddingModelCheckResult(
                Status.OLLAMA_OK,
                "Ollama embedding model available",
                null
        );
    }

    /**
     * Creates a result indicating no embedding model is available.
     */
    public static EmbeddingModelCheckResult notAvailable() {
        return new EmbeddingModelCheckResult(
                Status.NOT_AVAILABLE,
                "Model not configured or not available",
                null
        );
    }

    /**
     * Checks if the embedding model can be used (either local, Ollama, or fallback).
     *
     * @return true if the model is usable
     */
    public boolean isUsable() {
        return status == Status.LOCAL_OK ||
                status == Status.LOCAL_FAILED_FALLBACK_OK ||
                status == Status.OLLAMA_OK;
    }

    /**
     * Checks if this is a fallback scenario (local failed but Ollama available).
     *
     * @return true if using fallback
     */
    public boolean isFallback() {
        return status == Status.LOCAL_FAILED_FALLBACK_OK;
    }
}
