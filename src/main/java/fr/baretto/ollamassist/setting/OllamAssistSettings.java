package fr.baretto.ollamassist.setting;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
        name = "OllamAssist",
        storages = {@Storage("OllamAssist.xml")}
)
public class OllamAssistSettings implements PersistentStateComponent<OllamAssistSettings.State> {

    private State myState = new State();

    public static OllamAssistSettings getInstance() {
        return ServiceManager.getService(OllamAssistSettings.class);
    }

    @Nullable
    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }

    public String getChatModelName() {
        return myState.chatModelName;
    }

    public void setChatModelName(String modelName) {
        myState.chatModelName = modelName;
    }

    public String getCompletionModelName() {
        return myState.completionModelName;
    }

    public void setCompletionModelName(String modelName) {
        myState.completionModelName = modelName;
    }

    public String getSources() {
        return myState.sources;
    }

    public void setSources(String sources) {
        myState.sources = sources;
    }

    @Getter
    public static class State {
        public String chatModelName = "llama3.1";
        public String completionModelName = "llama3.1";
        public String sources = "src/";
    }


}
