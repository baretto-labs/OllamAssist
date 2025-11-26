package fr.baretto.ollamassist.setting;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBTabbedPane;
import fr.baretto.ollamassist.setting.panels.ActionsConfigPanel;
import fr.baretto.ollamassist.setting.panels.OllamaConfigPanel;
import fr.baretto.ollamassist.setting.panels.PromptConfigPanel;
import fr.baretto.ollamassist.setting.panels.RAGConfigPanel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ConfigurationPanel extends JPanel {

    private final transient OllamaConfigPanel ollamaPanel;
    private final transient RAGConfigPanel ragPanel;
    private final transient ActionsConfigPanel actionsPanel;
    private final transient PromptConfigPanel promptPanel;
    private final transient Project project;
    private final List<Consumer<Boolean>> changeListeners = new ArrayList<>();

    public ConfigurationPanel(Project project) {
        this.project = project;
        setLayout(new BorderLayout());

        // Create sub-panels
        ollamaPanel = new OllamaConfigPanel(project);
        ragPanel = new RAGConfigPanel(project);
        actionsPanel = new ActionsConfigPanel();
        promptPanel = new PromptConfigPanel();

        // Create tabbed pane
        JBTabbedPane tabbedPane = new JBTabbedPane();
        tabbedPane.addTab("Ollama", ollamaPanel);
        tabbedPane.addTab("RAG", ragPanel);
        tabbedPane.addTab("Actions", actionsPanel);
        tabbedPane.addTab("Prompts", promptPanel);

        add(tabbedPane, BorderLayout.CENTER);

        initializeListeners();
    }

    public void addChangeListener(Consumer<Boolean> listener) {
        changeListeners.add(listener);
    }
    public void removeChangeListener(Consumer<Boolean> listener){
        changeListeners.remove(listener);
    }

    private void notifyChangeListeners() {
        changeListeners.forEach(listener -> listener.accept(true));
    }

    private void initializeListeners() {
        DocumentListener documentListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                notifyChangeListeners();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                notifyChangeListeners();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                notifyChangeListeners();
            }
        };

        // Ollama panel listeners
        ollamaPanel.getChatOllamaUrlField().getDocument().addDocumentListener(documentListener);
        ollamaPanel.getCompletionOllamaUrlField().getDocument().addDocumentListener(documentListener);
        ollamaPanel.getEmbeddingOllamaUrlField().getDocument().addDocumentListener(documentListener);
        ollamaPanel.getUsernameField().getDocument().addDocumentListener(documentListener);
        ollamaPanel.getPasswordField().getDocument().addDocumentListener(documentListener);
        ollamaPanel.getTimeoutField().getDocument().addDocumentListener(documentListener);

        ItemListener itemListener = e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                notifyChangeListeners();
            }
        };
        ollamaPanel.getChatModelComboBox().addItemListener(itemListener);
        ollamaPanel.getCompletionModelComboBox().addItemListener(itemListener);
        ollamaPanel.getEmbeddingModelComboBox().addItemListener(itemListener);

        // RAG panel listeners
        ragPanel.getSourcesField().getDocument().addDocumentListener(documentListener);
        ragPanel.getMaxDocumentsField().getDocument().addDocumentListener(documentListener);

        // Actions panel listeners
        actionsPanel.getAutoApproveFileCreationCheckbox().addItemListener(e -> {
            // Apply immediately without waiting for "Apply" button
            ActionsSettings.getInstance().setAutoApproveFileCreation(actionsPanel.isAutoApproveFileCreation());
            notifyChangeListeners();
        });

        // Prompt panel listeners
        promptPanel.getChatSystemPromptArea().getDocument().addDocumentListener(documentListener);
        promptPanel.getRefactorUserPromptArea().getDocument().addDocumentListener(documentListener);
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

    public void setToolsEnabled(boolean value) {
        actionsPanel.setToolsEnabled(value);
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
}
