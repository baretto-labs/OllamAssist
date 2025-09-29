package fr.baretto.ollamassist.setting.mcp;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import fr.baretto.ollamassist.core.mcp.server.MCPServerConfig;
import fr.baretto.ollamassist.core.mcp.server.MCPServerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel de configuration pour les serveurs MCP
 */
@Slf4j
public class MCPConfigurationPanel implements Configurable {

    private JPanel mainPanel;
    private JBTable serversTable;
    private MCPServerTableModel tableModel;
    private JButton addButton;
    private JButton editButton;
    private JButton removeButton;
    private JButton testConnectionButton;
    private JBCheckBox enableMCPCheckBox;

    private MCPServerRegistry serverRegistry;
    private List<MCPServerConfig> servers;

    public MCPConfigurationPanel() {
        initializeComponents();
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "MCP Servers";
    }

    @Override
    public @Nullable JComponent createComponent() {
        return mainPanel;
    }

    @Override
    public boolean isModified() {
        if (serverRegistry == null) return false;

        List<MCPServerConfig> currentServers = serverRegistry.getAllServers();
        return !servers.equals(currentServers);
    }

    @Override
    public void apply() throws ConfigurationException {
        if (serverRegistry == null) {
            serverRegistry = ApplicationManager.getApplication().getService(MCPServerRegistry.class);
        }

        try {
            // Valider tous les serveurs avant de les appliquer
            for (MCPServerConfig server : servers) {
                if (!server.isValid()) {
                    throw new ConfigurationException("Configuration invalide pour le serveur: " + server.getName());
                }
            }

            // Supprimer tous les serveurs actuels (sauf les builtin)
            List<MCPServerConfig> currentServers = new ArrayList<>(serverRegistry.getAllServers());
            for (MCPServerConfig server : currentServers) {
                if (server.getType() != MCPServerConfig.MCPServerType.BUILTIN) {
                    serverRegistry.unregisterServer(server.getId());
                }
            }

            // Ajouter les nouveaux serveurs
            for (MCPServerConfig server : servers) {
                if (server.getType() != MCPServerConfig.MCPServerType.BUILTIN) {
                    serverRegistry.registerServer(server);
                }
            }

            log.info("MCP server configuration applied successfully");

        } catch (Exception e) {
            log.error("Error applying MCP configuration", e);
            throw new ConfigurationException("Erreur lors de l'application de la configuration: " + e.getMessage());
        }
    }

    @Override
    public void reset() {
        loadServers();
        tableModel.fireTableDataChanged();
    }

    private void initializeComponents() {
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(JBUI.Borders.empty(10));

        // Header
        JPanel headerPanel = createHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Table des serveurs
        createServersTable();
        JScrollPane scrollPane = new JScrollPane(serversTable);
        scrollPane.setPreferredSize(new Dimension(600, 300));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Buttons panel
        JPanel buttonsPanel = createButtonsPanel();
        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        loadServers();
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(JBUI.Borders.emptyBottom(10));

        enableMCPCheckBox = new JBCheckBox("Activer les serveurs MCP", true);
        enableMCPCheckBox.addActionListener(e -> updateButtonStates());

        JBLabel descriptionLabel = new JBLabel(
                "<html>Configurez les serveurs MCP (Model Context Protocol) pour étendre les capacités de l'agent.<br>" +
                        "Les serveurs MCP permettent d'accéder à des services externes comme la recherche web, l'analyse de code, etc.</html>"
        );
        descriptionLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        headerPanel.add(enableMCPCheckBox, BorderLayout.NORTH);
        headerPanel.add(descriptionLabel, BorderLayout.CENTER);

        return headerPanel;
    }

    private void createServersTable() {
        tableModel = new MCPServerTableModel();
        serversTable = new JBTable(tableModel);

        serversTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        serversTable.getSelectionModel().addListSelectionListener(e -> updateButtonStates());

        // Configuration des colonnes
        serversTable.getColumnModel().getColumn(0).setPreferredWidth(150); // Name
        serversTable.getColumnModel().getColumn(1).setPreferredWidth(100); // Type
        serversTable.getColumnModel().getColumn(2).setPreferredWidth(200); // Endpoint
        serversTable.getColumnModel().getColumn(3).setPreferredWidth(80);  // Enabled
        serversTable.getColumnModel().getColumn(4).setPreferredWidth(100); // Capabilities
    }

    private JPanel createButtonsPanel() {
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonsPanel.setBorder(JBUI.Borders.emptyTop(10));

        addButton = new JButton("Ajouter");
        addButton.addActionListener(e -> addServer());

        editButton = new JButton("Modifier");
        editButton.addActionListener(e -> editServer());

        removeButton = new JButton("Supprimer");
        removeButton.addActionListener(e -> removeServer());

        testConnectionButton = new JButton("Tester");
        testConnectionButton.addActionListener(e -> testConnection());

        buttonsPanel.add(addButton);
        buttonsPanel.add(editButton);
        buttonsPanel.add(removeButton);
        buttonsPanel.add(Box.createHorizontalStrut(20));
        buttonsPanel.add(testConnectionButton);

        updateButtonStates();
        return buttonsPanel;
    }

    private void loadServers() {
        if (serverRegistry == null) {
            serverRegistry = ApplicationManager.getApplication().getService(MCPServerRegistry.class);
        }

        servers = new ArrayList<>(serverRegistry.getAllServers());
        if (tableModel != null) {
            tableModel.fireTableDataChanged();
        }
    }

    private void updateButtonStates() {
        boolean mcpEnabled = enableMCPCheckBox.isSelected();
        boolean hasSelection = serversTable.getSelectedRow() >= 0;

        addButton.setEnabled(mcpEnabled);
        editButton.setEnabled(mcpEnabled && hasSelection);
        removeButton.setEnabled(mcpEnabled && hasSelection && isSelectedServerRemovable());
        testConnectionButton.setEnabled(mcpEnabled && hasSelection);
        serversTable.setEnabled(mcpEnabled);
    }

    private boolean isSelectedServerRemovable() {
        int selectedRow = serversTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= servers.size()) return false;

        MCPServerConfig server = servers.get(selectedRow);
        return server.getType() != MCPServerConfig.MCPServerType.BUILTIN;
    }

    private void addServer() {
        MCPServerEditDialog dialog = new MCPServerEditDialog(null, null);
        if (dialog.showAndGet()) {
            MCPServerConfig newServer = dialog.getServerConfig();
            servers.add(newServer);
            tableModel.fireTableRowsInserted(servers.size() - 1, servers.size() - 1);
        }
    }

    private void editServer() {
        int selectedRow = serversTable.getSelectedRow();
        if (selectedRow < 0) return;

        MCPServerConfig server = servers.get(selectedRow);
        if (server.getType() == MCPServerConfig.MCPServerType.BUILTIN) {
            Messages.showInfoMessage(
                    "Les serveurs intégrés ne peuvent pas être modifiés.",
                    "Modification Impossible"
            );
            return;
        }

        MCPServerEditDialog dialog = new MCPServerEditDialog(mainPanel, server);
        if (dialog.showAndGet()) {
            MCPServerConfig updatedServer = dialog.getServerConfig();
            servers.set(selectedRow, updatedServer);
            tableModel.fireTableRowsUpdated(selectedRow, selectedRow);
        }
    }

    private void removeServer() {
        int selectedRow = serversTable.getSelectedRow();
        if (selectedRow < 0) return;

        MCPServerConfig server = servers.get(selectedRow);
        if (server.getType() == MCPServerConfig.MCPServerType.BUILTIN) {
            Messages.showInfoMessage(
                    "Les serveurs intégrés ne peuvent pas être supprimés.",
                    "Suppression Impossible"
            );
            return;
        }

        int result = Messages.showYesNoDialog(
                mainPanel,
                "Êtes-vous sûr de vouloir supprimer le serveur '" + server.getName() + "' ?",
                "Confirmer la Suppression",
                Messages.getQuestionIcon()
        );

        if (result == Messages.YES) {
            servers.remove(selectedRow);
            tableModel.fireTableRowsDeleted(selectedRow, selectedRow);
        }
    }

    private void testConnection() {
        int selectedRow = serversTable.getSelectedRow();
        if (selectedRow < 0) return;

        MCPServerConfig server = servers.get(selectedRow);

        // TODO: Implémenter le test de connexion réel
        Messages.showInfoMessage(
                mainPanel,
                "Test de connexion pour '" + server.getName() + "':\n\n" +
                        "Type: " + server.getType() + "\n" +
                        "Endpoint: " + server.getEndpoint() + "\n" +
                        "Statut: Simulation - OK",
                "Test de Connexion"
        );
    }

    /**
     * Model de table pour les serveurs MCP
     */
    private class MCPServerTableModel extends AbstractTableModel {
        private final String[] columnNames = {"Nom", "Type", "Endpoint", "Activé", "Capacités"};

        @Override
        public int getRowCount() {
            return servers.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 3 -> Boolean.class; // Enabled column
                default -> String.class;
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            // Seule la colonne "Activé" est éditable pour les serveurs non-builtin
            if (columnIndex == 3) {
                MCPServerConfig server = servers.get(rowIndex);
                return server.getType() != MCPServerConfig.MCPServerType.BUILTIN;
            }
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            MCPServerConfig server = servers.get(rowIndex);

            return switch (columnIndex) {
                case 0 -> server.getName();
                case 1 -> server.getType().toString();
                case 2 -> server.getEndpoint() != null ? server.getEndpoint() : "N/A";
                case 3 -> server.isEnabled();
                case 4 -> server.getCapabilities().size() + " capacité(s)";
                default -> "";
            };
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            if (columnIndex == 3 && value instanceof Boolean) {
                MCPServerConfig server = servers.get(rowIndex);
                // TODO: Créer une nouvelle instance avec l'état modifié
                // Pour l'instant, on simule juste le changement
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }
    }
}