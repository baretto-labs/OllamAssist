package fr.baretto.ollamassist.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import fr.baretto.ollamassist.core.agent.ModelCapabilityDiagnostic;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Action pour diagnostiquer les capacités des modèles Ollama pour l'agent mode
 */
@Slf4j
public class DiagnoseModelCapabilitiesAction extends AnAction {

    public DiagnoseModelCapabilitiesAction() {
        super("Diagnose Model Capabilities", "Test model capabilities for Agent Mode", null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        // Modèles à tester
        List<String> modelsToTest = List.of(
                "llama3.1",      // Actuel
                "gpt-oss",       // Recommandé par user
                "llama3.2",      // Plus récent
                "mistral",       // Bon support function calling
                "qwen2.5"        // Très capable
        );

        showProgressNotification("🔍 Testing model capabilities...", project);

        CompletableFuture.runAsync(() -> {
            StringBuilder report = new StringBuilder();
            report.append("🔍 MODEL CAPABILITY DIAGNOSTIC REPORT\n");
            report.append("=====================================\n\n");

            ModelCapabilityDiagnostic diagnostic = new ModelCapabilityDiagnostic(project);

            for (String modelName : modelsToTest) {
                try {
                    report.append("Testing ").append(modelName).append("...\n");

                    ModelCapabilityDiagnostic.ModelCapabilityReport modelReport =
                            diagnostic.diagnoseModel(modelName, "http://localhost:11434");

                    report.append(modelReport.generateReport()).append("\n");
                    report.append("---\n\n");

                } catch (Exception ex) {
                    report.append("❌ Error testing ").append(modelName).append(": ")
                            .append(ex.getMessage()).append("\n\n");
                }
            }

            // Recommandations générales
            report.append("🎯 GENERAL RECOMMENDATIONS\n");
            report.append("=========================\n");
            report.append("Based on current testing:\n\n");

            report.append("For Agent Mode (ReAct pattern):\n");
            report.append("1. BEST: Models with native function calling support\n");
            report.append("2. GOOD: Models with reliable structured JSON output\n");
            report.append("3. AVOID: Models that fail both function calling and structured output\n\n");

            report.append("Recommended models (in order of preference):\n");
            report.append("1. gpt-oss (optimized for Ollama)\n");
            report.append("2. mistral (excellent function calling)\n");
            report.append("3. llama3.2 (latest Llama version)\n");
            report.append("4. qwen2.5 (very capable)\n");
            report.append("5. llama3.1 (fallback only)\n\n");

            // Afficher le rapport complet
            showDetailedReport(report.toString(), project);
        });
    }

    private void showProgressNotification(String message, Project project) {
        Notification notification = new Notification(
                "OllamAssist.Agent",
                "Model Diagnostic",
                message,
                NotificationType.INFORMATION
        );
        Notifications.Bus.notify(notification, project);
    }

    private void showDetailedReport(String report, Project project) {
        SwingUtilities.invokeLater(() -> {
            // Afficher dans une dialog scrollable
            Messages.showInfoMessage(
                    project,
                    report,
                    "Model Capability Diagnostic Report"
            );

            // Aussi logger pour debug
            log.info("Model diagnostic report:\n{}", report);

            // Notification de completion
            Notification notification = new Notification(
                    "OllamAssist.Agent",
                    "Model Diagnostic Complete",
                    "✅ Model capability diagnostic completed. Check the report for recommendations.",
                    NotificationType.INFORMATION
            );
            Notifications.Bus.notify(notification, project);
        });
    }
}