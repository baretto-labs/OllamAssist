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
 * Legacy settings class for backward compatibility.
 * Use {@link OllamaSettings}, {@link RAGSettings}, {@link ActionsSettings}, or {@link UISettings} instead.
 */
@State(
        name = "OllamAssist",
        storages = {@Storage("OllamAssist.xml")}
)
public class OllamAssistSettings implements PersistentStateComponent<OllamAssistSettings.State> {

    public static final String DEFAULT_URL = "http://localhost:11434";
    private State myState = new State();

    public static OllamAssistSettings getInstance() {
        return ApplicationManager.getApplication().getService(OllamAssistSettings.class);
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

    // Ollama settings - delegating to OllamaSettings
    public String getChatOllamaUrl() {
        return OllamaSettings.getInstance().getChatOllamaUrl();
    }

    public void setChatOllamaUrl(String url) {
        OllamaSettings.getInstance().setChatOllamaUrl(url);
    }

    public String getCompletionOllamaUrl() {
        return OllamaSettings.getInstance().getCompletionOllamaUrl();
    }

    public void setCompletionOllamaUrl(String url) {
        OllamaSettings.getInstance().setCompletionOllamaUrl(url);
    }

    public String getEmbeddingOllamaUrl() {
        return OllamaSettings.getInstance().getEmbeddingOllamaUrl();
    }

    public void setEmbeddingOllamaUrl(String url) {
        OllamaSettings.getInstance().setEmbeddingOllamaUrl(url);
    }

    public String getChatModelName() {
        return OllamaSettings.getInstance().getChatModelName();
    }

    public void setChatModelName(String modelName) {
        OllamaSettings.getInstance().setChatModelName(modelName);
    }

    public String getCompletionModelName() {
        return OllamaSettings.getInstance().getCompletionModelName();
    }

    public void setCompletionModelName(String modelName) {
        OllamaSettings.getInstance().setCompletionModelName(modelName);
    }

    public String getEmbeddingModelName() {
        return OllamaSettings.getInstance().getEmbeddingModelName();
    }

    public void setEmbeddingModelName(String embeddingModelName) {
        OllamaSettings.getInstance().setEmbeddingModelName(embeddingModelName);
    }

    public Duration getTimeoutDuration() {
        return OllamaSettings.getInstance().getTimeoutDuration();
    }

    public String getTimeout() {
        return OllamaSettings.getInstance().getTimeout();
    }

    public void setTimeout(String timeout) {
        OllamaSettings.getInstance().setTimeout(timeout);
    }

    public String getUsername() {
        return OllamaSettings.getInstance().getUsername();
    }

    public void setUsername(String username) {
        OllamaSettings.getInstance().setUsername(username);
    }

    public String getPassword() {
        return OllamaSettings.getInstance().getPassword();
    }

    public void setPassword(String password) {
        OllamaSettings.getInstance().setPassword(password);
    }

    // RAG settings - delegating to RAGSettings
    public void setIndexationSize(int numberOfDocuments) {
        RAGSettings.getInstance().setIndexationSize(numberOfDocuments);
    }

    public int getIndexationSize() {
        return RAGSettings.getInstance().getIndexationSize();
    }

    public String getSources() {
        return RAGSettings.getInstance().getSources();
    }

    public void setSources(String sources) {
        RAGSettings.getInstance().setSources(sources);
    }

    public void setWebSearchEnabled(boolean webSearchEnabled) {
        RAGSettings.getInstance().setWebSearchEnabled(webSearchEnabled);
    }

    public boolean webSearchEnabled() {
        return RAGSettings.getInstance().isWebSearchEnabled();
    }

    public void setRAGEnabled(boolean ragEnabled) {
        RAGSettings.getInstance().setRAGEnabled(ragEnabled);
    }

    public boolean ragEnabled() {
        return RAGSettings.getInstance().isRAGEnabled();
    }

    // Actions settings - delegating to ActionsSettings
    public boolean isAutoApproveFileCreation() {
        return ActionsSettings.getInstance().isAutoApproveFileCreation();
    }

    public void setAutoApproveFileCreation(boolean autoApprove) {
        ActionsSettings.getInstance().setAutoApproveFileCreation(autoApprove);
    }

    // UI settings - delegating to OllamAssistUISettings
    public void setUIState(boolean isCollapsed) {
        OllamAssistUISettings.getInstance().setContextPanelCollapsed(isCollapsed);
    }

    public boolean getUIState() {
        return OllamAssistUISettings.getInstance().getContextPanelCollapsed();
    }

    @Getter
    public static class State {
        public String chatOllamaUrl = DEFAULT_URL;
        public String completionOllamaUrl = DEFAULT_URL;
        public String embeddingOllamaUrl = DEFAULT_URL;
        public String chatModelName = OllamaSettings.DEFAULT_MODEL;
        public String completionModelName = OllamaSettings.DEFAULT_MODEL;
        public String embeddingModelName = DEFAULT_EMBEDDING_MODEL;
        public String timeout = "300";
        public String sources = "src/";
        public int indexationSize = 5000;
        public boolean webSearchEnabled = false;
        public boolean ragEnabled = false;
        public String username = "";
        public String password = "";

        // Persiste configuration for UI component, currently used only for the chat context
        public boolean uistate = false;

        // Auto-approve file creation without user confirmation
        public boolean autoApproveFileCreation = false;
    }

}