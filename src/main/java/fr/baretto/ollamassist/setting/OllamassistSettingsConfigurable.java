package fr.baretto.ollamassist.setting;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;

public class OllamassistSettingsConfigurable implements Configurable, Disposable {

    private final Project project;
    private ConfigurationPanel configurationPanel;
    private Consumer<Boolean> changeListener;

    public OllamassistSettingsConfigurable(Project project) {
        this.project = project;
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "OllamAssist Settings";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        configurationPanel = new ConfigurationPanel(project);

        changeListener = modified -> {
            if (modified) {
                try {
                    Method method = Configurable.class.getDeclaredMethod("fireConfigurationChanged");
                    method.setAccessible(true);
                    method.invoke(this);
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {

                }
            }
        };

        configurationPanel.addChangeListener(changeListener);

        OllamAssistSettings settings = OllamAssistSettings.getInstance();
        configurationPanel.setChatOllamaUrl(settings.getChatOllamaUrl());
        configurationPanel.setCompletionOllamaUrl(settings.getCompletionOllamaUrl());
        configurationPanel.setEmbeddingOllamaUrl(settings.getEmbeddingOllamaUrl());
        configurationPanel.setChatModelName(settings.getChatModelName());
        configurationPanel.setCompletionModelName(settings.getCompletionModelName());
        configurationPanel.setEmbeddingModelName(settings.getEmbeddingModelName());
        configurationPanel.setTimeout(settings.getTimeout());
        configurationPanel.setSources(settings.getSources());
        configurationPanel.setMaxDocuments(settings.getIndexationSize());

        // === Paramètres agent ===
        configurationPanel.setAgentModeEnabled(settings.isAgentModeEnabled());
        configurationPanel.setAgentSecurityLevel(settings.getAgentSecurityLevel());
        configurationPanel.setAgentMaxTasksPerSession(settings.getAgentMaxTasksPerSession());
        configurationPanel.setAgentAutoApprovalEnabled(settings.isAgentAutoApprovalEnabled());

        return configurationPanel;
    }

    @Override
    public boolean isModified() {
        if (configurationPanel == null ||
                configurationPanel.getChatModel() == null ||
                configurationPanel.getCompletionModel() == null ||
                configurationPanel.getEmbeddingModel() == null) {
            return false;
        }
        OllamAssistSettings settings = OllamAssistSettings.getInstance();
        return !configurationPanel.getChatOllamaUrl().equalsIgnoreCase(settings.getChatOllamaUrl()) ||
                !configurationPanel.getCompletionOllamaUrl().equalsIgnoreCase(settings.getCompletionOllamaUrl()) ||
                !configurationPanel.getEmbeddingOllamaUrl().equalsIgnoreCase(settings.getEmbeddingOllamaUrl()) ||
                !configurationPanel.getChatModel().equalsIgnoreCase(settings.getChatModelName()) ||
                !configurationPanel.getCompletionModel().equalsIgnoreCase(settings.getCompletionModelName()) ||
                !configurationPanel.getEmbeddingModel().equalsIgnoreCase(settings.getEmbeddingModelName()) ||
                !configurationPanel.getTimeout().equalsIgnoreCase(settings.getTimeout()) ||
                !configurationPanel.getSources().equalsIgnoreCase(settings.getSources()) ||
                configurationPanel.getMaxDocuments() != settings.getIndexationSize() ||
                // === Paramètres agent ===
                configurationPanel.isAgentModeEnabled() != settings.isAgentModeEnabled() ||
                configurationPanel.getAgentSecurityLevel() != settings.getAgentSecurityLevel() ||
                configurationPanel.getAgentMaxTasksPerSession() != settings.getAgentMaxTasksPerSession() ||
                configurationPanel.isAgentAutoApprovalEnabled() != settings.isAgentAutoApprovalEnabled();
    }


    @Override
    public void apply() {
        OllamAssistSettings settings = OllamAssistSettings.getInstance();

        if (isModified()) {
            boolean needIndexation = needIndexation();
            boolean shouldCleanAllDatabase = shouldCleanAllDatabase();
            settings.setChatOllamaUrl(configurationPanel.getChatOllamaUrl());
            settings.setCompletionOllamaUrl(configurationPanel.getCompletionOllamaUrl());
            settings.setEmbeddingOllamaUrl(configurationPanel.getEmbeddingOllamaUrl());
            settings.setChatModelName(configurationPanel.getChatModel());
            settings.setCompletionModelName(configurationPanel.getCompletionModel());
            settings.setEmbeddingModelName(configurationPanel.getEmbeddingModel());
            settings.setTimeout(configurationPanel.getTimeout());
            settings.setSources(configurationPanel.getSources());
            settings.setIndexationSize(configurationPanel.getMaxDocuments());

            // === Paramètres agent ===
            settings.setAgentModeEnabled(configurationPanel.isAgentModeEnabled());
            settings.setAgentSecurityLevel(configurationPanel.getAgentSecurityLevel());
            settings.setAgentMaxTasksPerSession(configurationPanel.getAgentMaxTasksPerSession());
            settings.setAgentAutoApprovalEnabled(configurationPanel.isAgentAutoApprovalEnabled());

            ApplicationManager.getApplication().getMessageBus()
                    .syncPublisher(ModelListener.TOPIC)
                    .reloadModel();

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
        return !settings.getEmbeddingModelName().equals(configurationPanel.getEmbeddingModel()) ||
                !settings.getEmbeddingOllamaUrl().equals(configurationPanel.getEmbeddingOllamaUrl());

    }

    @Override
    public void reset() {
        OllamAssistSettings settings = OllamAssistSettings.getInstance();
        configurationPanel.setChatOllamaUrl(settings.getChatOllamaUrl().trim());
        configurationPanel.setCompletionOllamaUrl(settings.getCompletionOllamaUrl().trim());
        configurationPanel.setEmbeddingOllamaUrl(settings.getEmbeddingOllamaUrl().trim());
        configurationPanel.setChatModelName(settings.getChatModelName().trim());
        configurationPanel.setCompletionModelName(settings.getCompletionModelName().trim());
        configurationPanel.setEmbeddingModelName(settings.getEmbeddingModelName());
        configurationPanel.setTimeout(settings.getTimeout().trim());
        configurationPanel.setSources(settings.getSources().trim());
        configurationPanel.setMaxDocuments(settings.getIndexationSize());

        // === Paramètres agent ===
        configurationPanel.setAgentModeEnabled(settings.isAgentModeEnabled());
        configurationPanel.setAgentSecurityLevel(settings.getAgentSecurityLevel());
        configurationPanel.setAgentMaxTasksPerSession(settings.getAgentMaxTasksPerSession());
        configurationPanel.setAgentAutoApprovalEnabled(settings.isAgentAutoApprovalEnabled());
    }

    @Override
    public void dispose() {
        if (changeListener != null) {
            configurationPanel.removeChangeListener(changeListener);
        }
        configurationPanel = null;
    }
}