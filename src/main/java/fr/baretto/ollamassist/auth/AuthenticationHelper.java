package fr.baretto.ollamassist.auth;

import fr.baretto.ollamassist.setting.OllamaSettings;
import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Helper class for handling authentication for Ollama connections.
 *
 * <p>Supports two mutually exclusive schemes, selected by {@link AuthMode}:
 * HTTP Basic ({@code username:password}) and Bearer token / API key
 * (used by proxies such as OpenWebUI).</p>
 *
 * <p>All call sites must go through {@link #createAuthorizationHeaderValue()} or
 * {@link #authHeaders()} — the {@code Authorization} header value (including its scheme)
 * is built here only, never duplicated.</p>
 */
@UtilityClass
public class AuthenticationHelper {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BASIC_PREFIX = "Basic ";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CREDENTIALS_FORMAT = "%s:%s";

    /**
     * Pure builder for the {@code Authorization} header value.
     * Returns {@code null} when no header should be sent (fail-closed): mode {@link AuthMode#NONE},
     * a {@code null} mode, or missing credentials for the selected mode.
     *
     * @return e.g. {@code "Basic dXNlcjpwYXNz"} or {@code "Bearer sk-..."}, or {@code null}
     */
    public static String buildAuthorizationHeaderValue(AuthMode mode, String username, String password, String apiKey) {
        if (mode == null) {
            return null;
        }
        return switch (mode) {
            case NONE -> null;
            case BASIC -> {
                if (isBlank(username) || isBlank(password)) {
                    yield null;
                }
                String credentials = String.format(CREDENTIALS_FORMAT, username.trim(), password.trim());
                yield BASIC_PREFIX + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            }
            case BEARER -> {
                if (isBlank(apiKey)) {
                    yield null;
                }
                yield BEARER_PREFIX + apiKey.trim();
            }
        };
    }

    /**
     * Builds the {@code Authorization} header value from the current settings, or {@code null}
     * when authentication is not configured.
     */
    public static String createAuthorizationHeaderValue() {
        OllamaSettings settings = OllamaSettings.getInstance();
        return buildAuthorizationHeaderValue(
                settings.getAuthMode(),
                settings.getUsername(),
                settings.getPassword(),
                settings.getApiKey());
    }

    /**
     * Returns the authentication headers to attach to an Ollama request, or an empty map when
     * authentication is not configured. Suitable for {@code builder.customHeaders(...)}.
     */
    public static Map<String, String> authHeaders() {
        String value = createAuthorizationHeaderValue();
        return value == null ? Map.of() : Map.of(AUTHORIZATION_HEADER, value);
    }

    /**
     * Checks if authentication is configured (any scheme).
     *
     * @return true if a valid {@code Authorization} header can be built, false otherwise
     */
    public static boolean isAuthenticationConfigured() {
        return createAuthorizationHeaderValue() != null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
