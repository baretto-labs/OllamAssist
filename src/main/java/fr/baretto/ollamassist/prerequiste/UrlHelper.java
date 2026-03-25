package fr.baretto.ollamassist.prerequiste;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Helper class for URL normalization and validation.
 * Ensures URLs are properly formatted for HTTP requests to Ollama.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UrlHelper {

    private static final String HTTP_SCHEME = "http://";
    private static final String HTTPS_SCHEME = "https://";

    /**
     * Normalizes a URL for use with Ollama API.
     * - Adds http:// scheme if missing
     * - Removes trailing slashes
     * - Validates the URL format
     *
     * @param url the URL to normalize (can be null or empty)
     * @return normalized URL, or null if input is invalid
     * @throws IllegalArgumentException if URL is null or empty
     */
    @NotNull
    public static String normalizeUrl(@NotNull String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }

        String trimmed = url.trim();

        // Add http:// scheme if missing
        if (!trimmed.startsWith(HTTP_SCHEME) && !trimmed.startsWith(HTTPS_SCHEME)) {
            trimmed = HTTP_SCHEME + trimmed;
        }

        // Remove trailing slashes
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        // Validate the URL format
        validateUrl(trimmed);

        return trimmed;
    }

    /**
     * Validates that a URL is properly formatted.
     *
     * @param url the URL to validate
     * @throws IllegalArgumentException if URL is invalid
     */
    public static void validateUrl(@NotNull String url) {
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            log.warn("Invalid URL format: {}", url);
            throw new IllegalArgumentException("Invalid URL format: " + url, e);
        }
    }

    /**
     * Constructs an API endpoint URL by appending a path to the base URL.
     * Ensures no double slashes in the resulting URL.
     *
     * @param baseUrl the base Ollama URL (will be normalized)
     * @param apiPath the API path (e.g., "/api/tags")
     * @return complete API endpoint URL
     */
    @NotNull
    public static String buildApiUrl(@NotNull String baseUrl, @NotNull String apiPath) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Base URL cannot be null or empty");
        }
        if (apiPath == null || apiPath.trim().isEmpty()) {
            throw new IllegalArgumentException("API path cannot be null or empty");
        }

        String normalized = normalizeUrl(baseUrl);
        String path = apiPath.startsWith("/") ? apiPath : "/" + apiPath;

        return normalized + path;
    }
}