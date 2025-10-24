package fr.baretto.ollamassist.setting;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class to verify settings persistence behavior.
 * This test demonstrates the issue where settings are not properly persisted after apply().
 */
class OllamassistSettingsConfigurableTest {

    @Test
    void testSettingsStatePersistenceAfterApply() {
        // Given: Initial settings with default values
        OllamAssistSettings settings = createMockSettings();
        OllamAssistSettings.State initialState = settings.getState();

        // When: Modify settings using setters (simulating what apply() does)
        String newChatUrl = "http://localhost:9999";
        String newChatModel = "llama3.2";
        String newTimeout = "600";

        settings.setChatOllamaUrl(newChatUrl);
        settings.setChatModelName(newChatModel);
        settings.setTimeout(newTimeout);

        // Then: The state object should reflect the changes
        OllamAssistSettings.State currentState = settings.getState();
        assertThat(currentState).isNotNull();
        assertThat(currentState.chatOllamaUrl).isEqualTo(newChatUrl);
        assertThat(currentState.chatModelName).isEqualTo(newChatModel);
        assertThat(currentState.timeout).isEqualTo(newTimeout);

        // Critical test: Verify that the state object is the same instance
        // This is the issue - if state is not marked as modified, IntelliJ won't persist it
        assertThat(currentState).isSameAs(initialState);
    }

    @Test
    void testSettingsStateModificationWithLoadState() {
        // Given: Settings with default values
        OllamAssistSettings settings = createMockSettings();

        String newChatUrl = "http://localhost:9999";
        String newChatModel = "llama3.2";
        String newTimeout = "600";

        // When: Modify state and explicitly call loadState
        settings.setChatOllamaUrl(newChatUrl);
        settings.setChatModelName(newChatModel);
        settings.setTimeout(newTimeout);

        // Get current state and reload it to trigger persistence mechanism
        OllamAssistSettings.State modifiedState = settings.getState();
        settings.loadState(modifiedState);

        // Then: Values should be persisted
        OllamAssistSettings.State reloadedState = settings.getState();
        assertThat(reloadedState.chatOllamaUrl).isEqualTo(newChatUrl);
        assertThat(reloadedState.chatModelName).isEqualTo(newChatModel);
        assertThat(reloadedState.timeout).isEqualTo(newTimeout);
    }

    @Test
    void testIsModifiedDetectsChanges() {
        // Given: Mock configurable with initial settings
        OllamAssistSettings settings = createMockSettings();

        // Store initial values
        String initialUrl = settings.getChatOllamaUrl();
        String initialModel = settings.getChatModelName();

        // When: Values are changed
        settings.setChatOllamaUrl("http://localhost:9999");
        settings.setChatModelName("llama3.2");

        // Then: Changes should be reflected
        assertThat(settings.getChatOllamaUrl()).isNotEqualTo(initialUrl);
        assertThat(settings.getChatModelName()).isNotEqualTo(initialModel);

        // And: State should contain new values
        OllamAssistSettings.State state = settings.getState();
        assertThat(state.chatOllamaUrl).isEqualTo("http://localhost:9999");
        assertThat(state.chatModelName).isEqualTo("llama3.2");
    }

    /**
     * Creates a mock settings instance for testing.
     * In real scenarios, this would be obtained via OllamAssistSettings.getInstance()
     */
    private OllamAssistSettings createMockSettings() {
        OllamAssistSettings settings = new OllamAssistSettings();
        // Initialize with default state
        settings.loadState(new OllamAssistSettings.State());
        return settings;
    }
}
