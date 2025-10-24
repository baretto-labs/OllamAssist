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
                    log.error("Error during OllamassistSettingsConfigurable creation : ", e);
                }
            }
        };

        configurationPanel.addChangeListener(changeListener);

        // Load settings using automatic binding
        OllamAssistSettings settings = OllamAssistSettings.getInstance();
        SettingsBindingHelper.loadSettings(settings.getState(), configurationPanel);
        return configurationPanel;
    }

    @Override
    public boolean isModified() {
        if (configurationPanel == null) {
            return false;
        }
        OllamAssistSettings settings = OllamAssistSettings.getInstance();
        return SettingsBindingHelper.isModified(settings.getState(), configurationPanel);
    }


    @Override
    public void apply() {
        OllamAssistSettings settings = OllamAssistSettings.getInstance();

        if (isModified()) {
            boolean needIndexation = needIndexation();
            boolean shouldCleanAllDatabase = shouldCleanAllDatabase();

            // Save settings using automatic binding
            SettingsBindingHelper.saveSettings(configurationPanel, settings.getState());

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

        String panelEmbeddingModel = configurationPanel.getEmbeddingModel();
        String panelEmbeddingUrl = configurationPanel.getEmbeddingOllamaUrl();

        // Null-safe comparison: if panel values are null (async loading), no cleaning needed
        if (panelEmbeddingModel == null || panelEmbeddingUrl == null) {
            return false;
        }

        return !settings.getEmbeddingModelName().equals(panelEmbeddingModel) ||
                !settings.getEmbeddingOllamaUrl().equals(panelEmbeddingUrl);
    }

    @Override
    public void reset() {
        // Load settings using automatic binding
        OllamAssistSettings settings = OllamAssistSettings.getInstance();
        SettingsBindingHelper.loadSettings(settings.getState(), configurationPanel);
    }

    @Override
    public void dispose() {
        if (changeListener != null) {
            configurationPanel.removeChangeListener(changeListener);
        }
        configurationPanel = null;
    }
}