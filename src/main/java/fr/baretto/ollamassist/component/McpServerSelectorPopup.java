package fr.baretto.ollamassist.component;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import fr.baretto.ollamassist.setting.McpServerConfig;
import fr.baretto.ollamassist.setting.McpSettings;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Popup component for selecting which MCP servers are active at runtime.
 * Displays a list of checkboxes for each configured MCP server.
 */
public class McpServerSelectorPopup {

    private final Project project;
    private final List<JCheckBox> serverCheckboxes = new ArrayList<>();
    private final Runnable onChangeCallback;

    /**
     * Create and show the MCP server selector popup.
     *
     * @param project the current project
     * @param parent the parent component (usually the MCP button)
     * @param onChangeCallback callback to invoke when selection changes
     */
    public static void show(Project project, Component parent, Runnable onChangeCallback) {
        new McpServerSelectorPopup(project, onChangeCallback).showPopup(parent);
    }

    private McpServerSelectorPopup(Project project,Runnable onChangeCallback) {
        this.project = project;
        this.onChangeCallback = onChangeCallback;
    }

    private void showPopup(Component parent) {
        JPanel panel = createPanel();

        ComponentPopupBuilder builder = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(panel, null)
                .setTitle("Select MCP Servers")
                .setMovable(true)
                .setResizable(false)
                .setRequestFocus(true)
                .setCancelOnClickOutside(true);

        JBPopup popup = builder.createPopup();
        popup.showUnderneathOf(parent);
    }

    private JPanel createPanel() {
        JBPanel<?> panel = new JBPanel<>();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.empty(10));

        McpSettings settings = McpSettings.getInstance(project);
        fr.baretto.ollamassist.mcp.McpRuntimeState runtimeState =
                fr.baretto.ollamassist.mcp.McpRuntimeState.getInstance(project);

        List<McpServerConfig> servers = settings.getMcpServers().stream()
                .filter(McpServerConfig::isEnabled)  // Only show enabled servers
                .toList();

        if (servers.isEmpty()) {
            JLabel emptyLabel = new JLabel("No MCP servers configured");
            emptyLabel.setForeground(JBColor.GRAY);
            panel.add(emptyLabel);
        } else {
            for (McpServerConfig config : servers) {
                JCheckBox checkbox = new JCheckBox(config.getName());
                checkbox.setSelected(runtimeState.isServerActive(config.getName()));
                checkbox.setToolTipText(config.getConfigurationSummary());

                checkbox.addActionListener(e -> {
                    runtimeState.setServerActive(config.getName(), checkbox.isSelected());
                    if (onChangeCallback != null) {
                        onChangeCallback.run();
                    }
                });

                serverCheckboxes.add(checkbox);
                panel.add(checkbox);
            }

            // Add separator
            panel.add(Box.createVerticalStrut(10));
            panel.add(new JSeparator());
            panel.add(Box.createVerticalStrut(10));

            // Add "Select All" / "Deselect All" buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton selectAllBtn = new JButton("Select All");
            JButton deselectAllBtn = new JButton("Deselect All");

            selectAllBtn.addActionListener(e -> {
                serverCheckboxes.forEach(cb -> cb.setSelected(true));
                servers.forEach(cfg -> runtimeState.setServerActive(cfg.getName(), true));
                if (onChangeCallback != null) {
                    onChangeCallback.run();
                }
            });

            deselectAllBtn.addActionListener(e -> {
                serverCheckboxes.forEach(cb -> cb.setSelected(false));
                servers.forEach(cfg -> runtimeState.setServerActive(cfg.getName(), false));
                if (onChangeCallback != null) {
                    onChangeCallback.run();
                }
            });

            buttonPanel.add(selectAllBtn);
            buttonPanel.add(deselectAllBtn);
            panel.add(buttonPanel);
        }

        return panel;
    }
}
