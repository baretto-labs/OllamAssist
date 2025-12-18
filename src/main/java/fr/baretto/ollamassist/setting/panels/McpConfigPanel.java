package fr.baretto.ollamassist.setting.panels;

import com.intellij.openapi.project.Project;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import fr.baretto.ollamassist.setting.McpServerConfig;
import fr.baretto.ollamassist.setting.McpSettings;
import fr.baretto.ollamassist.setting.dialogs.AddEditMcpServerDialog;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration panel for managing MCP (Model Context Protocol) servers.
 */
public class McpConfigPanel extends JBPanel<McpConfigPanel> {

    private final Project project;
    private final JBCheckBox mcpEnabledCheckbox = new JBCheckBox("Enable MCP Integration", false);
    private final JBCheckBox mcpApprovalRequiredCheckbox = new JBCheckBox("Require approval for MCP tool execution", true);
    private final JSpinner mcpApprovalTimeoutSpinner;
    private final JBTable serversTable;
    private final McpTableModel tableModel;
    private final JPanel tablePanel;

    public McpConfigPanel(Project project) {
        this.project = project;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(JBUI.Borders.empty(10));

        McpSettings settings = McpSettings.getInstance(project);

        // MCP enabled checkbox
        mcpEnabledCheckbox.setSelected(settings.isMcpEnabled());
        mcpEnabledCheckbox.setToolTipText("Enable Model Context Protocol integration to connect AI to external tools and services");
        add(createCheckboxPanel(mcpEnabledCheckbox));

        add(Box.createVerticalStrut(10));

        // MCP approval required checkbox
        mcpApprovalRequiredCheckbox.setSelected(settings.isMcpApprovalRequired());
        mcpApprovalRequiredCheckbox.setToolTipText("Require user approval before executing MCP tools for security");
        add(createCheckboxPanel(mcpApprovalRequiredCheckbox));

        add(Box.createVerticalStrut(5));

        // MCP approval timeout spinner
        SpinnerNumberModel timeoutModel = new SpinnerNumberModel(
                settings.getMcpApprovalTimeoutSeconds(), // current value
                10,   // minimum (10 seconds)
                3600, // maximum (1 hour)
                30    // step
        );
        mcpApprovalTimeoutSpinner = new JSpinner(timeoutModel);
        JPanel timeoutPanel = createLabeledSpinnerPanel("Approval timeout (seconds):", mcpApprovalTimeoutSpinner);
        timeoutPanel.setToolTipText("Maximum time to wait for user approval before timing out");
        add(timeoutPanel);

        add(Box.createVerticalStrut(10));

        // Information panel
        add(createInfoPanel());

        add(Box.createVerticalStrut(10));

        // Table with MCP servers
        tableModel = new McpTableModel(new ArrayList<>(McpSettings.getInstance(project).getMcpServers()));
        serversTable = new JBTable(tableModel);
        serversTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        serversTable.getColumnModel().getColumn(0).setPreferredWidth(150); // Name
        serversTable.getColumnModel().getColumn(1).setPreferredWidth(80);  // Type
        serversTable.getColumnModel().getColumn(2).setPreferredWidth(50);  // Enabled
        serversTable.getColumnModel().getColumn(3).setPreferredWidth(300); // Configuration

        tablePanel = ToolbarDecorator.createDecorator(serversTable)
                .setAddAction(button -> addServer())
                .setEditAction(button -> editServer())
                .setRemoveAction(button -> removeServer())
                .disableUpDownActions()
                .createPanel();

        tablePanel.setBorder(BorderFactory.createTitledBorder("MCP Servers"));
        tablePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(tablePanel);

        // Enable/disable table based on checkbox
        mcpEnabledCheckbox.addItemListener(e -> updateTableState());
        updateTableState();
    }

    private JPanel createCheckboxPanel(JCheckBox checkbox) {
        JBPanel<?> panel = new JBPanel<>();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.empty(10, 0));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        checkbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(checkbox);

        return panel;
    }

    private JPanel createLabeledSpinnerPanel(String labelText, JSpinner spinner) {
        JBPanel<?> panel = new JBPanel<>();
        panel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 0));
        panel.setBorder(JBUI.Borders.empty(5, 20, 5, 10));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JBLabel label = new JBLabel(labelText);
        panel.add(label);

        spinner.setPreferredSize(new Dimension(100, spinner.getPreferredSize().height));
        panel.add(spinner);

        return panel;
    }

    private JPanel createInfoPanel() {
        JBPanel<?> panel = new JBPanel<>();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.empty(5, 20, 15, 10));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel infoLabel = new JLabel("<html>" +
                "<b>ℹ️ About MCP (Model Context Protocol):</b><br/>" +
                "MCP enables the AI assistant to use external tools and access external data sources.<br/>" +
                "Configure servers below using one of three transport types:<br/>" +
                "• <b>Stdio:</b> Launch local processes (e.g., npm-based servers)<br/>" +
                "• <b>HTTP/SSE:</b> Connect to remote HTTP servers with Server-Sent Events<br/>" +
                "• <b>Docker:</b> Run MCP servers in Docker containers<br/>" +
                "Each server can be individually enabled or disabled." +
                "</html>");
        infoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(infoLabel);

        return panel;
    }

    private void updateTableState() {
        boolean enabled = mcpEnabledCheckbox.isSelected();
        serversTable.setEnabled(enabled);
        tablePanel.setEnabled(enabled);
    }

    private void addServer() {
        AddEditMcpServerDialog dialog = new AddEditMcpServerDialog(null);
        if (dialog.showAndGet()) {
            McpServerConfig config = dialog.getConfig();
            tableModel.addServer(config);
        }
    }

    private void editServer() {
        int selectedRow = serversTable.getSelectedRow();
        if (selectedRow >= 0) {
            McpServerConfig config = tableModel.getServer(selectedRow);
            AddEditMcpServerDialog dialog = new AddEditMcpServerDialog(config);
            if (dialog.showAndGet()) {
                McpServerConfig updatedConfig = dialog.getConfig();
                tableModel.updateServer(selectedRow, updatedConfig);
            }
        }
    }

    private void removeServer() {
        int selectedRow = serversTable.getSelectedRow();
        if (selectedRow >= 0) {
            tableModel.removeServer(selectedRow);
        }
    }

    // Getters and setters for SettingsBindingHelper

    public boolean isMcpEnabled() {
        return mcpEnabledCheckbox.isSelected();
    }

    public void setMcpEnabled(boolean enabled) {
        mcpEnabledCheckbox.setSelected(enabled);
        updateTableState();
    }

    public List<McpServerConfig> getMcpServers() {
        return tableModel.getServers();
    }

    public void setMcpServers(List<McpServerConfig> servers) {
        tableModel.setServers(servers);
    }

    public JBCheckBox getMcpEnabledCheckbox() {
        return mcpEnabledCheckbox;
    }

    public boolean isMcpApprovalRequired() {
        return mcpApprovalRequiredCheckbox.isSelected();
    }

    public void setMcpApprovalRequired(boolean required) {
        mcpApprovalRequiredCheckbox.setSelected(required);
    }

    public int getMcpApprovalTimeoutSeconds() {
        return (Integer) mcpApprovalTimeoutSpinner.getValue();
    }

    public void setMcpApprovalTimeoutSeconds(int timeout) {
        mcpApprovalTimeoutSpinner.setValue(timeout);
    }

    /**
     * Table model for MCP servers.
     */
    private static class McpTableModel extends AbstractTableModel {
        private static final String[] COLUMN_NAMES = {"Name", "Type", "Enabled", "Configuration"};
        private List<McpServerConfig> servers;

        public McpTableModel(List<McpServerConfig> servers) {
            this.servers = new ArrayList<>(servers);
        }

        @Override
        public int getRowCount() {
            return servers.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMN_NAMES[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 2 -> Boolean.class;
                default -> String.class;
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            McpServerConfig server = servers.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> server.getName();
                case 1 -> server.getType().toString();
                case 2 -> server.isEnabled();
                case 3 -> server.getConfigurationSummary();
                default -> null;
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 2; // Only "Enabled" column is directly editable
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            if (columnIndex == 2) {
                servers.get(rowIndex).setEnabled((Boolean) value);
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }

        public McpServerConfig getServer(int index) {
            return servers.get(index);
        }

        public void addServer(McpServerConfig server) {
            servers.add(server);
            fireTableRowsInserted(servers.size() - 1, servers.size() - 1);
        }

        public void updateServer(int index, McpServerConfig server) {
            servers.set(index, server);
            fireTableRowsUpdated(index, index);
        }

        public void removeServer(int index) {
            servers.remove(index);
            fireTableRowsDeleted(index, index);
        }

        public List<McpServerConfig> getServers() {
            return new ArrayList<>(servers);
        }

        public void setServers(List<McpServerConfig> servers) {
            this.servers = new ArrayList<>(servers != null ? servers : new ArrayList<>());
            fireTableDataChanged();
        }
    }
}
