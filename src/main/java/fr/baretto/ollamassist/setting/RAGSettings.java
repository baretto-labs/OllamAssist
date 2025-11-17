package fr.baretto.ollamassist.setting;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Settings for RAG (Retrieval-Augmented Generation) configuration.
 */
@State(
        name = "RAGSettings",
        storages = {@Storage("RAGSettings.xml")}
)
public class RAGSettings implements PersistentStateComponent<RAGSettings.State> {

    private State myState = new State();

    public static RAGSettings getInstance() {
        return ApplicationManager.getApplication().getService(RAGSettings.class);
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

    public String getSources() {
        return myState.sources;
    }

    public void setSources(String sources) {
        myState.sources = sources;
    }

    public int getIndexationSize() {
        return myState.indexationSize;
    }

    public void setIndexationSize(int numberOfDocuments) {
        myState.indexationSize = numberOfDocuments;
    }

    public boolean isWebSearchEnabled() {
        return myState.webSearchEnabled;
    }

    public void setWebSearchEnabled(boolean webSearchEnabled) {
        myState.webSearchEnabled = webSearchEnabled;
    }

    public boolean isRAGEnabled() {
        return myState.ragEnabled;
    }

    public void setRAGEnabled(boolean ragEnabled) {
        myState.ragEnabled = ragEnabled;
    }

    @Getter
    public static class State {
        public String sources = "src/";
        public int indexationSize = 5000;
        public boolean webSearchEnabled = false;
        public boolean ragEnabled = false;
    }
}
