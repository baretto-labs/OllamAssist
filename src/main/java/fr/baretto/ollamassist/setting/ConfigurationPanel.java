package fr.baretto.ollamassist.setting;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBTabbedPane;
import fr.baretto.ollamassist.setting.panels.ActionsConfigPanel;
import fr.baretto.ollamassist.setting.panels.AgentConfigPanel;
import fr.baretto.ollamassist.setting.panels.OllamaConfigPanel;
import fr.baretto.ollamassist.setting.panels.PromptConfigPanel;
import fr.baretto.ollamassist.setting.panels.RAGConfigPanel;
import fr.baretto.ollamassist.setting.panels.UIConfigPanel;

import javax.swing.*;
import java.awt.*;

public class ConfigurationPanel extends JPanel {

    private final transient OllamaConfigPanel ollamaPanel;
    private final transient RAGConfigPanel ragPanel;
    private final transient ActionsConfigPanel actionsPanel;
    private final transient PromptConfigPanel promptPanel;
    private final transient UIConfigPanel uiPanel;
    private final transient AgentConfigPanel agentPanel;
    private final transient Project project;

    public ConfigurationPanel(Project project) {
        this.project = project;
        setLayout(new BorderLayout());

        // Create sub-panels
        ollamaPanel = new OllamaConfigPanel(project);
        ragPanel = new RAGConfigPanel(project);
        actionsPanel = new ActionsConfigPanel();
        promptPanel = new PromptConfigPanel();
        uiPanel = new UIConfigPanel();
        agentPanel = new AgentConfigPanel(project);

        // Create tabbed pane
        JBTabbedPane tabbedPane = new JBTabbedPane();
        tabbedPane.addTab("Ollama", ollamaPanel);
        tabbedPane.addTab("RAG", ragPanel);
        tabbedPane.addTab("Actions", actionsPanel);
        tabbedPane.addTab("Prompts", promptPanel);
        tabbedPane.addTab("UI", uiPanel);
        tabbedPane.addTab("Agent", agentPanel);

        add(tabbedPane, BorderLayout.CENTER);

        initializeListeners();
    }

    private void initializeListeners() {
        // The Settings dialog detects pending changes by polling isModified(), so no manual change
        // notification is needed. Only the auto-approve toggle has a side effect: it is applied
        // immediately, without waiting for the "Apply" button.
        actionsPanel.getAutoApproveFileCreationCheckbox().addItemListener(e ->
                ActionsSettings.getInstance().setAutoApproveFileCreation(actionsPanel.isAutoApproveFileCreation()));
    }

    // Delegation methods to sub-panels for backward compatibility with SettingsBindingHelper

    // Ollama settings
    public String getChatOllamaUrl() {
        return ollamaPanel.getChatOllamaUrl();
    }

    public void setChatOllamaUrl(String url) {
        ollamaPanel.setChatOllamaUrl(url);
    }

    public String getCompletionOllamaUrl() {
        return ollamaPanel.getCompletionOllamaUrl();
    }

    public void setCompletionOllamaUrl(String url) {
        ollamaPanel.setCompletionOllamaUrl(url);
    }

    public String getEmbeddingOllamaUrl() {
        return ollamaPanel.getEmbeddingOllamaUrl();
    }

    public void setEmbeddingOllamaUrl(String url) {
        ollamaPanel.setEmbeddingOllamaUrl(url);
    }

    public String getUsername() {
        return ollamaPanel.getUsername();
    }

    public void setUsername(String username) {
        ollamaPanel.setUsername(username);
    }

    public String getPassword() {
        return ollamaPanel.getPassword();
    }

    public void setPassword(String password) {
        ollamaPanel.setPassword(password);
    }

    public fr.baretto.ollamassist.auth.AuthMode getAuthMode() {
        return ollamaPanel.getAuthMode();
    }

    public void setAuthMode(fr.baretto.ollamassist.auth.AuthMode authMode) {
        ollamaPanel.setAuthMode(authMode);
    }

    public String getApiKey() {
        return ollamaPanel.getApiKey();
    }

    public void setApiKey(String apiKey) {
        ollamaPanel.setApiKey(apiKey);
    }

    public String getChatModel() {
        return ollamaPanel.getChatModel();
    }

    public void setChatModelName(String chatModelName) {
        ollamaPanel.setChatModelName(chatModelName);
    }

    public String getCompletionModel() {
        return ollamaPanel.getCompletionModel();
    }

    public void setCompletionModelName(String completionModelName) {
        ollamaPanel.setCompletionModelName(completionModelName);
    }

    public String getEmbeddingModel() {
        return ollamaPanel.getEmbeddingModel();
    }

    public void setEmbeddingModelName(String embeddingModelName) {
        ollamaPanel.setEmbeddingModelName(embeddingModelName);
    }

    public String getTimeout() {
        return ollamaPanel.getTimeout();
    }

    public void setTimeout(String timeout) {
        ollamaPanel.setTimeout(timeout);
    }

    // RAG settings
    public String getSources() {
        return ragPanel.getSources();
    }

    public void setSources(String sources) {
        ragPanel.setSources(sources);
    }

    public int getMaxDocuments() {
        return ragPanel.getMaxDocuments();
    }

    public void setMaxDocuments(int maxDocuments) {
        ragPanel.setMaxDocuments(maxDocuments);
    }

    public void triggerClearLocalStorage() {
        ragPanel.triggerClearLocalStorage();
    }

    public void triggerCleanAllDatabase() {
        ragPanel.triggerCleanAllDatabase();
    }

    // Actions settings
    public boolean isAutoApproveFileCreation() {
        return actionsPanel.isAutoApproveFileCreation();
    }

    public void setAutoApproveFileCreation(boolean value) {
        actionsPanel.setAutoApproveFileCreation(value);
    }

    public boolean isToolsEnabled() {
        return actionsPanel.isToolsEnabled();
    }

    public boolean isCodeCompletionEnabled() {
        return actionsPanel.isCodeCompletionEnabled();
    }

    public void setToolsEnabled(boolean value) {
        actionsPanel.setToolsEnabled(value);
    }

    public void setCodeCompletionEnabled(boolean value) {
        actionsPanel.setCodeCompletionEnabled(value);
    }

    // Prompt settings
    public String getChatSystemPrompt() {
        return promptPanel.getChatSystemPrompt();
    }

    public void setChatSystemPrompt(String prompt) {
        promptPanel.setChatSystemPrompt(prompt);
    }

    public String getRefactorUserPrompt() {
        return promptPanel.getRefactorUserPrompt();
    }

    public void setRefactorUserPrompt(String prompt) {
        promptPanel.setRefactorUserPrompt(prompt);
    }

    public boolean validatePrompts() {
        return promptPanel.validatePrompts();
    }

    // UI settings delegation
    public void applyUISettings() {
        uiPanel.applySettings();
    }

    public void resetUISettings() {
        uiPanel.resetSettings();
    }

    public boolean isUISettingsModified() {
        return uiPanel.isModified();
    }

    // Agent settings delegation
    public int getAgentPlanTimeoutSeconds() {
        return agentPanel.getAgentPlanTimeoutSeconds();
    }

    public void setAgentPlanTimeoutSeconds(int seconds) {
        agentPanel.setAgentPlanTimeoutSeconds(seconds);
    }

    public int getRunCommandTimeoutSeconds() {
        return agentPanel.getRunCommandTimeoutSeconds();
    }

    public void setRunCommandTimeoutSeconds(int seconds) {
        agentPanel.setRunCommandTimeoutSeconds(seconds);
    }

    public int getApprovalTimeoutMinutes() {
        return agentPanel.getApprovalTimeoutMinutes();
    }

    public void setApprovalTimeoutMinutes(int minutes) {
        agentPanel.setApprovalTimeoutMinutes(minutes);
    }

    public int getAgentToolTimeoutSeconds() {
        return agentPanel.getAgentToolTimeoutSeconds();
    }

    public void setAgentToolTimeoutSeconds(int seconds) {
        agentPanel.setAgentToolTimeoutSeconds(seconds);
    }

    public int getAgentGlobalTimeoutMinutes() {
        return agentPanel.getAgentGlobalTimeoutMinutes();
    }

    public void setAgentGlobalTimeoutMinutes(int minutes) {
        agentPanel.setAgentGlobalTimeoutMinutes(minutes);
    }

    public boolean isAgentParanoidMode() {
        return agentPanel.isParanoidMode();
    }

    public void setAgentParanoidMode(boolean value) {
        agentPanel.setParanoidMode(value);
    }
}
