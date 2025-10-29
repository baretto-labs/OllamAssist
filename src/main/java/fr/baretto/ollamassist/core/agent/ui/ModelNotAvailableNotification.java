package fr.baretto.ollamassist.core.agent.ui;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.core.agent.ModelAvailabilityChecker;
import fr.baretto.ollamassist.setting.agent.AgentModeConfigurable;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

/**
 * Notification when agent model is not available
 * Provides instructions to download the model or configure agent settings
 */
@Slf4j
public class ModelNotAvailableNotification {

    private static final String NOTIFICATION_GROUP_ID = "OllamAssist Agent Model";

    /**
     * Shows a notification when the agent model is not available
     */
    public static void showModelNotAvailable(
            @NotNull Project project,
            @NotNull ModelAvailabilityChecker.ModelAvailabilityResult result) {

        NotificationGroup group = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID);

        if (group == null) {
            // Fallback: log warning instead of creating notification (NotificationGroup not registered in plugin.xml)
            log.warn("Cannot show model unavailable notification (NotificationGroup not registered): {}", result.getUserMessage());
            return;
        }

        String title = "Modèle Agent Non Disponible";
        String content = buildNotificationContent(result);

        Notification notification = group.createNotification(title, content, NotificationType.ERROR);

        // Add action to copy the pull command
        if (result.isNotAvailable() && result.getModelName() != null) {
            String pullCommand = "ollama pull " + result.getModelName();

            notification.addAction(new NotificationAction("Copier la commande") {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                    CopyPasteManager.getInstance().setContents(new StringSelection(pullCommand));
                    // Show a small success notification
                    NotificationGroup successGroup = NotificationGroupManager.getInstance()
                            .getNotificationGroup(NOTIFICATION_GROUP_ID);
                    successGroup.createNotification(
                            "Commande copiée dans le presse-papiers",
                            "Collez-la dans votre terminal pour télécharger le modèle.",
                            NotificationType.INFORMATION
                    ).notify(project);
                }
            });
        }

        // Add action to open settings
        notification.addAction(new NotificationAction("Configurer le modèle") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, AgentModeConfigurable.class);
            }
        });

        // Add action to disable agent mode
        notification.addAction(new NotificationAction("Désactiver le mode agent") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                fr.baretto.ollamassist.setting.agent.AgentModeSettings.getInstance().disableAgentMode();
                notification.expire();

                // Show confirmation
                NotificationGroup confirmGroup = NotificationGroupManager.getInstance()
                        .getNotificationGroup(NOTIFICATION_GROUP_ID);
                confirmGroup.createNotification(
                        "Mode agent désactivé",
                        "Vous pouvez le réactiver dans les paramètres.",
                        NotificationType.INFORMATION
                ).notify(project);
            }
        });

        notification.notify(project);
    }

    /**
     * Shows a notification when there's an error checking model availability
     */
    public static void showModelCheckError(
            @NotNull Project project,
            @NotNull ModelAvailabilityChecker.ModelAvailabilityResult result) {

        NotificationGroup group = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID);

        if (group == null) {
            // Fallback: log warning instead of creating notification (NotificationGroup not registered in plugin.xml)
            log.warn("Cannot show model check error notification (NotificationGroup not registered): {}", result.getErrorMessage());
            return;
        }

        String title = "Erreur de vérification du modèle";
        String content = buildErrorContent(result);

        Notification notification = group.createNotification(title, content, NotificationType.ERROR);

        // Add action to check Ollama status
        notification.addAction(new NotificationAction("Vérifier Ollama") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                ModelAvailabilityChecker checker = new ModelAvailabilityChecker();
                boolean reachable = checker.isOllamaReachable();

                NotificationGroup statusGroup = NotificationGroupManager.getInstance()
                        .getNotificationGroup(NOTIFICATION_GROUP_ID);

                if (reachable) {
                    statusGroup.createNotification(
                            "Ollama est accessible",
                            "Ollama fonctionne correctement. Le problème vient peut-être du modèle configuré.",
                            NotificationType.INFORMATION
                    ).notify(project);
                } else {
                    statusGroup.createNotification(
                            "Ollama n'est pas accessible",
                            "Vérifiez qu'Ollama est lancé avec la commande: ollama serve",
                            NotificationType.WARNING
                    ).notify(project);
                }
            }
        });

        // Add action to open settings
        notification.addAction(new NotificationAction("Ouvrir les paramètres") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, AgentModeConfigurable.class);
            }
        });

        notification.notify(project);
    }

    /**
     * Shows a notification when the model is not configured
     */
    public static void showModelNotConfigured(@NotNull Project project) {
        NotificationGroup group = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID);

        if (group == null) {
            // Fallback: log warning instead of creating notification (NotificationGroup not registered in plugin.xml)
            log.warn("Cannot show model not configured notification (NotificationGroup not registered): Agent model not configured, use gpt-oss");
            return;
        }

        String title = "Modèle Agent Non Configuré";
        String content = "Aucun modèle n'est configuré pour le mode agent.\n" +
                "Modèle recommandé: <b>gpt-oss</b>";

        Notification notification = group.createNotification(title, content, NotificationType.WARNING);

        notification.addAction(new NotificationAction("Configurer maintenant") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, AgentModeConfigurable.class);
            }
        });

        notification.notify(project);
    }

    /**
     * Builds notification content for model not available
     */
    private static String buildNotificationContent(ModelAvailabilityChecker.ModelAvailabilityResult result) {
        if (result.isNotAvailable()) {
            String modelName = result.getModelName();
            StringBuilder content = new StringBuilder();

            content.append("Le modèle <b>").append(modelName).append("</b> n'est pas disponible sur votre système.<br><br>");
            content.append("<b>Pour télécharger le modèle :</b><br>");
            content.append("1. Ouvrez un terminal<br>");
            content.append("2. Exécutez : <code>ollama pull ").append(modelName).append("</code><br>");
            content.append("3. Attendez la fin du téléchargement<br>");
            content.append("4. Relancez le plugin<br><br>");

            if (result.getAvailableModels() != null && !result.getAvailableModels().isEmpty()) {
                content.append("<b>Modèles disponibles :</b> ");
                content.append(String.join(", ", result.getAvailableModels()));
            }

            return content.toString();
        }

        return result.getUserMessage();
    }

    /**
     * Builds notification content for error
     */
    private static String buildErrorContent(ModelAvailabilityChecker.ModelAvailabilityResult result) {
        StringBuilder content = new StringBuilder();

        content.append("Impossible de vérifier la disponibilité du modèle <b>")
                .append(result.getModelName())
                .append("</b>.<br><br>");

        content.append("<b>Erreur :</b> ").append(result.getErrorMessage()).append("<br><br>");

        content.append("<b>Solutions possibles :</b><br>");
        content.append("• Vérifiez qu'Ollama est lancé : <code>ollama serve</code><br>");
        content.append("• Vérifiez l'URL Ollama dans les paramètres<br>");
        content.append("• Vérifiez votre connexion réseau<br>");
        content.append("• Consultez les logs d'Ollama pour plus de détails");

        return content.toString();
    }
}
