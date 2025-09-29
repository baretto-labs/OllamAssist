package fr.baretto.ollamassist.setting.agent;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Configurable pour les paramètres du mode agent
 */
public class AgentModeConfigurable implements Configurable, Disposable {

    private AgentModeConfigPanel configPanel;

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "Agent Mode";
    }

    @Override
    public @Nullable String getHelpTopic() {
        return "agent.mode.settings";
    }

    @Override
    @Nullable
    public JComponent createComponent() {
        if (configPanel == null) {
            configPanel = new AgentModeConfigPanel();
        }
        return configPanel;
    }

    @Override
    public boolean isModified() {
        return configPanel != null && configPanel.isModified();
    }

    @Override
    public void apply() {
        if (configPanel != null) {
            if (!configPanel.validateSettings()) {
                return; // Validation failed, don't apply
            }
            configPanel.saveSettings();
        }
    }

    @Override
    public void reset() {
        if (configPanel != null) {
            configPanel.loadSettings();
        }
    }

    @Override
    public void disposeUIResources() {
        configPanel = null;
    }

    @Override
    public void dispose() {
        disposeUIResources();
    }
}