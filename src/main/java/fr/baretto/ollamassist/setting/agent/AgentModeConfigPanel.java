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

        configSummaryLabel = new JBLabel();
        configSummaryLabel.setFont(configSummaryLabel.getFont().deriveFont(Font.ITALIC));
        configSummaryLabel.setForeground(JBColor.GRAY);

        setupUI();
        setupListeners();
        loadSettings();
        updateConfigSummary();
    }

    private void setupUI() {
        // Main form
        JPanel formPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Mode agent:", agentModeEnabledCheckBox)
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

        // Initial update
        updateDependentComponents();
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