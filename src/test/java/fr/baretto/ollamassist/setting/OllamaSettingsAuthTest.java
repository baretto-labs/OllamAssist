package fr.baretto.ollamassist.setting;

import fr.baretto.ollamassist.auth.AuthMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OllamaSettings authentication mode resolution")
class OllamaSettingsAuthTest {

    @Test
    @DisplayName("infers BASIC for a legacy config with username/password and no authMode")
    void shouldInferBasicForLegacyBasicAuthConfig() {
        OllamaSettings settings = new OllamaSettings();
        OllamaSettings.State legacy = new OllamaSettings.State();
        legacy.username = "alice";
        legacy.password = "secret";
        legacy.authMode = ""; // field did not exist before this feature
        settings.loadState(legacy);

        assertThat(settings.getAuthMode()).isEqualTo(AuthMode.BASIC);
    }

    @Test
    @DisplayName("infers NONE for a legacy config without credentials")
    void shouldInferNoneWhenNoCredentials() {
        OllamaSettings settings = new OllamaSettings();
        OllamaSettings.State legacy = new OllamaSettings.State();
        legacy.username = "";
        legacy.password = "";
        legacy.authMode = "";
        settings.loadState(legacy);

        assertThat(settings.getAuthMode()).isEqualTo(AuthMode.NONE);
    }

    @Test
    @DisplayName("uses the persisted authMode when explicitly set")
    void shouldUsePersistedAuthMode() {
        OllamaSettings settings = new OllamaSettings();
        OllamaSettings.State state = new OllamaSettings.State();
        state.authMode = AuthMode.BEARER.name();
        state.apiKey = "sk-token";
        settings.loadState(state);

        assertThat(settings.getAuthMode()).isEqualTo(AuthMode.BEARER);
        assertThat(settings.getApiKey()).isEqualTo("sk-token");
    }

    @Test
    @DisplayName("does not infer BASIC when authMode is explicitly NONE despite stored credentials")
    void shouldRespectExplicitNoneOverLegacyCredentials() {
        OllamaSettings settings = new OllamaSettings();
        OllamaSettings.State state = new OllamaSettings.State();
        state.username = "alice";
        state.password = "secret";
        state.authMode = AuthMode.NONE.name();
        settings.loadState(state);

        assertThat(settings.getAuthMode()).isEqualTo(AuthMode.NONE);
    }
}
