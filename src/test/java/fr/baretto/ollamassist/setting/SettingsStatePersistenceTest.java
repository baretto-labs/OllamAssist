package fr.baretto.ollamassist.setting;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the IntelliJ XML serialization contract for every {@code PersistentStateComponent}
 * state class: a customized value must survive a serialize / deserialize round-trip,
 * which is exactly what happens when the IDE is closed and restarted.
 * <p>
 * {@code XmlSerializer} only binds public non-final fields, or properties exposing both a
 * getter and a setter. A private field with a Lombok {@code @Getter} and no setter is
 * silently ignored — written nowhere, read back as the field initializer (the default).
 */
@DisplayName("PersistentStateComponent state serialization round-trip")
class SettingsStatePersistenceTest {

    private static <T> T roundTrip(T state, Class<T> stateClass) {
        Element serialized = XmlSerializer.serialize(state);
        return XmlSerializer.deserialize(serialized, stateClass);
    }

    @Test
    @DisplayName("keeps a customized chat system prompt across an IDE restart")
    void shouldPersistCustomizedChatSystemPrompt() {
        PromptSettings settings = new PromptSettings();
        settings.setChatSystemPrompt("You are a laconic C++ reviewer.");

        PromptSettings restarted = new PromptSettings();
        restarted.loadState(roundTrip(settings.getState(), PromptSettings.State.class));

        assertThat(restarted.getChatSystemPrompt()).isEqualTo("You are a laconic C++ reviewer.");
    }

    @Test
    @DisplayName("keeps a customized refactor user prompt across an IDE restart")
    void shouldPersistCustomizedRefactorUserPrompt() {
        PromptSettings settings = new PromptSettings();
        settings.setRefactorUserPrompt("Refactor {{code}} in {{language}}, no comments.");

        PromptSettings restarted = new PromptSettings();
        restarted.loadState(roundTrip(settings.getState(), PromptSettings.State.class));

        assertThat(restarted.getRefactorUserPrompt())
                .isEqualTo("Refactor {{code}} in {{language}}, no comments.");
    }

    /**
     * {@code XmlSerializer} silently drops characters it cannot store in an XML attribute —
     * control characters and, notably, anything outside the Basic Multilingual Plane (emoji).
     * A user who tweaks a few lines of the default prompt persists the whole text, so any
     * emoji left in the default would come back mangled after a restart.
     */
    @Test
    @DisplayName("stores the default chat system prompt verbatim, without dropping characters")
    void shouldRoundTripDefaultChatSystemPromptVerbatim() {
        PromptSettings restarted = new PromptSettings();
        restarted.loadState(roundTrip(new PromptSettings.State(), PromptSettings.State.class));

        assertThat(restarted.getChatSystemPrompt()).isEqualTo(PromptSettings.DEFAULT_CHAT_SYSTEM_PROMPT);
    }

    @Test
    @DisplayName("stores the default refactor user prompt verbatim, without dropping characters")
    void shouldRoundTripDefaultRefactorUserPromptVerbatim() {
        PromptSettings restarted = new PromptSettings();
        restarted.loadState(roundTrip(new PromptSettings.State(), PromptSettings.State.class));

        assertThat(restarted.getRefactorUserPrompt()).isEqualTo(PromptSettings.DEFAULT_REFACTOR_USER_PROMPT);
    }

    @Test
    @DisplayName("keeps inline code completion switched off across an IDE restart")
    void shouldPersistDisabledCodeCompletion() {
        ActionsSettings settings = new ActionsSettings();
        settings.setCodeCompletionEnabled(false);

        ActionsSettings restarted = new ActionsSettings();
        restarted.loadState(roundTrip(settings.getState(), ActionsSettings.State.class));

        assertThat(restarted.isCodeCompletionEnabled()).isFalse();
    }

    @Test
    @DisplayName("leaves inline code completion on for users who never touched the setting")
    void shouldKeepCodeCompletionEnabledByDefault() {
        ActionsSettings restarted = new ActionsSettings();
        restarted.loadState(roundTrip(new ActionsSettings.State(), ActionsSettings.State.class));

        assertThat(restarted.isCodeCompletionEnabled()).isTrue();
    }

    @Test
    @DisplayName("keeps the auto-approve file creation flag across an IDE restart")
    void shouldPersistAutoApproveFileCreation() {
        ActionsSettings settings = new ActionsSettings();
        settings.setAutoApproveFileCreation(true);

        ActionsSettings restarted = new ActionsSettings();
        restarted.loadState(roundTrip(settings.getState(), ActionsSettings.State.class));

        assertThat(restarted.isAutoApproveFileCreation()).isTrue();
    }

    @Test
    @DisplayName("keeps the tools enabled flag across an IDE restart")
    void shouldPersistToolsEnabled() {
        ActionsSettings settings = new ActionsSettings();
        settings.setToolsEnabled(true);

        ActionsSettings restarted = new ActionsSettings();
        restarted.loadState(roundTrip(settings.getState(), ActionsSettings.State.class));

        assertThat(restarted.isToolsEnabled()).isTrue();
    }
}
