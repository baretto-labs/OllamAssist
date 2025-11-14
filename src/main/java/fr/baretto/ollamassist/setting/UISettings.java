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
        name = "UISettings",
        storages = {@Storage("UISettings.xml")}
)
public class UISettings implements PersistentStateComponent<UISettings.State> {

    private State myState = new State();

    public static UISettings getInstance() {
        return ApplicationManager.getApplication().getService(UISettings.class);
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
