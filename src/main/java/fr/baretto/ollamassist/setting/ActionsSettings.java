package fr.baretto.ollamassist.setting;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Settings for AI actions configuration (file creation, code editing, etc.).
 */
@State(
        name = "ActionsSettings",
        storages = {@Storage("ActionsSettings.xml")}
)
public class ActionsSettings implements PersistentStateComponent<ActionsSettings.State> {

    private State myState = new State();

    public static ActionsSettings getInstance() {
        return ApplicationManager.getApplication().getService(ActionsSettings.class);
    }

    @Nullable
    @Override
    public State getState() {
        if (myState == null) {
            myState = new State();
        }
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }

    public boolean isAutoApproveFileCreation() {
        return myState.autoApproveFileCreation;
    }

    public void setAutoApproveFileCreation(boolean autoApprove) {
        myState.autoApproveFileCreation = autoApprove;
    }

    public boolean isCodeCompletionEnabled() {
        return myState.codeCompletionEnabled;
    }

    public void setCodeCompletionEnabled(boolean enabled) {
        myState.codeCompletionEnabled = enabled;
    }

    public boolean isToolsEnabled() {
        return myState.toolsEnabled;
    }

    public void setToolsEnabled(boolean enabled) {
        myState.toolsEnabled = enabled;
    }

    /**
     * Fields must stay public: IntelliJ's {@code XmlSerializer} only binds public non-final
     * fields, or properties exposing both a getter and a setter. A private field with only a
     * Lombok {@code @Getter} is silently skipped, so the value is never persisted and reverts
     * to the default on IDE restart (same root cause as issue #170).
     */
    @Getter
    public static class State {
        // Auto-approve file creation without user confirmation
        public boolean autoApproveFileCreation = false;

        // Enable/disable AI tools (function calling) - disabled by default (experimental)
        public boolean toolsEnabled = false;

        // Inline code completion in the editor. On by default; users whose workflow does not
        // want suggestions must be able to switch it off without disabling the whole plugin.
        public boolean codeCompletionEnabled = true;
    }
}
