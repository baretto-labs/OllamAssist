package fr.baretto.ollamassist.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import fr.baretto.ollamassist.setting.agent.AgentModeSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Action pour ouvrir le mode agent OllamAssist
 * Accessible via Tools → OllamAssist → Agent Mode
 */
public class AgentModeAction extends AnAction {

    public AgentModeAction() {
        super("Agent Mode", "Ouvrir le mode agent OllamAssist", AllIcons.Actions.Lightning);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        // Ouvrir le tool window OllamAssist
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        ToolWindow toolWindow = toolWindowManager.getToolWindow("OllamAssist");

        if (toolWindow != null) {
            toolWindow.activate(() -> {
                // Focus sur le mode agent - la logique sera ajoutée dans OllamaContent
                // pour détecter si on est en mode agent via AgentModeSettings
            });
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Presentation presentation = e.getPresentation();

        if (project == null) {
            presentation.setEnabledAndVisible(false);
            return;
        }

        AgentModeSettings settings = AgentModeSettings.getInstance();
        boolean agentModeAvailable = settings.isAgentModeAvailable();

        presentation.setEnabledAndVisible(true);
        presentation.setEnabled(agentModeAvailable);

        if (!agentModeAvailable) {
            presentation.setDescription("Mode agent désactivé - Activez-le dans Settings → OllamAssist → Agent Mode");
        } else {
            presentation.setDescription("Ouvrir le mode agent OllamAssist (" + settings.getConfigurationSummary() + ")");
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}