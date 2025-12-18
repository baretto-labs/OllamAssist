package fr.baretto.ollamassist.mcp.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Map;

/**
 * Dialog for requesting user approval before executing an MCP tool.
 * Displays the tool name, server, and arguments for transparency.
 */
public class McpToolApprovalDialog extends DialogWrapper {

    private final String serverName;
    private final String toolName;
    private final Map<String, Object> arguments;
    private JBCheckBox alwaysApproveCheckbox;

    public McpToolApprovalDialog(
            @NotNull Project project,
            @NotNull String serverName,
            @NotNull String toolName,
            @NotNull Map<String, Object> arguments
    ) {
        super(project);
        this.serverName = serverName;
        this.toolName = toolName;
        this.arguments = arguments;
        setTitle("MCP Tool Execution Approval");
        setModal(true);
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(JBUI.Borders.empty(10));

        // Warning header with icon
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JLabel warningIcon = new JLabel(UIManager.getIcon("OptionPane.warningIcon"));
        JBLabel messageLabel = new JBLabel(
            "<html><b>The AI assistant wants to execute an MCP tool.</b><br>" +
            "Please review the details below and approve or deny the execution.</html>"
        );
        headerPanel.add(warningIcon);
        headerPanel.add(messageLabel);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Details panel
        JPanel detailsPanel = new JPanel();
        detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));
        detailsPanel.setBorder(JBUI.Borders.empty(10));

        // Server name
        JPanel serverPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        serverPanel.add(new JBLabel("<html><b>MCP Server:</b></html>"));
        serverPanel.add(new JBLabel(serverName));
        detailsPanel.add(serverPanel);

        // Tool name
        JPanel toolPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolPanel.add(new JBLabel("<html><b>Tool Name:</b></html>"));
        toolPanel.add(new JBLabel(toolName));
        detailsPanel.add(toolPanel);

        // Arguments (if present)
        if (arguments != null && !arguments.isEmpty()) {
            detailsPanel.add(Box.createVerticalStrut(10));
            detailsPanel.add(new JBLabel("<html><b>Arguments:</b></html>"));

            JTextArea argsArea = new JTextArea(formatArguments(arguments));
            argsArea.setEditable(false);
            argsArea.setRows(Math.min(arguments.size() + 2, 10));
            argsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            argsArea.setBorder(JBUI.Borders.empty(5));
            argsArea.setBackground(JBColor.namedColor("Editor.background", mainPanel.getBackground()));

            JBScrollPane scrollPane = new JBScrollPane(argsArea);
            scrollPane.setPreferredSize(new Dimension(500, 150));
            detailsPanel.add(scrollPane);
        }

        mainPanel.add(detailsPanel, BorderLayout.CENTER);

        // Always approve checkbox
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(JBUI.Borders.emptyTop(10));

        alwaysApproveCheckbox = new JBCheckBox("Always approve this tool for this session");
        alwaysApproveCheckbox.setToolTipText(
            "If checked, this tool will be automatically approved until the project is closed"
        );
        bottomPanel.add(alwaysApproveCheckbox, BorderLayout.WEST);

        JBLabel warningLabel = new JBLabel(
            "<html><i>Warning: Only approve tools from trusted sources</i></html>"
        );
        warningLabel.setForeground(JBColor.ORANGE);
        bottomPanel.add(warningLabel, BorderLayout.SOUTH);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        return mainPanel;
    }

    /**
     * Format arguments as human-readable text.
     */
    private String formatArguments(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return "(no arguments)";
        }

        StringBuilder sb = new StringBuilder();
        args.forEach((key, value) -> {
            sb.append("  ").append(key).append(": ");
            if (value == null) {
                sb.append("null");
            } else if (value instanceof String s && s.length() > 100) {
                // Truncate long strings
                sb.append(s, 0, 100).append("...");
            } else {
                sb.append(value);
            }
            sb.append("\n");
        });
        return sb.toString();
    }

    /**
     * Check if user selected "always approve this tool".
     */
    public boolean isAlwaysApprove() {
        return alwaysApproveCheckbox.isSelected();
    }

    @NotNull
    @Override
    protected Action[] createActions() {
        return new Action[]{
            new DialogWrapperAction("Approve") {
                {
                    putValue(DEFAULT_ACTION, true);
                }

                @Override
                protected void doAction(ActionEvent e) {
                    close(OK_EXIT_CODE);
                }
            },
            new DialogWrapperAction("Deny") {
                @Override
                protected void doAction(ActionEvent e) {
                    close(CANCEL_EXIT_CODE);
                }
            }
        };
    }

    @Nullable
    @Override
    protected String getDimensionServiceKey() {
        return "OllamAssist.McpToolApprovalDialog";
    }
}
