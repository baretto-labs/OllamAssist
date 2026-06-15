package fr.baretto.ollamassist.auth;

import java.util.Locale;

/**
 * Authentication mode used to reach the Ollama-compatible backend.
 *
 * <ul>
 *     <li>{@link #NONE} — no {@code Authorization} header is sent.</li>
 *     <li>{@link #BASIC} — HTTP Basic auth ({@code username:password}, Base64 encoded).</li>
 *     <li>{@link #BEARER} — Bearer token / API key (e.g. OpenWebUI proxy).</li>
 * </ul>
 *
 * Basic and Bearer are mutually exclusive: both rely on the single {@code Authorization} header.
 */
public enum AuthMode {
    NONE,
    BASIC,
    BEARER;

    /**
     * Parses a persisted mode value.
     * Unknown, null or blank values resolve to {@link #NONE} (fail-closed: never send credentials
     * we cannot build, and never crash on a corrupted setting).
     */
    public static AuthMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
