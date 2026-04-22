package fr.baretto.ollamassist.agent;

import fr.baretto.ollamassist.agent.tools.SecretDetector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretDetectorTest {

    // -------------------------------------------------------------------------
    // Clean content — must return null
    // -------------------------------------------------------------------------

    @Test
    void nullContent_returnsNull() {
        assertThat(SecretDetector.detect(null)).isNull();
    }

    @Test
    void emptyContent_returnsNull() {
        assertThat(SecretDetector.detect("")).isNull();
    }

    @Test
    void plainJavaClass_returnsNull() {
        String content = "public class OrderService {\n    void save(Order o) {}\n}";
        assertThat(SecretDetector.detect(content)).isNull();
    }

    @Test
    void regularConfigFile_returnsNull() {
        String content = "server.port=8080\nspring.datasource.url=jdbc:h2:mem:test";
        assertThat(SecretDetector.detect(content)).isNull();
    }

    // -------------------------------------------------------------------------
    // PEM private key
    // -------------------------------------------------------------------------

    @Test
    void pemPrivateKey_detected() {
        String content = "-----BEGIN PRIVATE KEY-----\nMIIE...\n-----END PRIVATE KEY-----";
        assertThat(SecretDetector.detect(content)).contains("PEM");
    }

    @Test
    void pemRsaPrivateKey_detected() {
        String content = "-----BEGIN RSA PRIVATE KEY-----\nMIIE...\n-----END RSA PRIVATE KEY-----";
        assertThat(SecretDetector.detect(content)).contains("PEM");
    }

    @Test
    void pemOpensshPrivateKey_detected() {
        String content = "-----BEGIN OPENSSH PRIVATE KEY-----\nb3BlbnNzaC1rZXktdjEA\n-----END OPENSSH PRIVATE KEY-----";
        assertThat(SecretDetector.detect(content)).contains("PEM");
    }

    // -------------------------------------------------------------------------
    // AWS access key (AKIA...)
    // -------------------------------------------------------------------------

    @Test
    void awsAccessKey_detected() {
        String content = "aws_access_key_id = AKIAIOSFODNN7EXAMPLE";
        assertThat(SecretDetector.detect(content)).contains("AWS access key");
    }

    @Test
    void awsAccessKey_lowercase_detected() {
        String content = "AKIA1234567890ABCDEF";
        assertThat(SecretDetector.detect(content)).contains("AWS access key");
    }

    // -------------------------------------------------------------------------
    // GitHub personal access token
    // -------------------------------------------------------------------------

    @Test
    void githubPat_gho_detected() {
        String content = "token: ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefg123";
        assertThat(SecretDetector.detect(content)).contains("GitHub");
    }

    @Test
    void githubPat_ghs_detected() {
        String content = "GHS_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefg123";
        assertThat(SecretDetector.detect(content)).contains("GitHub");
    }

    // -------------------------------------------------------------------------
    // Google API key
    // -------------------------------------------------------------------------

    @Test
    void googleApiKey_detected() {
        // Pattern: AIza + exactly 35 chars from [0-9A-Za-z\-_]
        String content = "AIzaABCDEFGHIJKLMNOPQRSTUVWXYZ123456789";
        assertThat(SecretDetector.detect(content)).contains("Google");
    }

    // -------------------------------------------------------------------------
    // Slack token
    // -------------------------------------------------------------------------

    @Test
    void slackBotToken_detected() {
        String content = "xoxb-" + "1234567890-1234567890-abcdefghijklmnop";
        assertThat(SecretDetector.detect(content)).contains("Slack");
    }

    @Test
    void slackAppToken_detected() {
        String content = "xoxa-12345678901234567890";
        assertThat(SecretDetector.detect(content)).contains("Slack");
    }

    // -------------------------------------------------------------------------
    // JWT token
    // -------------------------------------------------------------------------

    @Test
    void jwtToken_detected() {
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
                + ".eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0"
                + ".SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        assertThat(SecretDetector.detect(jwt)).contains("JWT");
    }

    // -------------------------------------------------------------------------
    // Generic high-entropy API key
    // -------------------------------------------------------------------------

    @Test
    void genericApiKey_detected() {
        // "api_key = " + 32+ alphanumeric chars
        String content = "api_key = abcdefghijklmnopqrstuvwxyz123456";
        assertThat(SecretDetector.detect(content)).isNotNull();
    }

    @Test
    void accessToken_detected() {
        String content = "access_token = ABCDEFGHIJKLMNOPQRSTUVWXYZ123456";
        assertThat(SecretDetector.detect(content)).isNotNull();
    }

    @Test
    void secretKey_detected() {
        // Pattern requires value directly after [=:"'] with no intervening quote
        String content = "secret_key=abcdefghijklmnopqrstuvwxyz123456";
        assertThat(SecretDetector.detect(content)).isNotNull();
    }

    // -------------------------------------------------------------------------
    // S-4: ollamassist-nocheck suppressor — false positive prevention
    // -------------------------------------------------------------------------

    @Test
    void nocheckOnSameLine_suppressesDetection() {
        // Placeholder AWS key in test code should not trigger when suppressed
        String content = "String testKey = \"AKIAIOSFODNN7EXAMPLE\"; // ollamassist-nocheck";
        assertThat(SecretDetector.detect(content)).isNull();
    }

    @Test
    void nocheckOnlyOnAnnotatedLine_otherLinesStillScanned() {
        // The suppressed line is clean; the other line has a real secret
        String content = "String placeholder = \"AKIAIOSFODNN7EXAMPLE\"; // ollamassist-nocheck\n"
                + "String realKey = \"AKIAIOSFODNN7REALKEY1\";";
        assertThat(SecretDetector.detect(content)).isNotNull(); // second line still triggers
    }

    @Test
    void nocheckDoesNotSuppressOtherLines() {
        // nocheck on one line should not suppress an unrelated line with a real secret
        String content = "// example constants — ollamassist-nocheck\n"
                + "private static final String KEY = \"sk_live_" + "abcdefghijklmnop123456789\";";
        assertThat(SecretDetector.detect(content)).isNotNull();
    }

    @Test
    void contentWithoutNocheck_returnedUnchanged() {
        // When no suppressor present, detection must still work
        String content = "AKIAIOSFODNN7EXAMPLE";
        assertThat(SecretDetector.detect(content)).contains("AWS access key");
    }

    // -------------------------------------------------------------------------
    // Short values must NOT trigger generic pattern (< 32 chars)
    // -------------------------------------------------------------------------

    @Test
    void shortApiKeyValue_notDetected() {
        // "api_key = " + short value — should NOT trigger
        String content = "api_key = shortvalue";
        assertThat(SecretDetector.detect(content)).isNull();
    }
}
