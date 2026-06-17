package fr.baretto.ollamassist.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthenticationHelper.buildAuthorizationHeaderValue")
class AuthenticationHelperTest {

    @Test
    @DisplayName("returns null when mode is NONE")
    void shouldReturnNullWhenModeIsNone() {
        String header = AuthenticationHelper.buildAuthorizationHeaderValue(
                AuthMode.NONE, "user", "pass", "key");

        assertThat(header).isNull();
    }

    @Test
    @DisplayName("returns null when mode is null (fail-closed)")
    void shouldReturnNullWhenModeIsNull() {
        String header = AuthenticationHelper.buildAuthorizationHeaderValue(
                null, "user", "pass", "key");

        assertThat(header).isNull();
    }

    @Test
    @DisplayName("builds a Basic header from username and password")
    void shouldBuildBasicHeader() {
        String expected = "Basic " + Base64.getEncoder()
                .encodeToString("user:pass".getBytes(StandardCharsets.UTF_8));

        String header = AuthenticationHelper.buildAuthorizationHeaderValue(
                AuthMode.BASIC, "user", "pass", null);

        assertThat(header).isEqualTo(expected);
    }

    @Test
    @DisplayName("trims username and password before encoding")
    void shouldTrimBasicCredentials() {
        String expected = "Basic " + Base64.getEncoder()
                .encodeToString("user:pass".getBytes(StandardCharsets.UTF_8));

        String header = AuthenticationHelper.buildAuthorizationHeaderValue(
                AuthMode.BASIC, "  user  ", "  pass  ", null);

        assertThat(header).isEqualTo(expected);
    }

    @Test
    @DisplayName("returns null for BASIC when password is blank (fail-closed)")
    void shouldReturnNullForBasicWhenPasswordBlank() {
        String header = AuthenticationHelper.buildAuthorizationHeaderValue(
                AuthMode.BASIC, "user", "   ", null);

        assertThat(header).isNull();
    }

    @Test
    @DisplayName("returns null for BASIC when username is null (fail-closed)")
    void shouldReturnNullForBasicWhenUsernameNull() {
        String header = AuthenticationHelper.buildAuthorizationHeaderValue(
                AuthMode.BASIC, null, "pass", null);

        assertThat(header).isNull();
    }

    @Test
    @DisplayName("builds a Bearer header from the API key")
    void shouldBuildBearerHeader() {
        String header = AuthenticationHelper.buildAuthorizationHeaderValue(
                AuthMode.BEARER, null, null, "sk-secret-token");

        assertThat(header).isEqualTo("Bearer sk-secret-token");
    }

    @Test
    @DisplayName("trims the API key before building the Bearer header")
    void shouldTrimApiKey() {
        String header = AuthenticationHelper.buildAuthorizationHeaderValue(
                AuthMode.BEARER, null, null, "  sk-secret-token  ");

        assertThat(header).isEqualTo("Bearer sk-secret-token");
    }

    @Test
    @DisplayName("returns null for BEARER when the API key is blank (fail-closed)")
    void shouldReturnNullForBearerWhenApiKeyBlank() {
        String header = AuthenticationHelper.buildAuthorizationHeaderValue(
                AuthMode.BEARER, "user", "pass", "  ");

        assertThat(header).isNull();
    }
}
