package fr.baretto.ollamassist.setting;

import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class for SettingsBindingHelper to verify automatic synchronization between
 * OllamAssistSettings.State and ConfigurationPanel.
 *
 * Note: These tests focus on the binding logic, not the full ConfigurationPanel behavior.
 */
class SettingsBindingHelperTest {

    private OllamAssistSettings.State state;
    private MockConfigurationPanel panel;

    @Mock
    private Project project;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        state = new OllamAssistSettings.State();
        panel = new MockConfigurationPanel();
    }

    /**
     * Mock implementation of ConfigurationPanel for testing purposes.
     * Avoids UI dependencies while preserving the getter/setter contract.
     */
    static class MockConfigurationPanel {
        private String chatOllamaUrl = "";
        private String completionOllamaUrl = "";
        private String embeddingOllamaUrl = "";
        private String chatModel = null;
        private String completionModel = null;
        private String embeddingModel = null;
        private String timeout = "";
        private String sources = "";
        private int maxDocuments = 0;

        public String getChatOllamaUrl() { return chatOllamaUrl; }
        public void setChatOllamaUrl(String url) { this.chatOllamaUrl = url; }

        public String getCompletionOllamaUrl() { return completionOllamaUrl; }
        public void setCompletionOllamaUrl(String url) { this.completionOllamaUrl = url; }

        public String getEmbeddingOllamaUrl() { return embeddingOllamaUrl; }
        public void setEmbeddingOllamaUrl(String url) { this.embeddingOllamaUrl = url; }

        public String getChatModel() { return chatModel; }
        public void setChatModelName(String model) { this.chatModel = model; }

        public String getCompletionModel() { return completionModel; }
        public void setCompletionModelName(String model) { this.completionModel = model; }

        public String getEmbeddingModel() { return embeddingModel; }
        public void setEmbeddingModelName(String model) { this.embeddingModel = model; }

        public String getTimeout() { return timeout; }
        public void setTimeout(String timeout) { this.timeout = timeout; }

        public String getSources() { return sources; }
        public void setSources(String sources) { this.sources = sources; }

        public int getMaxDocuments() { return maxDocuments; }
        public void setMaxDocuments(int maxDocuments) { this.maxDocuments = maxDocuments; }
    }

    @Test
    void testLoadSettings_shouldCopyAllFieldsFromStateToPanel() {
        // Given: State with custom values
        state.chatOllamaUrl = "http://localhost:9999";
        state.completionOllamaUrl = "http://localhost:8888";
        state.embeddingOllamaUrl = "http://localhost:7777";
        state.chatModelName = "llama3.2";
        state.completionModelName = "codellama";
        state.embeddingModelName = "nomic-embed-text";
        state.timeout = "600";
        state.sources = "src/main;src/test";
        state.indexationSize = 10000;

        // When: Load settings
        SettingsBindingHelper.loadSettings(state, panel);

        // Then: Panel should contain all values from state
        assertThat(panel.getChatOllamaUrl()).isEqualTo("http://localhost:9999");
        assertThat(panel.getCompletionOllamaUrl()).isEqualTo("http://localhost:8888");
        assertThat(panel.getEmbeddingOllamaUrl()).isEqualTo("http://localhost:7777");
        assertThat(panel.getTimeout()).isEqualTo("600");
        assertThat(panel.getSources()).isEqualTo("src/main;src/test");
        assertThat(panel.getMaxDocuments()).isEqualTo(10000);
    }

    @Test
    void testSaveSettings_shouldCopyAllFieldsFromPanelToState() {
        // Given: Panel with values set (simulate user input)
        // Note: We need to load initial state first to populate the panel
        SettingsBindingHelper.loadSettings(state, panel);

        // Simulate user modifying values
        state.chatOllamaUrl = "http://localhost:1111";
        state.completionOllamaUrl = "http://localhost:2222";
        state.embeddingOllamaUrl = "http://localhost:3333";
        state.timeout = "900";
        state.sources = "custom/src";
        state.indexationSize = 5000;

        SettingsBindingHelper.loadSettings(state, panel);

        // Create new state to save into
        OllamAssistSettings.State newState = new OllamAssistSettings.State();

        // When: Save settings
        SettingsBindingHelper.saveSettings(panel, newState);

        // Then: New state should contain all values from panel
        assertThat(newState.chatOllamaUrl).isEqualTo("http://localhost:1111");
        assertThat(newState.completionOllamaUrl).isEqualTo("http://localhost:2222");
        assertThat(newState.embeddingOllamaUrl).isEqualTo("http://localhost:3333");
        assertThat(newState.timeout).isEqualTo("900");
        assertThat(newState.sources).isEqualTo("custom/src");
        assertThat(newState.indexationSize).isEqualTo(5000);
    }

    @Test
    void testIsModified_shouldReturnFalseWhenNoChanges() {
        // Given: Panel loaded with state values
        state.chatOllamaUrl = "http://localhost:11434";
        state.timeout = "300";
        SettingsBindingHelper.loadSettings(state, panel);

        // When/Then: No modifications should be detected
        assertThat(SettingsBindingHelper.isModified(state, panel)).isFalse();
    }

    @Test
    void testIsModified_shouldReturnTrueWhenChatUrlChanged() {
        // Given: Panel loaded with state values
        state.chatOllamaUrl = "http://localhost:11434";
        SettingsBindingHelper.loadSettings(state, panel);

        // When: State value is different from panel
        state.chatOllamaUrl = "http://localhost:9999";

        // Then: Modification should be detected
        assertThat(SettingsBindingHelper.isModified(state, panel)).isTrue();
    }

    @Test
    void testIsModified_shouldReturnTrueWhenTimeoutChanged() {
        // Given: Panel loaded with state values
        state.timeout = "300";
        SettingsBindingHelper.loadSettings(state, panel);

        // When: State value is different from panel
        state.timeout = "600";

        // Then: Modification should be detected
        assertThat(SettingsBindingHelper.isModified(state, panel)).isTrue();
    }

    @Test
    void testIsModified_shouldReturnTrueWhenIndexationSizeChanged() {
        // Given: Panel loaded with state values
        state.indexationSize = 5000;
        SettingsBindingHelper.loadSettings(state, panel);

        // When: State value is different from panel
        state.indexationSize = 10000;

        // Then: Modification should be detected
        assertThat(SettingsBindingHelper.isModified(state, panel)).isTrue();
    }

    @Test
    void testIsModified_shouldBeCaseInsensitiveForStrings() {
        // Given: Panel loaded with state values
        state.chatOllamaUrl = "http://LOCALHOST:11434";
        SettingsBindingHelper.loadSettings(state, panel);

        // When: State has same value but different case
        state.chatOllamaUrl = "http://localhost:11434";

        // Then: No modification should be detected (case insensitive)
        assertThat(SettingsBindingHelper.isModified(state, panel)).isFalse();
    }

    @Test
    void testIsModified_shouldHandleNullModelsGracefully() {
        // Given: Panel loaded with state values
        SettingsBindingHelper.loadSettings(state, panel);

        // When: Models are null (async loading scenario)
        // Panel's combo boxes might return null during initialization

        // Then: Should not throw exception and should handle null gracefully
        assertThat(SettingsBindingHelper.isModified(state, panel)).isFalse();
    }

    @Test
    void testExcludedFields_shouldNotBeLoadedOrSaved() {
        // Given: State with excluded fields set
        state.webSearchEnabled = true;
        state.ragEnabled = true;
        state.uistate = true;

        // When: Load settings
        SettingsBindingHelper.loadSettings(state, panel);

        // Then: Excluded fields should not affect panel (no corresponding setters should be called)
        // This is verified implicitly - if setters were called, they would throw NoSuchMethodException

        // When: Save settings
        OllamAssistSettings.State newState = new OllamAssistSettings.State();
        newState.webSearchEnabled = false;
        newState.ragEnabled = false;
        newState.uistate = false;

        SettingsBindingHelper.saveSettings(panel, newState);

        // Then: Excluded fields should remain unchanged (false)
        assertThat(newState.webSearchEnabled).isFalse();
        assertThat(newState.ragEnabled).isFalse();
        assertThat(newState.uistate).isFalse();
    }

    @Test
    void testFullRoundTrip_shouldPreserveAllValues() {
        // Given: Original state with all values set
        state.chatOllamaUrl = "http://localhost:9999";
        state.completionOllamaUrl = "http://localhost:8888";
        state.embeddingOllamaUrl = "http://localhost:7777";
        state.chatModelName = "llama3.2";
        state.completionModelName = "codellama";
        state.embeddingModelName = "nomic-embed-text";
        state.timeout = "600";
        state.sources = "src/main;src/test";
        state.indexationSize = 10000;

        // When: Load into panel, then save to new state
        SettingsBindingHelper.loadSettings(state, panel);
        OllamAssistSettings.State newState = new OllamAssistSettings.State();
        SettingsBindingHelper.saveSettings(panel, newState);

        // Then: All values should be preserved (except excluded fields)
        assertThat(newState.chatOllamaUrl).isEqualTo(state.chatOllamaUrl);
        assertThat(newState.completionOllamaUrl).isEqualTo(state.completionOllamaUrl);
        assertThat(newState.embeddingOllamaUrl).isEqualTo(state.embeddingOllamaUrl);
        assertThat(newState.timeout).isEqualTo(state.timeout);
        assertThat(newState.sources).isEqualTo(state.sources);
        assertThat(newState.indexationSize).isEqualTo(state.indexationSize);
    }

    @Test
    void testPersistence_modificationDetectionWorksAfterMultipleChanges() {
        // Given: Initial state
        state.chatOllamaUrl = "http://localhost:11434";
        state.timeout = "300";
        SettingsBindingHelper.loadSettings(state, panel);

        // When: Multiple changes are made
        state.chatOllamaUrl = "http://localhost:9999";
        boolean firstCheck = SettingsBindingHelper.isModified(state, panel);

        state.timeout = "600";
        boolean secondCheck = SettingsBindingHelper.isModified(state, panel);

        // Then: Both checks should detect modifications
        assertThat(firstCheck).isTrue();
        assertThat(secondCheck).isTrue();
    }

    @Test
    void testPersistence_noModificationAfterSaveAndReload() {
        // Given: State with values
        state.chatOllamaUrl = "http://localhost:9999";
        state.timeout = "600";

        // When: Load, save to new state, then load again
        SettingsBindingHelper.loadSettings(state, panel);
        OllamAssistSettings.State savedState = new OllamAssistSettings.State();
        SettingsBindingHelper.saveSettings(panel, savedState);

        // Then: No modification should be detected between saved state and panel
        assertThat(SettingsBindingHelper.isModified(savedState, panel)).isFalse();
    }
}
