package fr.baretto.ollamassist.setting.dialogs;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import fr.baretto.ollamassist.setting.McpServerConfig;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

/**
 * Dialog for adding or editing an MCP server configuration.
 */
public class AddEditMcpServerDialog extends DialogWrapper {

    private final JBTextField nameField = new JBTextField(20);
    private final ComboBox<McpServerConfig.TransportType> typeComboBox = new ComboBox<>(McpServerConfig.TransportType.values());
    private final JBCheckBox enabledCheckbox = new JBCheckBox("Enabled", true);

    // Stdio fields
    private final JBTextField commandField = new JBTextField(30);
    private final JBPanel<?> stdioPanel = new JBPanel<>();

    // HTTP/SSE fields
    private final JBTextField urlField = new JBTextField(30);
    private final JBTextField authTokenField = new JBTextField(30);
    private final JBPanel<?> httpPanel = new JBPanel<>();

    // Docker fields
    private final JBTextField dockerImageField = new JBTextField(30);
    private final JBTextField dockerHostField = new JBTextField(30);
    private final JBPanel<?> dockerPanel = new JBPanel<>();

    // Advanced options
    private final JBCheckBox logEventsCheckbox = new JBCheckBox("Log events");
    private final JBCheckBox logRequestsCheckbox = new JBCheckBox("Log HTTP requests");
    private final JBCheckBox logResponsesCheckbox = new JBCheckBox("Log HTTP responses");
    private final JBTextField filterToolNamesField = new JBTextField(30);
    private final JBTextField toolNamePrefixField = new JBTextField(20);
    private final JBCheckBox failOnErrorCheckbox = new JBCheckBox("Fail assistant if server fails");
    private final JTextArea environmentVariablesArea = new JTextArea(4, 30);

    private final JPanel dynamicConfigPanel = new JPanel(new CardLayout());
    private final McpServerConfig existingConfig;

    public AddEditMcpServerDialog(@Nullable McpServerConfig config) {
        super(true);
        this.existingConfig = config;
        setTitle(config == null ? "Add MCP Server" : "Edit MCP Server");
        init();

        if (config != null) {
            loadConfig(config);
        }
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JBPanel<?> mainPanel = new JBPanel<>();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(JBUI.Borders.empty(10));

        // Basic configuration
        mainPanel.add(createBasicConfigPanel());
        mainPanel.add(Box.createVerticalStrut(10));

        // Dynamic transport configuration
        mainPanel.add(createDynamicConfigPanel());
        mainPanel.add(Box.createVerticalStrut(10));

        // Advanced configuration
        mainPanel.add(createAdvancedConfigPanel());

        return mainPanel;
    }

    private JPanel createBasicConfigPanel() {
        JBPanel<?> panel = new JBPanel<>();
        panel.setLayout(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Basic Configuration"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Name field
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JBLabel("Name:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(nameField, gbc);

        // Transport type
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JBLabel("Transport Type:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(typeComboBox, gbc);

        // Enabled checkbox
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        panel.add(enabledCheckbox, gbc);

        // Add listener to switch panels based on transport type
        typeComboBox.addActionListener(e -> updateDynamicPanel());

        return panel;
    }

    private JPanel createDynamicConfigPanel() {
        dynamicConfigPanel.setBorder(BorderFactory.createTitledBorder("Transport Configuration"));

        // Stdio panel
        stdioPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        stdioPanel.add(new JBLabel("Command:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        stdioPanel.add(commandField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        stdioPanel.add(new JBLabel("<html><i>Space-separated command (e.g., \"npm exec @modelcontextprotocol/server-everything\")</i></html>"), gbc);

        // HTTP panel
        httpPanel.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        httpPanel.add(new JBLabel("URL:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        httpPanel.add(urlField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        httpPanel.add(new JBLabel("<html><i>HTTP/SSE endpoint (e.g., \"http://localhost:3001/mcp\")</i></html>"), gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        httpPanel.add(new JBLabel("API Key / Auth Token (optional):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        httpPanel.add(authTokenField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        httpPanel.add(new JBLabel("<html><i>Will be sent as \"Authorization: Bearer &lt;token&gt;\" header</i></html>"), gbc);

        // Docker panel
        dockerPanel.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        dockerPanel.add(new JBLabel("Docker Image:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        dockerPanel.add(dockerImageField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        dockerPanel.add(new JBLabel("Docker Host:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        dockerPanel.add(dockerHostField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        dockerPanel.add(new JBLabel("<html><i>Docker socket (e.g., \"unix:///var/run/docker.sock\")</i></html>"), gbc);

        dockerHostField.setText("unix:///var/run/docker.sock");

        // Add panels to card layout
        dynamicConfigPanel.add(stdioPanel, McpServerConfig.TransportType.STDIO.name());
        dynamicConfigPanel.add(httpPanel, McpServerConfig.TransportType.HTTP_SSE.name());
        dynamicConfigPanel.add(dockerPanel, McpServerConfig.TransportType.DOCKER.name());

        updateDynamicPanel();

        return dynamicConfigPanel;
    }

    private JPanel createAdvancedConfigPanel() {
        JBPanel<?> panel = new JBPanel<>();
        panel.setLayout(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Advanced Options"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Logging options
        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 2;
        panel.add(logEventsCheckbox, gbc);

        gbc.gridy = row++;
        panel.add(logRequestsCheckbox, gbc);

        gbc.gridy = row++;
        panel.add(logResponsesCheckbox, gbc);

        // Filter tool names
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JBLabel("Filter Tool Names:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(filterToolNamesField, gbc);

        gbc.gridx = 0;
        gbc.gridy = ++row;
        gbc.gridwidth = 2;
        panel.add(new JBLabel("<html><i>Comma-separated tool names (empty = all tools)</i></html>"), gbc);

        // Tool name prefix
        gbc.gridy = ++row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JBLabel("Tool Name Prefix:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(toolNamePrefixField, gbc);

        gbc.gridx = 0;
        gbc.gridy = ++row;
        gbc.gridwidth = 2;
        panel.add(new JBLabel("<html><i>Prefix to avoid tool name conflicts</i></html>"), gbc);

        // Fail on error
        gbc.gridy = ++row;
        panel.add(failOnErrorCheckbox, gbc);

        // Environment variables
        gbc.gridy = ++row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JBLabel("Environment Variables:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        environmentVariablesArea.setLineWrap(true);
        environmentVariablesArea.setWrapStyleWord(false);
        environmentVariablesArea.setBorder(BorderFactory.createLineBorder(java.awt.Color.GRAY));
        JScrollPane scrollPane = new JScrollPane(environmentVariablesArea);
        scrollPane.setPreferredSize(new Dimension(300, 80));
        panel.add(scrollPane, gbc);

        gbc.gridx = 0;
        gbc.gridy = ++row;
        gbc.gridwidth = 2;
        panel.add(new JBLabel("<html><i>Format: KEY=value (one per line)<br/>Example:<br/>GITHUB_TOKEN=ghp_xxxxx<br/>SLACK_TOKEN=xoxb-xxxxx</i></html>"), gbc);

        return panel;
    }

    private void updateDynamicPanel() {
        McpServerConfig.TransportType selected = (McpServerConfig.TransportType) typeComboBox.getSelectedItem();
        if (selected != null) {
            CardLayout layout = (CardLayout) dynamicConfigPanel.getLayout();
            layout.show(dynamicConfigPanel, selected.name());

            // Enable/disable HTTP-specific logging options
            logRequestsCheckbox.setEnabled(selected == McpServerConfig.TransportType.HTTP_SSE);
            logResponsesCheckbox.setEnabled(selected == McpServerConfig.TransportType.HTTP_SSE);
        }
    }

    private void loadConfig(McpServerConfig config) {
        nameField.setText(config.getName());
        typeComboBox.setSelectedItem(config.getType());
        enabledCheckbox.setSelected(config.isEnabled());

        // Transport-specific
        commandField.setText(String.join(" ", config.getCommand()));
        urlField.setText(config.getUrl());
        authTokenField.setText(config.getAuthToken());
        dockerImageField.setText(config.getDockerImage());
        dockerHostField.setText(config.getDockerHost());

        // Advanced
        logEventsCheckbox.setSelected(config.isLogEvents());
        logRequestsCheckbox.setSelected(config.isLogRequests());
        logResponsesCheckbox.setSelected(config.isLogResponses());
        filterToolNamesField.setText(config.getFilterToolNames());
        toolNamePrefixField.setText(config.getToolNamePrefix());
        failOnErrorCheckbox.setSelected(config.isFailOnError());
        environmentVariablesArea.setText(config.getEnvironmentVariables());

        updateDynamicPanel();
    }

    public McpServerConfig getConfig() {
        McpServerConfig config = existingConfig != null ? existingConfig : new McpServerConfig();

        config.setName(nameField.getText().trim());
        config.setType((McpServerConfig.TransportType) typeComboBox.getSelectedItem());
        config.setEnabled(enabledCheckbox.isSelected());

        // Transport-specific
        String commandText = commandField.getText().trim();
        config.setCommand(commandText.isEmpty() ?
                List.of() :
            Arrays.stream(commandText.split("\\s+"))
                .filter(s -> !s.isEmpty())
                .toList()
        );
        config.setUrl(urlField.getText().trim());
        config.setAuthToken(authTokenField.getText().trim());
        config.setDockerImage(dockerImageField.getText().trim());
        config.setDockerHost(dockerHostField.getText().trim());

        // Advanced
        config.setLogEvents(logEventsCheckbox.isSelected());
        config.setLogRequests(logRequestsCheckbox.isSelected());
        config.setLogResponses(logResponsesCheckbox.isSelected());
        config.setFilterToolNames(filterToolNamesField.getText().trim());
        config.setToolNamePrefix(toolNamePrefixField.getText().trim());
        config.setFailOnError(failOnErrorCheckbox.isSelected());
        config.setEnvironmentVariables(environmentVariablesArea.getText().trim());

        return config;
    }

    @Override
    protected ValidationInfo doValidate() {
        if (nameField.getText().trim().isEmpty()) {
            return new ValidationInfo("Server name is required", nameField);
        }

        McpServerConfig.TransportType type = (McpServerConfig.TransportType) typeComboBox.getSelectedItem();
        if (type == McpServerConfig.TransportType.STDIO && commandField.getText().trim().isEmpty()) {
            return new ValidationInfo("Command is required for Stdio transport", commandField);
        }

        if (type == McpServerConfig.TransportType.HTTP_SSE && urlField.getText().trim().isEmpty()) {
            return new ValidationInfo("URL is required for HTTP/SSE transport", urlField);
        }

        if (type == McpServerConfig.TransportType.DOCKER && dockerImageField.getText().trim().isEmpty()) {
            return new ValidationInfo("Docker image is required for Docker transport", dockerImageField);
        }

        return null;
    }
}
