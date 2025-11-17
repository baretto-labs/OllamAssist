package fr.baretto.ollamassist.setting;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

import static fr.baretto.ollamassist.chat.rag.RAGConstants.DEFAULT_EMBEDDING_MODEL;

/**
 * Settings for Ollama configuration (URLs, models, authentication, timeout).
 */
@State(
        name = "OllamaSettings",
        storages = {@Storage("OllamaSettings.xml")}
)
public class OllamaSettings implements PersistentStateComponent<OllamaSettings.State> {

    public static final String DEFAULT_URL = "http://localhost:11434";
    public static final String DEFAULT_MODEL = "llama3.1";
    private State myState = new State();

    public static OllamaSettings getInstance() {
        return ApplicationManager.getApplication().getService(OllamaSettings.class);
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
        // Ensure default values if fields are null or empty
        if (myState.chatModelName == null || myState.chatModelName.isEmpty()) {
            myState.chatModelName = DEFAULT_MODEL;
        }
        if (myState.completionModelName == null || myState.completionModelName.isEmpty()) {
            myState.completionModelName = DEFAULT_MODEL;
        }
        if (myState.embeddingModelName == null || myState.embeddingModelName.isEmpty()) {
            myState.embeddingModelName = DEFAULT_EMBEDDING_MODEL;
        }
    }

    public String getChatOllamaUrl() {
        return myState.chatOllamaUrl;
    }

    public void setChatOllamaUrl(String url) {
        myState.chatOllamaUrl = url;
    }

    public String getCompletionOllamaUrl() {
        return myState.completionOllamaUrl;
    }

    public void setCompletionOllamaUrl(String url) {
        myState.completionOllamaUrl = url;
    }

    public String getEmbeddingOllamaUrl() {
        return myState.embeddingOllamaUrl;
    }

    public void setEmbeddingOllamaUrl(String url) {
        myState.embeddingOllamaUrl = url;
    }

    public String getChatModelName() {
        if (myState.chatModelName == null || myState.chatModelName.isEmpty()) {
            return DEFAULT_MODEL;
        }
        return myState.chatModelName;
    }

    public void setChatModelName(String modelName) {
        myState.chatModelName = modelName;
    }

    public String getCompletionModelName() {
        if (myState.completionModelName == null || myState.completionModelName.isEmpty()) {
            return DEFAULT_MODEL;
        }
        return myState.completionModelName;
    }

    public void setCompletionModelName(String modelName) {
        myState.completionModelName = modelName;
    }

    public String getEmbeddingModelName() {
        if (myState.embeddingModelName == null || myState.embeddingModelName.isEmpty()) {
            return DEFAULT_EMBEDDING_MODEL;
        }
        return myState.embeddingModelName;
    }

    public void setEmbeddingModelName(String embeddingModelName) {
        myState.embeddingModelName = embeddingModelName;
    }

    public Duration getTimeoutDuration() {
        try {
            return Duration.ofSeconds(Long.parseLong(myState.timeout));
        } catch (NumberFormatException e) {
            return Duration.ofSeconds(300);
        }
    }

    public String getTimeout() {
        return myState.timeout;
    }

    public void setTimeout(String timeout) {
        myState.timeout = timeout;
    }

    public String getUsername() {
        return myState.username;
    }

    public void setUsername(String username) {
        myState.username = username;
    }

    public String getPassword() {
        return myState.password;
    }

    public void setPassword(String password) {
        myState.password = password;
    }

    @Getter
    public static class State {
        public String chatOllamaUrl = DEFAULT_URL;
        public String completionOllamaUrl = DEFAULT_URL;
        public String embeddingOllamaUrl = DEFAULT_URL;
        public String chatModelName = DEFAULT_MODEL;
        public String completionModelName = DEFAULT_MODEL;
        public String embeddingModelName = DEFAULT_EMBEDDING_MODEL;
        public String timeout = "300";
        public String username = "";
        public String password = "";
    }
}
