package fr.baretto.ollamassist.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthMode.fromString")
class AuthModeTest {

    @Test
    @DisplayName("parses known values case-insensitively")
    void shouldParseKnownValues() {
        assertThat(AuthMode.fromString("basic")).isEqualTo(AuthMode.BASIC);
        assertThat(AuthMode.fromString("BEARER")).isEqualTo(AuthMode.BEARER);
        assertThat(AuthMode.fromString(" None ")).isEqualTo(AuthMode.NONE);
    }

    @Test
    @DisplayName("falls back to NONE on null, blank or unknown value")
    void shouldFallBackToNone() {
        assertThat(AuthMode.fromString(null)).isEqualTo(AuthMode.NONE);
        assertThat(AuthMode.fromString("")).isEqualTo(AuthMode.NONE);
        assertThat(AuthMode.fromString("   ")).isEqualTo(AuthMode.NONE);
        assertThat(AuthMode.fromString("oauth2")).isEqualTo(AuthMode.NONE);
    }
}
