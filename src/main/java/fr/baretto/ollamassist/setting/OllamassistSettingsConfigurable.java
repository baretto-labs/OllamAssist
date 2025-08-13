package fr.baretto.ollamassist.setting;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class OllamassistSettingsConfigurable implements Configurable, Disposable {

    private ConfigurationPanel configurationPanel;

    public OllamassistSettingsConfigurable(Project project) {
        configurationPanel = new ConfigurationPanel(project);
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "OllamAssist Settings";
    }

    @Nullable
    @Override
    public JComponent createComponent() {

        OllamAssistSettings settings = OllamAssistSettings.getInstance();
        configurationPanel.setOllamaUrl(settings.getOllamaUrl());
        configurationPanel.setChatModelName(settings.getChatModelName());
        configurationPanel.setCompletionModelName(settings.getCompletionModelName());
        configurationPanel.setEmbeddingModelName(settings.getEmbeddingModelName());
        configurationPanel.setTimeout(settings.getTimeout());
        configurationPanel.setSources(settings.getSources());
        configurationPanel.setMaxDocuments(settings.getIndexationSize());
        return configurationPanel;
    }

    @Override
    public boolean isModified() {
        OllamAssistSettings settings = OllamAssistSettings.getInstance();
        return !configurationPanel.getOllamaUrl().equalsIgnoreCase(settings.getOllamaUrl()) ||
                !configurationPanel.getChatModel().equalsIgnoreCase(settings.getChatModelName()) ||
                !configurationPanel.getCompletionModel().equalsIgnoreCase(settings.getCompletionModelName()) ||
                !configurationPanel.getEmbeddingModel().equalsIgnoreCase(settings.getEmbeddingModelName()) ||
                !configurationPanel.getTimeout().equalsIgnoreCase(settings.getTimeout()) ||
                !configurationPanel.getSources().equalsIgnoreCase(settings.getSources()) ||
                configurationPanel.getMaxDocuments() != settings.getIndexationSize();
    }


    @Override
    public void apply() {
        OllamAssistSettings settings = OllamAssistSettings.getInstance();

        if (isModified()) {
            boolean needIndexation = needIndexation();
            boolean shouldCleanAllDatabase = shouldCleanAllDatabase();
            settings.setOllamaUrl(configurationPanel.getOllamaUrl());
            settings.setChatModelName(configurationPanel.getChatModel());
            settings.setCompletionModelName(configurationPanel.getCompletionModel());
            settings.setEmbeddingModelName(configurationPanel.getEmbeddingModel());
            settings.setTimeout(configurationPanel.getTimeout());
            settings.setSources(configurationPanel.getSources());
            settings.setIndexationSize(configurationPanel.getMaxDocuments());

            ApplicationManager.getApplication().getMessageBus()
                    .syncPublisher(SettingsListener.TOPIC)
                    .settingsChanged(settings.getState());

            if (shouldCleanAllDatabase) {
                configurationPanel.triggerCleanAllDatabase();
                return;
            }

            if (needIndexation) {
                configurationPanel.triggerClearLocalStorage();
            }

        }
    }

    private boolean needIndexation() {
        OllamAssistSettings settings = OllamAssistSettings.getInstance();
        return configurationPanel.getMaxDocuments() != settings.getIndexationSize();
    }

    private boolean shouldCleanAllDatabase() {
        OllamAssistSettings settings = OllamAssistSettings.getInstance();
        return !settings.getEmbeddingModelName().equals(configurationPanel.getEmbeddingModel());

    }

    @Override
    public void reset() {
        OllamAssistSettings settings = OllamAssistSettings.getInstance();
        configurationPanel.setOllamaUrl(settings.getOllamaUrl().trim());
        configurationPanel.setChatModelName(settings.getChatModelName().trim());
        configurationPanel.setCompletionModelName(settings.getCompletionModelName().trim());
        configurationPanel.setEmbeddingModelName(settings.getEmbeddingModelName());
        configurationPanel.setTimeout(settings.getTimeout().trim());
        configurationPanel.setSources(settings.getSources().trim());
        configurationPanel.setMaxDocuments(settings.getIndexationSize());
    }

    @Override
    public void dispose() {
        configurationPanel = null;
    }
}
