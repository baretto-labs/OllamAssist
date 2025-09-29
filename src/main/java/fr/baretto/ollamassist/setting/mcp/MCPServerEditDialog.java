package fr.baretto.ollamassist.setting.mcp;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import fr.baretto.ollamassist.core.mcp.server.MCPServerConfig;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dialog pour éditer/créer un serveur MCP
 */
public class MCPServerEditDialog extends DialogWrapper {

    private final MCPServerConfig existingServer;

    // Composants UI
    private JBTextField nameField;
    private JComboBox<MCPServerConfig.MCPServerType> typeComboBox;
    private JBTextField endpointField;
    private JBCheckBox enabledCheckBox;
    private JBTextField capabilitiesField;
    private JComboBox<MCPServerConfig.MCPAuth.MCPAuthType> authTypeComboBox;
    private JBTextField authKeyField;
    private JBTextField authKeyEnvField;
    private JBTextField timeoutField;

    private JPanel authPanel;
    private CardLayout authCardLayout;

    public MCPServerEditDialog(@Nullable Component parent, @Nullable MCPServerConfig server) {
        super(parent, true);
        this.existingServer = server;

        setTitle(server == null ? "Ajouter un Serveur MCP" : "Modifier le Serveur MCP");
        init();

        if (server != null) {
            populateFields(server);
        }
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setPreferredSize(new Dimension(500, 400));

        // Panel principal avec les champs
        JPanel fieldsPanel = createFieldsPanel();
        mainPanel.add(fieldsPanel, BorderLayout.CENTER);

        return mainPanel;
    }

    private JPanel createFieldsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        // Nom
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JBLabel("Nom:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        nameField = new JBTextField();
        panel.add(nameField, gbc);

        // Type
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JBLabel("Type:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        typeComboBox = new JComboBox<>(new MCPServerConfig.MCPServerType[]{
                MCPServerConfig.MCPServerType.HTTP,
                MCPServerConfig.MCPServerType.WEBSOCKET
        });
        typeComboBox.addActionListener(e -> updateEndpointVisibility());
        panel.add(typeComboBox, gbc);

        // Endpoint
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JBLabel("Endpoint:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        endpointField = new JBTextField();
        endpointField.setToolTipText("URL du serveur MCP (ex: http://localhost:8080/mcp ou ws://localhost:8080/mcp)");
        panel.add(endpointField, gbc);

        // Activé
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JBLabel("Activé:"), gbc);
        gbc.gridx = 1;
        enabledCheckBox = new JBCheckBox("Serveur activé", true);
        panel.add(enabledCheckBox, gbc);

        // Capacités
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JBLabel("Capacités:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        capabilitiesField = new JBTextField();
        capabilitiesField.setToolTipText("Capacités séparées par des virgules (ex: web_search,url_fetch)");
        panel.add(capabilitiesField, gbc);

        // Timeout
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JBLabel("Timeout (ms):"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        timeoutField = new JBTextField("30000");
        panel.add(timeoutField, gbc);

        // Section Authentification
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JSeparator separator = new JSeparator();
        panel.add(separator, gbc);

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JBLabel("Authentification:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        authTypeComboBox = new JComboBox<>(MCPServerConfig.MCPAuth.MCPAuthType.values());
        authTypeComboBox.addActionListener(e -> updateAuthPanel());
        panel.add(authTypeComboBox, gbc);

        // Panel d'authentification avec CardLayout
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        authPanel = createAuthPanel();
        panel.add(authPanel, gbc);

        return panel;
    }

    private JPanel createAuthPanel() {
        authCardLayout = new CardLayout();
        JPanel panel = new JPanel(authCardLayout);
        panel.setBorder(JBUI.Borders.empty(5));

        // Panel pour NONE
        panel.add(new JPanel(), "NONE");

        // Panel pour API_KEY
        JPanel apiKeyPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(2);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        apiKeyPanel.add(new JBLabel("Clé API:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        authKeyField = new JBTextField();
        apiKeyPanel.add(authKeyField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        apiKeyPanel.add(new JBLabel("Variable d'env:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        authKeyEnvField = new JBTextField();
        authKeyEnvField.setToolTipText("Nom de la variable d'environnement contenant la clé (optionnel)");
        apiKeyPanel.add(authKeyEnvField, gbc);

        panel.add(apiKeyPanel, "API_KEY");

        // Panel pour les autres types (placeholder)
        panel.add(new JBLabel("Authentification non implémentée"), "BASIC_AUTH");
        panel.add(new JBLabel("OAuth2 non implémenté"), "OAUTH2");
        panel.add(new JBLabel("Authentification personnalisée"), "CUSTOM");

        return panel;
    }

    private void updateEndpointVisibility() {
        MCPServerConfig.MCPServerType selectedType = (MCPServerConfig.MCPServerType) typeComboBox.getSelectedItem();
        boolean needsEndpoint = selectedType != MCPServerConfig.MCPServerType.BUILTIN;
        endpointField.setEnabled(needsEndpoint);

        if (needsEndpoint && endpointField.getText().isEmpty()) {
            if (selectedType == MCPServerConfig.MCPServerType.HTTP) {
                endpointField.setText("http://localhost:8080/mcp");
            } else if (selectedType == MCPServerConfig.MCPServerType.WEBSOCKET) {
                endpointField.setText("ws://localhost:8080/mcp");
            }
        }
    }

    private void updateAuthPanel() {
        MCPServerConfig.MCPAuth.MCPAuthType selectedType =
                (MCPServerConfig.MCPAuth.MCPAuthType) authTypeComboBox.getSelectedItem();
        authCardLayout.show(authPanel, selectedType.name());
    }

    private void populateFields(MCPServerConfig server) {
        nameField.setText(server.getName());
        typeComboBox.setSelectedItem(server.getType());
        endpointField.setText(server.getEndpoint() != null ? server.getEndpoint() : "");
        enabledCheckBox.setSelected(server.isEnabled());

        if (server.getCapabilities() != null) {
            capabilitiesField.setText(String.join(", ", server.getCapabilities()));
        }

        timeoutField.setText(String.valueOf(server.getTimeout()));

        if (server.getAuth() != null) {
            authTypeComboBox.setSelectedItem(server.getAuth().getType());
            authKeyField.setText(server.getAuth().getKey() != null ? server.getAuth().getKey() : "");
            authKeyEnvField.setText(server.getAuth().getKeyEnv() != null ? server.getAuth().getKeyEnv() : "");
        }

        updateEndpointVisibility();
        updateAuthPanel();
    }

    @Override
    protected ValidationInfo doValidate() {
        if (nameField.getText().trim().isEmpty()) {
            return new ValidationInfo("Le nom du serveur est requis", nameField);
        }

        MCPServerConfig.MCPServerType selectedType = (MCPServerConfig.MCPServerType) typeComboBox.getSelectedItem();
        if (selectedType != MCPServerConfig.MCPServerType.BUILTIN && endpointField.getText().trim().isEmpty()) {
            return new ValidationInfo("L'endpoint est requis pour ce type de serveur", endpointField);
        }

        try {
            long timeout = Long.parseLong(timeoutField.getText().trim());
            if (timeout <= 0) {
                return new ValidationInfo("Le timeout doit être positif", timeoutField);
            }
        } catch (NumberFormatException e) {
            return new ValidationInfo("Le timeout doit être un nombre valide", timeoutField);
        }

        return null;
    }

    public MCPServerConfig getServerConfig() {
        MCPServerConfig.MCPServerType type = (MCPServerConfig.MCPServerType) typeComboBox.getSelectedItem();

        // Créer l'authentification
        MCPServerConfig.MCPAuth.MCPAuthType authType =
                (MCPServerConfig.MCPAuth.MCPAuthType) authTypeComboBox.getSelectedItem();

        MCPServerConfig.MCPAuth auth = MCPServerConfig.MCPAuth.builder()
                .type(authType)
                .key(authKeyField.getText().trim().isEmpty() ? null : authKeyField.getText().trim())
                .keyEnv(authKeyEnvField.getText().trim().isEmpty() ? null : authKeyEnvField.getText().trim())
                .build();

        // Parser les capacités
        List<String> capabilities = Arrays.stream(capabilitiesField.getText().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        return MCPServerConfig.builder()
                .id(existingServer != null ? existingServer.getId() : UUID.randomUUID().toString())
                .name(nameField.getText().trim())
                .type(type)
                .enabled(enabledCheckBox.isSelected())
                .endpoint(endpointField.getText().trim().isEmpty() ? null : endpointField.getText().trim())
                .capabilities(capabilities)
                .timeout(Long.parseLong(timeoutField.getText().trim()))
                .auth(auth)
                .config(Map.of()) // Configuration vide pour l'instant
                .build();
    }
}