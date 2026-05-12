package fr.baretto.ollamassist.setting.panels;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import fr.baretto.ollamassist.agent.AgentMemoryService;
import fr.baretto.ollamassist.setting.OllamaSettings;

import javax.swing.*;
import java.awt.*;

public class AgentConfigPanel extends JBPanel<AgentConfigPanel> {

    private final JSpinner planTimeoutSpinner;
    private final JSpinner commandTimeoutSpinner;
    private final JSpinner approvalTimeoutSpinner;
    private final JSpinner toolTimeoutSpinner;
    private final JSpinner globalTimeoutSpinner;
    private final JCheckBox paranoidModeCheckbox = new JCheckBox("Paranoid mode (Critic after every step)");
    private final Project project;

    public AgentConfigPanel(Project project) {
        this.project = project;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(JBUI.Borders.empty(10));

        OllamaSettings settings = OllamaSettings.getInstance();

        planTimeoutSpinner = new JSpinner(new SpinnerNumberModel(
                settings.getAgentPlanTimeoutSeconds(), 10, 3600, 10));
        commandTimeoutSpinner = new JSpinner(new SpinnerNumberModel(
                settings.getRunCommandTimeoutSeconds(), 5, 3600, 5));
        approvalTimeoutSpinner = new JSpinner(new SpinnerNumberModel(
                settings.getApprovalTimeoutMinutes(), 1, 60, 1));
        toolTimeoutSpinner = new JSpinner(new SpinnerNumberModel(
                settings.getAgentToolTimeoutSeconds(), 10, 3600, 10));
        globalTimeoutSpinner = new JSpinner(new SpinnerNumberModel(
                settings.getAgentGlobalTimeoutMinutes(), 0, 120, 5));

        add(createSection("Timeouts",
                createSpinnerRow("Plan generation timeout (seconds):",
                        "Maximum time allowed for the planner LLM to produce a plan.",
                        planTimeoutSpinner),
                createSpinnerRow("Command execution timeout (seconds):",
                        "Maximum time allowed for a terminal command to complete.",
                        commandTimeoutSpinner),
                createSpinnerRow("Per-tool timeout (seconds):",
                        "Maximum time a single tool call can run before it is cancelled.",
                        toolTimeoutSpinner),
                createSpinnerRow("Approval timeout (minutes):",
                        "How long the agent waits for user approval before aborting a mutating or destructive step.",
                        approvalTimeoutSpinner),
                createSpinnerRow("Global execution timeout (minutes, 0 = disabled):",
                        "Maximum wall-clock time for an entire agent execution. 0 disables the global timeout.",
                        globalTimeoutSpinner)
        ));

        add(Box.createVerticalStrut(10));

        add(createSection("Safety",
                createCheckboxRow(paranoidModeCheckbox,
                        "<html>When enabled, the Critic evaluates the result after every step — not only after each phase.<br/>"
                                + "This increases safety for sensitive operations (file deletion, command execution)<br/>"
                                + "at the cost of additional LLM calls per execution.</html>")
        ));

        paranoidModeCheckbox.setSelected(settings.isAgentParanoidMode());

        add(Box.createVerticalStrut(10));
        add(createMemorySection());

        add(Box.createVerticalGlue());
    }

    private JPanel createMemorySection() {
        JPanel section = new JBPanel<>();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Memory"),
                JBUI.Borders.empty(8, 8, 4, 8)
        ));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel desc = new JLabel("<html>The agent records past executions to avoid repeating work.<br/>"
                + "Clear this if you want the agent to start fresh.</html>");
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);
        desc.setBorder(JBUI.Borders.emptyBottom(8));
        section.add(desc);

        JButton clearBtn = new JButton("Clear Agent Memory");
        clearBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        clearBtn.addActionListener(e -> {
            if (project != null) {
                AgentMemoryService mem = project.getService(AgentMemoryService.class);
                if (mem != null) {
                    mem.clearMemory();
                    JOptionPane.showMessageDialog(this,
                            "Agent memory cleared. The planner will not reference past executions.",
                            "Memory Cleared", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        });
        section.add(clearBtn);

        return section;
    }

    // -------------------------------------------------------------------------
    // Getters / setters (used by ConfigurationPanel and OllamassistSettingsConfigurable)
    // -------------------------------------------------------------------------

    public int getAgentPlanTimeoutSeconds() {
        return (int) planTimeoutSpinner.getValue();
    }

    public void setAgentPlanTimeoutSeconds(int seconds) {
        planTimeoutSpinner.setValue(seconds);
    }

    public int getRunCommandTimeoutSeconds() {
        return (int) commandTimeoutSpinner.getValue();
    }

    public void setRunCommandTimeoutSeconds(int seconds) {
        commandTimeoutSpinner.setValue(seconds);
    }

    public int getApprovalTimeoutMinutes() {
        return (int) approvalTimeoutSpinner.getValue();
    }

    public void setApprovalTimeoutMinutes(int minutes) {
        approvalTimeoutSpinner.setValue(minutes);
    }

    public int getAgentToolTimeoutSeconds() {
        return (int) toolTimeoutSpinner.getValue();
    }

    public void setAgentToolTimeoutSeconds(int seconds) {
        toolTimeoutSpinner.setValue(seconds);
    }

    public int getAgentGlobalTimeoutMinutes() {
        return (int) globalTimeoutSpinner.getValue();
    }

    public void setAgentGlobalTimeoutMinutes(int minutes) {
        globalTimeoutSpinner.setValue(minutes);
    }

    public boolean isParanoidMode() {
        return paranoidModeCheckbox.isSelected();
    }

    public void setParanoidMode(boolean value) {
        paranoidModeCheckbox.setSelected(value);
    }

    public JCheckBox getParanoidModeCheckbox() {
        return paranoidModeCheckbox;
    }

    // -------------------------------------------------------------------------
    // Layout helpers
    // -------------------------------------------------------------------------

    private JPanel createSection(String title, JPanel... rows) {
        JPanel section = new JBPanel<>();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                JBUI.Borders.empty(8, 8, 4, 8)
        ));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (JPanel row : rows) {
            section.add(row);
        }
        return section;
    }

    private JPanel createSpinnerRow(String label, String tooltip, JSpinner spinner) {
        JPanel row = new JBPanel<>();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBorder(JBUI.Borders.empty(4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JBLabel fieldLabel = new JBLabel(label);
        fieldLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(fieldLabel);

        row.add(Box.createVerticalStrut(4));

        spinner.setAlignmentX(Component.LEFT_ALIGNMENT);
        spinner.setMaximumSize(new Dimension(120, 28));
        spinner.setPreferredSize(new Dimension(120, 28));
        row.add(spinner);

        if (tooltip != null) {
            JLabel hint = new JLabel("<html><font color='gray'><i>" + tooltip + "</i></font></html>");
            hint.setAlignmentX(Component.LEFT_ALIGNMENT);
            hint.setBorder(JBUI.Borders.emptyTop(3));
            row.add(hint);
        }

        return row;
    }

    private JPanel createCheckboxRow(JCheckBox checkbox, String description) {
        JPanel row = new JBPanel<>();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBorder(JBUI.Borders.empty(4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        checkbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(checkbox);

        if (description != null) {
            JLabel hint = new JLabel(description);
            hint.setAlignmentX(Component.LEFT_ALIGNMENT);
            hint.setBorder(JBUI.Borders.empty(3, 20, 0, 0));
            row.add(hint);
        }

        return row;
    }
}
