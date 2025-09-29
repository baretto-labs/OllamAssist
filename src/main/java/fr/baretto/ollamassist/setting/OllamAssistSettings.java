package fr.baretto.ollamassist.setting;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import fr.baretto.ollamassist.setting.agent.AgentModeSettings;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

import static fr.baretto.ollamassist.chat.rag.RAGConstants.DEFAULT_EMBEDDING_MODEL;

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

    public String getEmbeddingModelName() {
        return myState.embeddingModelName;
    }

    public void setEmbeddingModelName(String embeddingModelName) {
        myState.embeddingModelName = embeddingModelName;
    }

    public int getIndexationSize() {
        return myState.indexationSize;
    }

    public void setIndexationSize(int numberOfDocuments) {
        myState.indexationSize = numberOfDocuments;
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

    public String getSources() {
        return myState.sources;
    }

    public void setSources(String sources) {
        myState.sources = sources;
    }

    public void setWebSearchEnabled(boolean webSearchEnabled) {
        myState.webSearchEnabled = webSearchEnabled;
    }

    public void setRAGEnabled(boolean ragEnabled) {
        myState.ragEnabled = ragEnabled;
    }

    public boolean webSearchEnabled() {
        return myState.webSearchEnabled;
    }

    public boolean ragEnabled() {
        return myState.ragEnabled;
    }

    public boolean getUIState() {
        return myState.uistate;
    }

    public void setUIState(boolean isCollapsed) {
        myState.uistate = isCollapsed;
    }

    // === Méthodes pour le mode agent ===

    public boolean isAgentModeEnabled() {
        return myState.agentModeEnabled;
    }

    public void setAgentModeEnabled(boolean enabled) {
        myState.agentModeEnabled = enabled;
    }

    public AgentModeSettings.AgentSecurityLevel getAgentSecurityLevel() {
        return myState.agentSecurityLevel;
    }

    public void setAgentSecurityLevel(AgentModeSettings.AgentSecurityLevel level) {
        myState.agentSecurityLevel = level;
    }

    public int getAgentMaxTasksPerSession() {
        return myState.agentMaxTasksPerSession;
    }

    public void setAgentMaxTasksPerSession(int maxTasks) {
        myState.agentMaxTasksPerSession = Math.max(1, Math.min(100, maxTasks));
    }

    public boolean isAgentAutoApprovalEnabled() {
        return myState.agentAutoApprovalEnabled;
    }

    public void setAgentAutoApprovalEnabled(boolean enabled) {
        myState.agentAutoApprovalEnabled = enabled;
    }

    @Getter
    public static class State {
        public String chatOllamaUrl = DEFAULT_URL;
        public String completionOllamaUrl = DEFAULT_URL;
        public String embeddingOllamaUrl = DEFAULT_URL;
        public String chatModelName = "llama3.1";
        public String completionModelName = "llama3.1";
        public String embeddingModelName = DEFAULT_EMBEDDING_MODEL;
        public String timeout = "300";
        public String sources = "src/";
        public int indexationSize = 5000;
        public boolean webSearchEnabled = false;
        public boolean ragEnabled = false;

        // Persiste configuration for UI component, currently used only for the chat context
        public boolean uistate = false;

        // === Paramètres du mode agent ===
        public boolean agentModeEnabled = true; // Activé par défaut
        public AgentModeSettings.AgentSecurityLevel agentSecurityLevel = AgentModeSettings.AgentSecurityLevel.STANDARD;
        public int agentMaxTasksPerSession = 10;
        public boolean agentAutoApprovalEnabled = false;
    }

}