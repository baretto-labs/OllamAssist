package fr.baretto.ollamassist.setting;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Settings for UI state persistence.
 */
@State(
        name = "OllamAssistUISettings",
        storages = {@Storage("OllamAssistUISettings.xml")}
)
public class OllamAssistUISettings implements PersistentStateComponent<OllamAssistUISettings.State> {

    private State myState = new State();

    public static OllamAssistUISettings getInstance() {
        return ApplicationManager.getApplication().getService(OllamAssistUISettings.class);
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

    public boolean getContextPanelCollapsed() {
        return myState.contextPanelCollapsed;
    }

    public void setContextPanelCollapsed(boolean isCollapsed) {
        myState.contextPanelCollapsed = isCollapsed;
    }

    @Getter
    public static class State {
        // Persiste configuration for UI component, currently used only for the chat context
        public boolean contextPanelCollapsed = false;
    }
}
