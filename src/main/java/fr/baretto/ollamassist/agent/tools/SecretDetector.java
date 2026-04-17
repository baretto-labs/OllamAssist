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
     * Returns a non-null label if a high-confidence secret pattern is detected,
     * or {@code null} if the content appears clean.
     */
    public static String detect(String content) {
        if (content == null) return null;
        for (SecretPattern sp : PATTERNS) {
            if (sp.pattern().matcher(content).find()) {
                return sp.label();
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------

    private record SecretPattern(String label, Pattern pattern) {}

    private static final SecretPattern[] PATTERNS = {
        new SecretPattern("PEM private key",
            Pattern.compile("-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----")),
        new SecretPattern("AWS access key",
            Pattern.compile("(?i)AKIA[0-9A-Z]{16}")),
        new SecretPattern("AWS secret key",
            Pattern.compile("(?i)aws[_\\-.]?secret[_\\-.]?(access[_\\-.]?)?key\\s*[=:\"']\\s*[A-Za-z0-9/+=]{40}")),
        new SecretPattern("Generic high-entropy API key",
            Pattern.compile("(?i)(api[_\\-.]?key|apikey|auth[_\\-.]?token|access[_\\-.]?token|secret[_\\-.]?key)\\s*[=:\"']\\s*[A-Za-z0-9\\-_]{32,}")),
        new SecretPattern("GitHub personal access token",
            Pattern.compile("(?i)gh[pousr]_[A-Za-z0-9]{36,}")),
        new SecretPattern("Google API key",
            Pattern.compile("AIza[0-9A-Za-z\\-_]{35}")),
        new SecretPattern("Slack token",
            Pattern.compile("xox[baprs]-[0-9A-Za-z\\-]{10,}")),
        new SecretPattern("JWT token",
            Pattern.compile("eyJ[A-Za-z0-9\\-_]+\\.eyJ[A-Za-z0-9\\-_]+\\.[A-Za-z0-9\\-_]+")),
        new SecretPattern("Dotenv secret assignment",
            Pattern.compile("(?im)^(?:API[_-]?KEY|SECRET[_-]?KEY|TOKEN|PASSWORD|PASSWD|PRIVATE[_-]?KEY|ACCESS[_-]?KEY|AUTH[_-]?TOKEN|CLIENT[_-]?SECRET|DB[_-]?(?:PASSWORD|PASS)|DATABASE[_-]?(?:PASSWORD|PASS))\\s*=\\s*\\S{16,}")),
        new SecretPattern("Azure storage connection string",
            Pattern.compile("DefaultEndpointsProtocol=https;AccountName=[^;]+;AccountKey=")),
        new SecretPattern("GCP service account private key",
            Pattern.compile("\"private_key_id\"\\s*:\\s*\"[a-f0-9]{40}\"")),
    };
}
