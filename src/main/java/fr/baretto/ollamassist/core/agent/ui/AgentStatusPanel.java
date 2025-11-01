package fr.baretto.ollamassist.core.agent.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import fr.baretto.ollamassist.core.agent.AgentService;
import fr.baretto.ollamassist.core.agent.AgentTaskNotifier;
import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import fr.baretto.ollamassist.setting.agent.AgentModeSettings;

import java.util.List;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Panel compact pour afficher l'état du mode agent
 * Affiché en haut de l'interface OllamAssist quand le mode agent est actif
 */
public class AgentStatusPanel extends JBPanel<AgentStatusPanel> {

    private final Project project;
    private final AgentModeSettings agentSettings;
    private final JBLabel statusLabel;
    private final JBLabel configLabel;
    private final JButton toggleButton;
    private final TaskProgressIndicator progressIndicator;
    private Timer refreshTimer;
    private MessageBusConnection messageBusConnection;
    private volatile boolean isAgentProcessing = false;

    public AgentStatusPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.agentSettings = AgentModeSettings.getInstance();

        // Components
        statusLabel = new JBLabel();
        configLabel = new JBLabel();
        toggleButton = new JButton();
        progressIndicator = new TaskProgressIndicator();

        setupUI();
        setupRefreshTimer();
        setupAgentEventListeners();
        updateStatus();
    }

    private void setupUI() {
        setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.GRAY, 0, 0, 1, 0),
                JBUI.Borders.empty(6, 8)
        ));
        setMinimumSize(new Dimension(400, 50));
        setPreferredSize(new Dimension(600, 60));
        setBackground(UIUtil.getPanelBackground());

        // Left panel - Status (using vertical layout for better readability)
        JPanel leftPanel = new JBPanel<>(new BorderLayout());
        leftPanel.setOpaque(false);

        JPanel statusPanel = new JBPanel<>(new FlowLayout(FlowLayout.LEFT, 5, 0));
        statusPanel.setOpaque(false);

        JPanel configPanel = new JBPanel<>(new FlowLayout(FlowLayout.LEFT, 5, 0));
        configPanel.setOpaque(false);

        statusLabel.setIcon(AllIcons.Actions.Lightning);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 11f));

        configLabel.setFont(configLabel.getFont().deriveFont(Font.PLAIN, 11f));
        configLabel.setForeground(UIUtil.getLabelForeground());

        statusPanel.add(statusLabel);
        configPanel.add(configLabel);

        leftPanel.add(statusPanel, BorderLayout.NORTH);
        leftPanel.add(configPanel, BorderLayout.CENTER);

        // Right panel - Controls
        JPanel rightPanel = new JBPanel<>(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        rightPanel.setOpaque(false);

        toggleButton.setFont(toggleButton.getFont().deriveFont(Font.PLAIN, 10f));
        toggleButton.setPreferredSize(new Dimension(80, 20));
        toggleButton.addActionListener(this::onToggleClicked);

        rightPanel.add(toggleButton);

        // Main status panel
        JPanel mainPanel = new JBPanel<>(new BorderLayout());
        mainPanel.add(leftPanel, BorderLayout.CENTER);
        mainPanel.add(rightPanel, BorderLayout.EAST);

        // Layout with progress indicator
        add(mainPanel, BorderLayout.NORTH);

        // Progress indicator (initially hidden)
        progressIndicator.setVisible(false);
        add(progressIndicator, BorderLayout.CENTER);

        // Start collapsed if agent mode is disabled
        setVisible(agentSettings.isAgentModeEnabled());
    }

    private void setupRefreshTimer() {
        // Refresh every 2 seconds to keep status up to date
        refreshTimer = new Timer(2000, e -> updateStatus());
        refreshTimer.start();
    }

    /**
     * FIX: Subscribe to agent events for real-time status updates
     */
    private void setupAgentEventListeners() {
        messageBusConnection = project.getMessageBus().connect();
        messageBusConnection.subscribe(AgentTaskNotifier.TOPIC, new AgentTaskNotifier() {
            @Override
            public void taskStarted(Task task) {
                // Not displayed here
            }

            @Override
            public void taskCompleted(Task task, TaskResult result) {
                // Not displayed here
            }

            @Override
            public void taskProgress(Task task, String progressMessage) {
                // Not displayed here
            }

            @Override
            public void taskCancelled(Task task) {
                // Not displayed here
            }

            @Override
            public void agentProcessingStarted(String userRequest) {
                isAgentProcessing = true;
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Agent Mode: En cours...");
                    statusLabel.setForeground(JBColor.BLUE);
                    statusLabel.setIcon(AllIcons.Process.ProgressResumeHover);
                    progressIndicator.setVisible(true);
                    progressIndicator.updateProgress(0, "Processing: " + userRequest);
                });
            }

            @Override
            public void agentProcessingCompleted(String userRequest, String response) {
                isAgentProcessing = false;
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Agent Mode: Actif");
                    statusLabel.setForeground(new JBColor(new Color(0, 128, 0), new Color(0, 255, 0)));
                    statusLabel.setIcon(AllIcons.Actions.Lightning);
                    finishTaskProgress(true, "Completed");
                });
            }

            @Override
            public void agentProcessingFailed(String userRequest, String errorMessage) {
                isAgentProcessing = false;
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Agent Mode: Erreur");
                    statusLabel.setForeground(JBColor.RED);
                    statusLabel.setIcon(AllIcons.General.Error);
                    finishTaskProgress(false, "Failed: " + errorMessage);
                });
            }

            @Override
            public void agentStreamingToken(String token) {
                // Not displayed here
            }

            @Override
            public void agentProposalRequested(String userRequest, List<Task> proposedTasks, ActionProposalCard.ActionValidator validator) {
                // Not displayed here
            }
        });
    }

    private void updateStatus() {
        SwingUtilities.invokeLater(() -> {
            boolean agentAvailable = agentSettings.isAgentModeAvailable();
            boolean agentEnabled = agentSettings.isAgentModeEnabled();

            // Update visibility
            setVisible(agentEnabled);

            if (!agentEnabled) {
                return;
            }

            // Update status
            if (agentAvailable) {
                statusLabel.setText("Agent Mode: Actif");
                statusLabel.setForeground(new JBColor(new Color(0, 128, 0), new Color(0, 255, 0)));
                statusLabel.setIcon(AllIcons.Actions.Lightning);
            } else {
                statusLabel.setText("Agent Mode: Configuration requise");
                statusLabel.setForeground(JBColor.ORANGE);
                statusLabel.setIcon(AllIcons.General.Warning);
            }

            // Update config summary
            String summary = agentSettings.getConfigurationSummary();
            configLabel.setText(summary);

            // Update button
            toggleButton.setText(agentEnabled ? "Désactiver" : "Activer");
            toggleButton.setEnabled(true);

            // Update tooltip with detailed info
            AgentService agentService = project.getService(AgentService.class);
            if (agentService != null && agentService.isAvailable()) {
                String toolsInfo = String.format("Tools: %s | Native: %s",
                        "6 outils disponibles",
                        agentService.isUsingNativeTools() ? "Oui" : "JSON Fallback");
                setToolTipText(summary + " | " + toolsInfo);
            } else {
                setToolTipText(summary);
            }
        });
    }

    private void onToggleClicked(ActionEvent e) {
        boolean currentState = agentSettings.isAgentModeEnabled();

        if (currentState) {
            // Confirmation avant désactivation
            int result = JOptionPane.showConfirmDialog(
                    this,
                    "Désactiver le mode agent ?\nVous pourrez toujours utiliser le chat normal.",
                    "Confirmer désactivation",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );

            if (result == JOptionPane.YES_OPTION) {
                agentSettings.disableAgentMode();
            }
        } else {
            agentSettings.enableAgentMode();
        }

        updateStatus();
    }

    /**
     * Force une mise à jour immédiate du status
     */
    public void refreshStatus() {
        updateStatus();
    }

    /**
     * Affiche un message temporaire dans le status
     */
    public void showTemporaryMessage(String message, int durationMs) {
        String originalText = statusLabel.getText();
        Color originalColor = statusLabel.getForeground();

        statusLabel.setText(message);
        statusLabel.setForeground(JBColor.BLUE);

        Timer resetTimer = new Timer(durationMs, e -> {
            statusLabel.setText(originalText);
            statusLabel.setForeground(originalColor);
        });
        resetTimer.setRepeats(false);
        resetTimer.start();
    }

    /**
     * Démarre l'affichage de progression pour une tâche
     */
    public void startTaskProgress(String taskName) {
        SwingUtilities.invokeLater(() -> {
            progressIndicator.setVisible(true);
            progressIndicator.showTemporaryStatus("Starting " + taskName, 2000);
            revalidate();
            repaint();
        });
    }

    /**
     * Met à jour la progression d'une tâche
     */
    public void updateTaskProgress(int percentage, String message) {
        SwingUtilities.invokeLater(() -> {
            if (progressIndicator.isVisible()) {
                progressIndicator.updateProgress(percentage, message);
            }
        });
    }

    /**
     * Termine l'affichage de progression
     */
    public void finishTaskProgress(boolean success, String message) {
        SwingUtilities.invokeLater(() -> {
            if (success) {
                progressIndicator.showTemporaryStatus(message, 3000);
            } else {
                progressIndicator.showTemporaryStatus(message, 5000);
            }

            // Hide progress after delay
            Timer hideTimer = new Timer(success ? 3000 : 5000, e -> {
                progressIndicator.setVisible(false);
                revalidate();
                repaint();
            });
            hideTimer.setRepeats(false);
            hideTimer.start();
        });
    }

    /**
     * Exécute une tâche avec affichage de progression
     */
    public void executeTaskWithProgress(String taskName, Runnable task) {
        startTaskProgress(taskName);

        progressIndicator.executeIntelliJTask(project, taskName, () -> {
            try {
                task.run();
                finishTaskProgress(true, taskName + " completed successfully");
            } catch (Exception e) {
                finishTaskProgress(false, taskName + " failed: " + e.getMessage());
            }
        });
    }

    /**
     * Nettoyage des ressources
     */
    public void dispose() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        if (progressIndicator != null) {
            progressIndicator.dispose();
        }
        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
        }
    }
}