package fr.baretto.ollamassist.setting;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Test class to verify settings persistence behavior.
 * This test demonstrates the issue where settings are not properly persisted after apply().
 */
class OllamassistSettingsConfigurableTest {

    private OllamaSettings mockOllamaSettings;
    private RAGSettings mockRAGSettings;
    private ActionsSettings mockActionsSettings;
    private OllamAssistUISettings mockUISettings;

    @BeforeEach
    void setup() {
        // Create mock settings instances
        mockOllamaSettings = new OllamaSettings();
        mockOllamaSettings.loadState(new OllamaSettings.State());

        mockRAGSettings = new RAGSettings();
        mockRAGSettings.loadState(new RAGSettings.State());

        mockActionsSettings = new ActionsSettings();
        mockActionsSettings.loadState(new ActionsSettings.State());

        mockUISettings = new OllamAssistUISettings();
        mockUISettings.loadState(new OllamAssistUISettings.State());
    }

    @Test
    void testSettingsStatePersistenceAfterApply() {
        try (MockedStatic<ApplicationManager> mockedAppManager = mockStatic(ApplicationManager.class);
             MockedStatic<OllamaSettings> mockedOllamaSettings = mockStatic(OllamaSettings.class);
             MockedStatic<RAGSettings> mockedRAGSettings = mockStatic(RAGSettings.class);
             MockedStatic<ActionsSettings> mockedActionsSettings = mockStatic(ActionsSettings.class);
             MockedStatic<OllamAssistUISettings> mockedUISettings = mockStatic(OllamAssistUISettings.class)) {

            Application mockApp = mock(Application.class);
            mockedAppManager.when(ApplicationManager::getApplication).thenReturn(mockApp);

            // Setup mocks to return our test instances
            mockedOllamaSettings.when(OllamaSettings::getInstance).thenReturn(mockOllamaSettings);
            mockedRAGSettings.when(RAGSettings::getInstance).thenReturn(mockRAGSettings);
            mockedActionsSettings.when(ActionsSettings::getInstance).thenReturn(mockActionsSettings);
            mockedUISettings.when(OllamAssistUISettings::getInstance).thenReturn(mockUISettings);

            // Given: Initial settings with default values
            OllamAssistSettings settings = createMockSettings();
            OllamaSettings.State initialOllamaState = mockOllamaSettings.getState();

            // When: Modify settings using setters (simulating what apply() does)
            String newChatUrl = "http://localhost:9999";
            String newChatModel = "llama3.2";
            String newTimeout = "600";
            String newUsername = "user";
            String newPassword = "pass1234";

            settings.setChatOllamaUrl(newChatUrl);
            settings.setChatModelName(newChatModel);
            settings.setTimeout(newTimeout);
            settings.setUsername(newUsername);
            settings.setPassword(newPassword);

            // Then: The state object should reflect the changes in OllamaSettings
            OllamaSettings.State currentOllamaState = mockOllamaSettings.getState();
            assertThat(currentOllamaState).isNotNull();
            assertThat(currentOllamaState.chatOllamaUrl).isEqualTo(newChatUrl);
            assertThat(currentOllamaState.chatModelName).isEqualTo(newChatModel);
            assertThat(currentOllamaState.timeout).isEqualTo(newTimeout);
            assertThat(currentOllamaState.username).isEqualTo(newUsername);
            assertThat(currentOllamaState.password).isEqualTo(newPassword);

            // Critical test: Verify that the state object is the same instance
            assertThat(currentOllamaState).isSameAs(initialOllamaState);
        }
    }

    @Test
    void testSettingsStateModificationWithLoadState() {
        try (MockedStatic<ApplicationManager> mockedAppManager = mockStatic(ApplicationManager.class);
             MockedStatic<OllamaSettings> mockedOllamaSettings = mockStatic(OllamaSettings.class);
             MockedStatic<RAGSettings> mockedRAGSettings = mockStatic(RAGSettings.class);
             MockedStatic<ActionsSettings> mockedActionsSettings = mockStatic(ActionsSettings.class);
             MockedStatic<OllamAssistUISettings> mockedUISettings = mockStatic(OllamAssistUISettings.class)) {

            Application mockApp = mock(Application.class);
            mockedAppManager.when(ApplicationManager::getApplication).thenReturn(mockApp);

            mockedOllamaSettings.when(OllamaSettings::getInstance).thenReturn(mockOllamaSettings);
            mockedRAGSettings.when(RAGSettings::getInstance).thenReturn(mockRAGSettings);
            mockedActionsSettings.when(ActionsSettings::getInstance).thenReturn(mockActionsSettings);
            mockedUISettings.when(OllamAssistUISettings::getInstance).thenReturn(mockUISettings);

            // Given: Settings with default values
            OllamAssistSettings settings = createMockSettings();

            String newChatUrl = "http://localhost:9999";
            String newChatModel = "llama3.2";
            String newTimeout = "600";
            String newUsername = "user";
            String newPassword = "pass1234";

            // When: Modify state and explicitly call loadState
            settings.setChatOllamaUrl(newChatUrl);
            settings.setChatModelName(newChatModel);
            settings.setTimeout(newTimeout);
            settings.setUsername(newUsername);
            settings.setPassword(newPassword);

            // Get current state and reload it to trigger persistence mechanism
            OllamaSettings.State modifiedState = mockOllamaSettings.getState();
            mockOllamaSettings.loadState(modifiedState);

            // Then: Values should be persisted
            OllamaSettings.State reloadedState = mockOllamaSettings.getState();
            assertThat(reloadedState.chatOllamaUrl).isEqualTo(newChatUrl);
            assertThat(reloadedState.chatModelName).isEqualTo(newChatModel);
            assertThat(reloadedState.timeout).isEqualTo(newTimeout);
            assertThat(reloadedState.username).isEqualTo(newUsername);
            assertThat(reloadedState.password).isEqualTo(newPassword);
        }
    }

    @Test
    void testIsModifiedDetectsChanges() {
        try (MockedStatic<ApplicationManager> mockedAppManager = mockStatic(ApplicationManager.class);
             MockedStatic<OllamaSettings> mockedOllamaSettings = mockStatic(OllamaSettings.class);
             MockedStatic<RAGSettings> mockedRAGSettings = mockStatic(RAGSettings.class);
             MockedStatic<ActionsSettings> mockedActionsSettings = mockStatic(ActionsSettings.class);
             MockedStatic<OllamAssistUISettings> mockedUISettings = mockStatic(OllamAssistUISettings.class)) {

            Application mockApp = mock(Application.class);
            mockedAppManager.when(ApplicationManager::getApplication).thenReturn(mockApp);

            mockedOllamaSettings.when(OllamaSettings::getInstance).thenReturn(mockOllamaSettings);
            mockedRAGSettings.when(RAGSettings::getInstance).thenReturn(mockRAGSettings);
            mockedActionsSettings.when(ActionsSettings::getInstance).thenReturn(mockActionsSettings);
            mockedUISettings.when(OllamAssistUISettings::getInstance).thenReturn(mockUISettings);

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

            // And: Ollama state should contain new values
            OllamaSettings.State state = mockOllamaSettings.getState();
            assertThat(state.chatOllamaUrl).isEqualTo("http://localhost:9999");
            assertThat(state.chatModelName).isEqualTo("llama3.2");
        }
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
