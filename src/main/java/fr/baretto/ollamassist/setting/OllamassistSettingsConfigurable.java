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

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "OllamAssist Settings";
    }

    public OllamassistSettingsConfigurable(Project project){
        configurationPanel = new ConfigurationPanel(project);
    }
    @Nullable
    @Override
    public JComponent createComponent() {

        OllamAssistSettings settings = OllamAssistSettings.getInstance();
        configurationPanel.setChatModelName(settings.getChatModelName());
        configurationPanel.setCompletionModelName(settings.getCompletionModelName());
        configurationPanel.setSources(settings.getSources());
        return configurationPanel;
    }

    @Override
    public boolean isModified() {
        OllamAssistSettings settings = OllamAssistSettings.getInstance();
        return !configurationPanel.getChatModel().equalsIgnoreCase(settings.getChatModelName()) ||
                !configurationPanel.getCompletionModel().equalsIgnoreCase(settings.getCompletionModelName()) ||
                !configurationPanel.getSources().equalsIgnoreCase(settings.getSources());
    }


    @Override
    public void apply() {
        OllamAssistSettings settings = OllamAssistSettings.getInstance();
        boolean modified = isModified();

        if (modified) {

            settings.setChatModelName(configurationPanel.getChatModel());
            settings.setCompletionModelName(configurationPanel.getCompletionModel());
            settings.setSources(configurationPanel.getSources());


            ApplicationManager.getApplication().getMessageBus()
                    .syncPublisher(SettingsListener.TOPIC)
                    .settingsChanged(settings.getState());
        }
    }

    @Override
    public void reset() {
        OllamAssistSettings settings = OllamAssistSettings.getInstance();
        configurationPanel.setChatModelName(settings.getChatModelName().trim());
        configurationPanel.setCompletionModelName(settings.getCompletionModelName().trim());
        configurationPanel.setSources(settings.getSources().trim());
    }

    @Override
    public void dispose() {
        configurationPanel = null;
    }
}
