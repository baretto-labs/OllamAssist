package fr.baretto.ollamassist.agent.tools;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

/**
 * Detects high-confidence secrets in file content before the agent reads or returns it.
 *
 * <p>This is a best-effort guard, not a comprehensive scanner. The goal is to prevent
 * accidental exfiltration of obvious secrets (API keys, tokens, private keys) via the
 * agent's tool output. It is NOT a replacement for a dedicated secret scanner.
 *
 * <p>When a secret is detected, the tool should return a failure rather than returning
 * the content to the LLM, which would then echo it in its response.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SecretDetector {

    /**
     * Suppress comment — any line containing this token is excluded from secret scanning.
     *
     * <p>Use this in example code, test fixtures, or documentation that intentionally
     * contains placeholder secrets to silence false-positive detections (S-4):
     * <pre>
     *     String testKey = "AKIAIOSFODNN7EXAMPLE"; // ollamassist-nocheck
     * </pre>
     */
    static final String NOCHECK_COMMENT = "ollamassist-nocheck";

    /**
     * Returns a non-null label if a high-confidence secret pattern is detected,
     * or {@code null} if the content appears clean.
     *
     * <p>Lines annotated with {@code // ollamassist-nocheck} are excluded from scanning.
     */
    public static String detect(String content) {
        if (content == null) return null;
        String filtered = filterNoCheckLines(content);
        for (SecretPattern sp : PATTERNS) {
            if (sp.pattern().matcher(filtered).find()) {
                return sp.label();
            }
        }
        return null;
    }

    /**
     * Strips any line that contains the {@value NOCHECK_COMMENT} suppressor comment,
     * then returns the remaining content for pattern matching.
     * When no suppressor is present, returns {@code content} unchanged (zero allocation).
     */
    private static String filterNoCheckLines(String content) {
        if (!content.contains(NOCHECK_COMMENT)) return content;
        String[] lines = content.split("\n", -1);
        StringBuilder sb = new StringBuilder(content.length());
        for (String line : lines) {
            if (!line.contains(NOCHECK_COMMENT)) {
                sb.append(line).append('\n');
            }
        }
        // Remove the trailing newline we added if the original did not end with one
        if (!content.endsWith("\n") && sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------

    private record SecretPattern(String label, Pattern pattern) {}

    private static final SecretPattern[] PATTERNS = {
        new SecretPattern("PEM private key",
            Pattern.compile("-----BEGIN (RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----")),
        // AWS — both traditional AKIA* access keys and the new ASIA* session tokens
        new SecretPattern("AWS access key",
            Pattern.compile("(?i)(AKIA|ASIA|AROA|AIPA|ANPA|ANVA|APKA)[0-9A-Z]{16}")),
        // AWS secret key — all common naming variants (underscore, camelCase, uppercase)
        new SecretPattern("AWS secret key",
            Pattern.compile("(?i)aws[_\\-.]?secret[_\\-.]?(access[_\\-.]?)?key\\s*[=:\"']\\s*[A-Za-z0-9/+=]{40}")),
        // Stripe live/test secret keys
        new SecretPattern("Stripe secret key",
            Pattern.compile("sk_(live|test)_[0-9a-zA-Z]{24,}")),
        // Stripe restricted keys
        new SecretPattern("Stripe restricted key",
            Pattern.compile("rk_(live|test)_[0-9a-zA-Z]{24,}")),
        // Twilio Account SID + Auth Token (Auth Token is 32 hex chars)
        new SecretPattern("Twilio credentials",
            Pattern.compile("AC[a-f0-9]{32}|SK[a-f0-9]{32}")),
        // Database connection strings with embedded credentials
        new SecretPattern("Database connection string with credentials",
            Pattern.compile("(?i)(mysql|postgres|postgresql|mongodb|redis|jdbc)://[^:@\\s\"']{1,128}:[^@\\s\"']{4,}@")),
        // Generic high-entropy API key (also covers OAuth client secrets, etc.)
        new SecretPattern("Generic high-entropy API key",
            Pattern.compile("(?i)(api[_\\-.]?key|apikey|auth[_\\-.]?token|access[_\\-.]?token|secret[_\\-.]?key|client[_\\-.]?secret|app[_\\-.]?secret)\\s*[=:\"']\\s*[A-Za-z0-9\\-_]{32,}")),
        new SecretPattern("GitHub personal access token",
            Pattern.compile("(?i)gh[pousr]_[A-Za-z0-9]{36,}")),
        new SecretPattern("Google API key",
            Pattern.compile("AIza[0-9A-Za-z\\-_]{35}")),
        new SecretPattern("Slack token",
            Pattern.compile("xox[baprs]-[0-9A-Za-z\\-]{10,}")),
        new SecretPattern("JWT token",
            Pattern.compile("eyJ[A-Za-z0-9\\-_]+\\.eyJ[A-Za-z0-9\\-_]+\\.[A-Za-z0-9\\-_]+")),
        // Dotenv: matches NAME=value at start of line (case-insensitive, includes indented lines).
        // Minimum 16 non-whitespace chars for the value to avoid false positives on short words
        // like "api_key = disabled" or "password = change_me".
        new SecretPattern("Dotenv secret assignment",
            Pattern.compile("(?im)^\\s*(?:API[_-]?KEY|SECRET[_-]?KEY|TOKEN|PASSWORD|PASSWD|PRIVATE[_-]?KEY|ACCESS[_-]?KEY|AUTH[_-]?TOKEN|CLIENT[_-]?SECRET|OAUTH[_-]?(?:SECRET|TOKEN)|DB[_-]?(?:PASSWORD|PASS|PWD)|DATABASE[_-]?(?:PASSWORD|PASS|PWD)|ENCRYPTION[_-]?KEY)\\s*=\\s*\\S{16,}")),
        new SecretPattern("Azure storage connection string",
            Pattern.compile("DefaultEndpointsProtocol=https;AccountName=[^;]+;AccountKey=")),
        new SecretPattern("GCP service account private key",
            Pattern.compile("\"private_key_id\"\\s*:\\s*\"[a-f0-9]{40}\"")),
        // SendGrid / Mailgun / Postmark API keys
        new SecretPattern("SendGrid API key",
            Pattern.compile("SG\\.[A-Za-z0-9\\-_]{22}\\.[A-Za-z0-9\\-_]{43}")),
        // npm auth token (in .npmrc files)
        new SecretPattern("npm auth token",
            Pattern.compile("//registry\\.npmjs\\.org/:_authToken=[A-Za-z0-9\\-_]+")),
        // SSH private key inside a config or env file (bare Base64 block)
        new SecretPattern("SSH private key block",
            Pattern.compile("-----BEGIN OPENSSH PRIVATE KEY-----")),
    };
}
