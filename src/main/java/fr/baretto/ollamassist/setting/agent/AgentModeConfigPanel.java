package fr.baretto.ollamassist.setting.agent;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;

/**
 * Panel de configuration pour le mode agent
 */
public class AgentModeConfigPanel extends JBPanel<AgentModeConfigPanel> {

    private final JBCheckBox agentModeEnabledCheckBox;
    private final JBCheckBox autoTaskApprovalCheckBox;
    private final JBTextField maxTasksField;
    private final JBCheckBox snapshotEnabledCheckBox;
    private final JBCheckBox mcpIntegrationEnabledCheckBox;
    private final ComboBox<AgentModeSettings.AgentSecurityLevel> securityLevelComboBox;
    private final JBCheckBox taskProgressUIEnabledCheckBox;

    // ✨ Agent model configuration
    private final JBTextField agentModelNameField;
    private final JBTextField agentOllamaUrlField;
    private final JButton checkModelButton;
    private final JBLabel modelStatusLabel;

    private final JBLabel configSummaryLabel;
    private final AgentModeSettings settings;

    public AgentModeConfigPanel() {
        super(new BorderLayout());
        this.settings = AgentModeSettings.getInstance();

        // Initialize components
        agentModeEnabledCheckBox = new JBCheckBox("Activer le mode agent");
        autoTaskApprovalCheckBox = new JBCheckBox("Auto-approbation des tâches (mode expert)");
        maxTasksField = new JBTextField(10);
        snapshotEnabledCheckBox = new JBCheckBox("Activer les snapshots pour rollback");
        mcpIntegrationEnabledCheckBox = new JBCheckBox("Activer l'intégration MCP");
        securityLevelComboBox = new ComboBox<>(AgentModeSettings.AgentSecurityLevel.values());
        taskProgressUIEnabledCheckBox = new JBCheckBox("Afficher l'interface de progression des tâches");

        // ✨ Agent model configuration
        agentModelNameField = new JBTextField(20);
        agentOllamaUrlField = new JBTextField(30);
        checkModelButton = new JButton("Vérifier la disponibilité");
        modelStatusLabel = new JBLabel();
        modelStatusLabel.setFont(modelStatusLabel.getFont().deriveFont(Font.ITALIC));

        configSummaryLabel = new JBLabel();
        configSummaryLabel.setFont(configSummaryLabel.getFont().deriveFont(Font.ITALIC));
        configSummaryLabel.setForeground(JBColor.GRAY);

        setupUI();
        setupListeners();
        loadSettings();
        updateConfigSummary();
    }

    private void setupUI() {
        // ✨ Model configuration panel
        JPanel modelPanel = new JBPanel<>(new GridBagLayout());
        modelPanel.setBorder(JBUI.Borders.empty(5));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = JBUI.insets(2);

        modelPanel.add(new JBLabel("Modèle (recommandé: gpt-oss):"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        modelPanel.add(agentModelNameField, gbc);

        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        modelPanel.add(checkModelButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        modelPanel.add(modelStatusLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        modelPanel.add(new JBLabel("URL Ollama (optionnel):"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        modelPanel.add(agentOllamaUrlField, gbc);

        // Main form
        JPanel formPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Mode agent:", agentModeEnabledCheckBox)
                .addSeparator()
                .addComponent(modelPanel)
                .addSeparator()
                .addLabeledComponent("Niveau de sécurité:", securityLevelComboBox)
                .addLabeledComponent("", autoTaskApprovalCheckBox)
                .addSeparator()
                .addLabeledComponent("Nombre maximum de tâches par session:", maxTasksField)
                .addLabeledComponent("", snapshotEnabledCheckBox)
                .addLabeledComponent("", mcpIntegrationEnabledCheckBox)
                .addLabeledComponent("", taskProgressUIEnabledCheckBox)
                .addSeparator()
                .getPanel();

        // Description panel
        JPanel descriptionPanel = new JBPanel<>(new BorderLayout());
        descriptionPanel.setBorder(JBUI.Borders.empty(10));

        JBLabel titleLabel = new JBLabel("Configuration actuelle:");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));

        descriptionPanel.add(titleLabel, BorderLayout.NORTH);
        descriptionPanel.add(configSummaryLabel, BorderLayout.CENTER);

        // Security level descriptions
        JPanel securityDescPanel = createSecurityDescriptionPanel();

        // Main layout
        add(formPanel, BorderLayout.NORTH);
        add(descriptionPanel, BorderLayout.CENTER);
        add(securityDescPanel, BorderLayout.SOUTH);
    }

    private JPanel createSecurityDescriptionPanel() {
        JPanel panel = new JBPanel<>(new GridLayout(0, 1, 5, 5));
        panel.setBorder(JBUI.Borders.compound(
                JBUI.Borders.empty(10),
                JBUI.Borders.customLine(JBColor.GRAY, 1, 0, 0, 0)
        ));

        JBLabel titleLabel = new JBLabel("Niveaux de sécurité:");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        panel.add(titleLabel);

        for (AgentModeSettings.AgentSecurityLevel level : AgentModeSettings.AgentSecurityLevel.values()) {
            JBLabel levelLabel = new JBLabel("• " + level.name() + ": " + level.getDescription());
            levelLabel.setFont(levelLabel.getFont().deriveFont(Font.PLAIN, 12f));
            panel.add(levelLabel);
        }

        return panel;
    }

    private void setupListeners() {
        // Update config summary when any setting changes
        agentModeEnabledCheckBox.addActionListener(e -> {
            updateDependentComponents();
            updateConfigSummary();
        });

        autoTaskApprovalCheckBox.addActionListener(e -> updateConfigSummary());
        securityLevelComboBox.addActionListener(e -> updateConfigSummary());
        taskProgressUIEnabledCheckBox.addActionListener(e -> updateConfigSummary());
        snapshotEnabledCheckBox.addActionListener(e -> updateConfigSummary());
        mcpIntegrationEnabledCheckBox.addActionListener(e -> updateConfigSummary());

        maxTasksField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateConfigSummary();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateConfigSummary();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateConfigSummary();
            }
        });

        // ✨ Check model availability button
        checkModelButton.addActionListener(e -> checkModelAvailability());

        // Initial update
        updateDependentComponents();
    }

    /**
     * Checks if the configured agent model is available
     */
    private void checkModelAvailability() {
        String modelName = agentModelNameField.getText().trim();
        String ollamaUrl = agentOllamaUrlField.getText().trim();

        if (modelName.isEmpty()) {
            modelStatusLabel.setText("⚠️ Veuillez entrer un nom de modèle");
            modelStatusLabel.setForeground(JBColor.ORANGE);
            return;
        }

        modelStatusLabel.setText("🔍 Vérification en cours...");
        modelStatusLabel.setForeground(JBColor.GRAY);
        checkModelButton.setEnabled(false);

        // Check in background to avoid freezing UI
        new Thread(() -> {
            fr.baretto.ollamassist.core.agent.ModelAvailabilityChecker checker;
            if (ollamaUrl != null && !ollamaUrl.isEmpty()) {
                checker = new fr.baretto.ollamassist.core.agent.ModelAvailabilityChecker(
                        ollamaUrl,
                        java.time.Duration.ofSeconds(10)
                );
            } else {
                checker = new fr.baretto.ollamassist.core.agent.ModelAvailabilityChecker();
            }

            fr.baretto.ollamassist.core.agent.ModelAvailabilityChecker.ModelAvailabilityResult result =
                    checker.checkModelAvailability(modelName);

            // Update UI on EDT
            SwingUtilities.invokeLater(() -> {
                checkModelButton.setEnabled(true);

                if (result.isAvailable()) {
                    modelStatusLabel.setText("✅ Modèle disponible");
                    modelStatusLabel.setForeground(new JBColor(new Color(0, 128, 0), new Color(50, 200, 50)));
                } else if (result.isNotAvailable()) {
                    String message = String.format(
                            "<html>❌ Modèle non disponible<br>" +
                            "<small>Exécutez: <b>ollama pull %s</b></small></html>",
                            modelName
                    );
                    modelStatusLabel.setText(message);
                    modelStatusLabel.setForeground(JBColor.RED);
                } else if (result.isError()) {
                    modelStatusLabel.setText("❌ Erreur: " + result.getErrorMessage());
                    modelStatusLabel.setForeground(JBColor.RED);
                }
            });
        }).start();
    }

    private void updateDependentComponents() {
        boolean agentModeEnabled = agentModeEnabledCheckBox.isSelected();

        autoTaskApprovalCheckBox.setEnabled(agentModeEnabled);
        maxTasksField.setEnabled(agentModeEnabled);
        snapshotEnabledCheckBox.setEnabled(agentModeEnabled);
        mcpIntegrationEnabledCheckBox.setEnabled(agentModeEnabled);
        securityLevelComboBox.setEnabled(agentModeEnabled);
        taskProgressUIEnabledCheckBox.setEnabled(agentModeEnabled);
    }

    private void updateConfigSummary() {
        if (!agentModeEnabledCheckBox.isSelected()) {
            configSummaryLabel.setText("Mode Agent désactivé");
            return;
        }

        try {
            AgentModeSettings.AgentSecurityLevel selectedLevel =
                    (AgentModeSettings.AgentSecurityLevel) securityLevelComboBox.getSelectedItem();
            int maxTasks = Integer.parseInt(maxTasksField.getText().trim());

            String summary = String.format(
                    "Mode Agent: Activé | Sécurité: %s | Max Tâches: %d | Auto-approbation: %s",
                    selectedLevel != null ? selectedLevel.getDescription() : "Non défini",
                    maxTasks,
                    autoTaskApprovalCheckBox.isSelected() ? "Oui" : "Non"
            );

            configSummaryLabel.setText(summary);
        } catch (NumberFormatException e) {
            configSummaryLabel.setText("Configuration invalide - Vérifiez le nombre de tâches");
        }
    }

    public void loadSettings() {
        agentModeEnabledCheckBox.setSelected(settings.isAgentModeEnabled());
        autoTaskApprovalCheckBox.setSelected(settings.isAutoTaskApprovalEnabled());
        maxTasksField.setText(String.valueOf(settings.getMaxTasksPerSession()));
        snapshotEnabledCheckBox.setSelected(settings.isSnapshotEnabled());
        mcpIntegrationEnabledCheckBox.setSelected(settings.isMcpIntegrationEnabled());
        securityLevelComboBox.setSelectedItem(settings.getSecurityLevel());
        taskProgressUIEnabledCheckBox.setSelected(settings.isTaskProgressUIEnabled());

        // ✨ Load agent model settings
        agentModelNameField.setText(settings.getAgentModelName() != null ? settings.getAgentModelName() : "gpt-oss");
        agentOllamaUrlField.setText(settings.getAgentOllamaUrl() != null ? settings.getAgentOllamaUrl() : "");

        updateDependentComponents();
        updateConfigSummary();
    }

    public void saveSettings() {
        settings.setAgentModeEnabled(agentModeEnabledCheckBox.isSelected());
        settings.setAutoTaskApprovalEnabled(autoTaskApprovalCheckBox.isSelected());

        try {
            int maxTasks = Integer.parseInt(maxTasksField.getText().trim());
            settings.setMaxTasksPerSession(maxTasks);
        } catch (NumberFormatException e) {
            // Keep current value if invalid
        }

        settings.setSnapshotEnabled(snapshotEnabledCheckBox.isSelected());
        settings.setMcpIntegrationEnabled(mcpIntegrationEnabledCheckBox.isSelected());

        // ✨ Save agent model settings
        String modelName = agentModelNameField.getText().trim();
        if (!modelName.isEmpty()) {
            settings.setAgentModelName(modelName);
        }

        String ollamaUrl = agentOllamaUrlField.getText().trim();
        settings.setAgentOllamaUrl(ollamaUrl.isEmpty() ? null : ollamaUrl);

        AgentModeSettings.AgentSecurityLevel selectedLevel =
                (AgentModeSettings.AgentSecurityLevel) securityLevelComboBox.getSelectedItem();
        if (selectedLevel != null) {
            settings.setSecurityLevel(selectedLevel);
        }

        settings.setTaskProgressUIEnabled(taskProgressUIEnabledCheckBox.isSelected());
    }

    public boolean isModified() {
        if (agentModeEnabledCheckBox.isSelected() != settings.isAgentModeEnabled()) return true;
        if (autoTaskApprovalCheckBox.isSelected() != settings.isAutoTaskApprovalEnabled()) return true;
        if (snapshotEnabledCheckBox.isSelected() != settings.isSnapshotEnabled()) return true;
        if (mcpIntegrationEnabledCheckBox.isSelected() != settings.isMcpIntegrationEnabled()) return true;
        if (taskProgressUIEnabledCheckBox.isSelected() != settings.isTaskProgressUIEnabled()) return true;

        AgentModeSettings.AgentSecurityLevel selectedLevel =
                (AgentModeSettings.AgentSecurityLevel) securityLevelComboBox.getSelectedItem();
        if (selectedLevel != settings.getSecurityLevel()) return true;

        try {
            int maxTasks = Integer.parseInt(maxTasksField.getText().trim());
            if (maxTasks != settings.getMaxTasksPerSession()) return true;
        } catch (NumberFormatException e) {
            return true; // Invalid value counts as modified
        }

        return false;
    }

    public void resetToDefaults() {
        settings.resetToDefaults();
        loadSettings();
    }

    public boolean validateSettings() {
        try {
            int maxTasks = Integer.parseInt(maxTasksField.getText().trim());
            if (maxTasks < 1 || maxTasks > 100) {
                showValidationError("Le nombre de tâches doit être entre 1 et 100");
                return false;
            }
        } catch (NumberFormatException e) {
            showValidationError("Le nombre de tâches doit être un nombre entier valide");
            return false;
        }

        return true;
    }

    private void showValidationError(String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                "Erreur de validation",
                JOptionPane.ERROR_MESSAGE
        );
    }
}