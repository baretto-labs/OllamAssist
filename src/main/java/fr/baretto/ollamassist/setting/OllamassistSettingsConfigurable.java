package fr.baretto.ollamassist.setting;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;

@Slf4j
public class OllamassistSettingsConfigurable implements Configurable, Disposable {

    private ConfigurationPanel configurationPanel;
    private final Project project;
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
                    log.error("Error during OllamassistSettingsConfigurable creation : ", e);
                }
            }
        };

        configurationPanel.addChangeListener(changeListener);

        // Load settings from new separated services
        loadAllSettings();
        return configurationPanel;
    }

    @Override
    public boolean isModified() {
        if (configurationPanel == null) {
            return false;
        }

        // Check if any setting has been modified
        OllamaSettings ollamaSettings = OllamaSettings.getInstance();
        RAGSettings ragSettings = RAGSettings.getInstance();
        ActionsSettings actionsSettings = ActionsSettings.getInstance();
        PromptSettings promptSettings = PromptSettings.getInstance();

        return !ollamaSettings.getChatOllamaUrl().equals(configurationPanel.getChatOllamaUrl())
                || !ollamaSettings.getCompletionOllamaUrl().equals(configurationPanel.getCompletionOllamaUrl())
                || !ollamaSettings.getEmbeddingOllamaUrl().equals(configurationPanel.getEmbeddingOllamaUrl())
                || !ollamaSettings.getChatModelName().equals(configurationPanel.getChatModel())
                || !ollamaSettings.getCompletionModelName().equals(configurationPanel.getCompletionModel())
                || !ollamaSettings.getEmbeddingModelName().equals(configurationPanel.getEmbeddingModel())
                || !ollamaSettings.getUsername().equals(configurationPanel.getUsername())
                || !ollamaSettings.getPassword().equals(configurationPanel.getPassword())
                || !ollamaSettings.getTimeout().equals(configurationPanel.getTimeout())
                || !ragSettings.getSources().equals(configurationPanel.getSources())
                || ragSettings.getIndexationSize() != configurationPanel.getMaxDocuments()
                || actionsSettings.isAutoApproveFileCreation() != configurationPanel.isAutoApproveFileCreation()
                || actionsSettings.isToolsEnabled() != configurationPanel.isToolsEnabled()
                || !promptSettings.getChatSystemPrompt().equals(configurationPanel.getChatSystemPrompt())
                || !promptSettings.getRefactorUserPrompt().equals(configurationPanel.getRefactorUserPrompt());
    }


    @Override
    public void apply() {
        if (isModified()) {
            // Validate prompts before saving
            if (!configurationPanel.validatePrompts()) {
                return;
            }

            boolean needIndexation = needIndexation();
            boolean shouldCleanAllDatabase = shouldCleanAllDatabase();

            // Save to OllamaSettings
            OllamaSettings ollamaSettings = OllamaSettings.getInstance();
            ollamaSettings.setChatOllamaUrl(configurationPanel.getChatOllamaUrl());
            ollamaSettings.setCompletionOllamaUrl(configurationPanel.getCompletionOllamaUrl());
            ollamaSettings.setEmbeddingOllamaUrl(configurationPanel.getEmbeddingOllamaUrl());
            ollamaSettings.setChatModelName(configurationPanel.getChatModel());
            ollamaSettings.setCompletionModelName(configurationPanel.getCompletionModel());
            ollamaSettings.setEmbeddingModelName(configurationPanel.getEmbeddingModel());
            ollamaSettings.setUsername(configurationPanel.getUsername());
            ollamaSettings.setPassword(configurationPanel.getPassword());
            ollamaSettings.setTimeout(configurationPanel.getTimeout());

            // Save to RAGSettings
            RAGSettings ragSettings = RAGSettings.getInstance();
            ragSettings.setSources(configurationPanel.getSources());
            ragSettings.setIndexationSize(configurationPanel.getMaxDocuments());

            // Save to ActionsSettings
            ActionsSettings actionsSettings = ActionsSettings.getInstance();
            actionsSettings.setAutoApproveFileCreation(configurationPanel.isAutoApproveFileCreation());
            actionsSettings.setToolsEnabled(configurationPanel.isToolsEnabled());

            // Save to PromptSettings
            PromptSettings promptSettings = PromptSettings.getInstance();
            promptSettings.setChatSystemPrompt(configurationPanel.getChatSystemPrompt());
            promptSettings.setRefactorUserPrompt(configurationPanel.getRefactorUserPrompt());

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
        RAGSettings ragSettings = RAGSettings.getInstance();
        return configurationPanel.getMaxDocuments() != ragSettings.getIndexationSize();
    }

    private boolean shouldCleanAllDatabase() {
        OllamaSettings ollamaSettings = OllamaSettings.getInstance();

        String panelEmbeddingModel = configurationPanel.getEmbeddingModel();
        String panelEmbeddingUrl = configurationPanel.getEmbeddingOllamaUrl();

        // Null-safe comparison: if panel values are null (async loading), no cleaning needed
        if (panelEmbeddingModel == null || panelEmbeddingUrl == null) {
            return false;
        }

        return !ollamaSettings.getEmbeddingModelName().equals(panelEmbeddingModel) ||
                !ollamaSettings.getEmbeddingOllamaUrl().equals(panelEmbeddingUrl);
    }

    @Override
    public void reset() {
        // Load settings from new separated services
        loadAllSettings();
    }

    private void loadAllSettings() {
        // Load from OllamaSettings
        OllamaSettings ollamaSettings = OllamaSettings.getInstance();
        configurationPanel.setChatOllamaUrl(ollamaSettings.getChatOllamaUrl());
        configurationPanel.setCompletionOllamaUrl(ollamaSettings.getCompletionOllamaUrl());
        configurationPanel.setEmbeddingOllamaUrl(ollamaSettings.getEmbeddingOllamaUrl());
        configurationPanel.setChatModelName(ollamaSettings.getChatModelName());
        configurationPanel.setCompletionModelName(ollamaSettings.getCompletionModelName());
        configurationPanel.setEmbeddingModelName(ollamaSettings.getEmbeddingModelName());
        configurationPanel.setUsername(ollamaSettings.getUsername());
        configurationPanel.setPassword(ollamaSettings.getPassword());
        configurationPanel.setTimeout(ollamaSettings.getTimeout());

        // Load from RAGSettings
        RAGSettings ragSettings = RAGSettings.getInstance();
        configurationPanel.setSources(ragSettings.getSources());
        configurationPanel.setMaxDocuments(ragSettings.getIndexationSize());

        // Load from ActionsSettings
        ActionsSettings actionsSettings = ActionsSettings.getInstance();
        configurationPanel.setAutoApproveFileCreation(actionsSettings.isAutoApproveFileCreation());
        configurationPanel.setToolsEnabled(actionsSettings.isToolsEnabled());

        // Load from PromptSettings
        PromptSettings promptSettings = PromptSettings.getInstance();
        configurationPanel.setChatSystemPrompt(promptSettings.getChatSystemPrompt());
        configurationPanel.setRefactorUserPrompt(promptSettings.getRefactorUserPrompt());
    }

    @Override
    public void dispose() {
        if (changeListener != null) {
            configurationPanel.removeChangeListener(changeListener);
        }
        configurationPanel = null;
    }
}