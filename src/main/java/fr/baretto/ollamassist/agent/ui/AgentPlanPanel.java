package fr.baretto.ollamassist.agent.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import fr.baretto.ollamassist.agent.AgentProgressEvent;
import lombok.extern.slf4j.Slf4j;
import fr.baretto.ollamassist.agent.plan.AgentPlan;
import fr.baretto.ollamassist.agent.plan.Phase;
import fr.baretto.ollamassist.agent.plan.Step;
import fr.baretto.ollamassist.chat.ui.IconUtils;
import fr.baretto.ollamassist.utils.FontUtils;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Inline Swing panel rendered inside MessagesPanel to display the agent plan lifecycle:
 * Planning → Plan ready (validate) → Execution progress → Completed / Aborted
 */
@Slf4j
public class AgentPlanPanel extends JPanel {

    private static final Border PANEL_BORDER = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, JBColor.namedColor("Plugins.tagBackground", JBColor.BLUE)),
            JBUI.Borders.empty(8, 12, 8, 8)
    );

    private final Consumer<AgentPlan> onValidate;

    private final JLabel statusLabel = new JLabel();
    private final JPanel phasesContainer = new JPanel();
    private final JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));

    private AgentPlan currentPlan;
    private final List<PhaseRow> phaseRows = new ArrayList<>();
    private boolean autoValidate = false;

    public AgentPlanPanel(Consumer<AgentPlan> onValidate) {
        super(new BorderLayout(0, 8));
        this.onValidate = onValidate;
        setBorder(PANEL_BORDER);
        setOpaque(false);
        buildLayout();
        showPlanning();
    }

    // -------------------------------------------------------------------------
    // Event handler — called from EDT-safe context
    // -------------------------------------------------------------------------

    public void handleEvent(AgentProgressEvent event) {
        SwingUtilities.invokeLater(() -> {
            switch (event.getType()) {
                case PLANNING -> showPlanning();
                case PLAN_READY -> showPlan(event.getPlan());
                case STEP_STARTED -> updateStep(event.getStep(), StepStatus.RUNNING);
                case STEP_COMPLETED -> updateStep(event.getStep(), StepStatus.SUCCESS);
                case STEP_FAILED -> updateStep(event.getStep(), StepStatus.FAILED);
                case CRITIC_THINKING -> setStatus(IconUtils.OLLAMASSIST_THINKING_ICON, "Evaluating progress...");
                case PLAN_ADAPTED -> adaptPlan(event.getPlan());
                case COMPLETED -> showCompleted();
                case ABORTED -> showAborted(event.getMessage());
            }
        });
    }

    // -------------------------------------------------------------------------
    // State transitions
    // -------------------------------------------------------------------------

    private void showPlanning() {
        setStatus(IconUtils.OLLAMASSIST_THINKING_ICON, "Generating execution plan...");
        phasesContainer.setVisible(false);
        actionsPanel.setVisible(false);
        revalidatePanel();
    }

    private void showPlan(AgentPlan plan) {
        if (plan == null) return;
        this.currentPlan = plan;

        setStatus(AllIcons.Actions.Commit, "Plan ready — " + plan.totalSteps() + " steps across " + plan.getPhases().size() + " phase(s)");

        buildPhasesUI(plan.getPhases());
        phasesContainer.setVisible(true);
        buildActionsPanel();
        actionsPanel.setVisible(true);
        revalidatePanel();

        if (autoValidate) {
            triggerValidation();
        }
    }

    private void updateStep(Step step, StepStatus status) {
        if (step == null) return;
        boolean found = phaseRows.stream()
                .flatMap(row -> row.stepRows.stream())
                .filter(sr -> sr.step.getId().equals(step.getId()))
                .findFirst()
                .map(sr -> { sr.setStatus(status); return true; })
                .orElse(false);
        if (!found) {
            log.warn("updateStep: no StepRow found for step id={} toolId={} description='{}'",
                    step.getId(), step.getToolId(), step.getDescription());
        }
    }

    private void adaptPlan(AgentPlan revisedPlan) {
        if (revisedPlan == null) return;
        this.currentPlan = revisedPlan;
        setStatus(AllIcons.Actions.Refresh, "Plan adapted — " + revisedPlan.totalSteps() + " remaining step(s)");

        // Rebuild only the remaining (non-completed) phases
        List<Phase> remaining = revisedPlan.getPhases();
        List<PhaseRow> completed = phaseRows.stream()
                .filter(r -> r.allCompleted())
                .toList();

        phasesContainer.removeAll();
        phaseRows.clear();

        for (PhaseRow done : completed) {
            phasesContainer.add(done.panel);
            phaseRows.add(done);
        }
        for (Phase phase : remaining) {
            PhaseRow row = new PhaseRow(phase);
            phaseRows.add(row);
            phasesContainer.add(row.panel);
        }

        revalidatePanel();
    }

    private void showCompleted() {
        setStatus(IconUtils.VALIDATE, "Task completed");
        actionsPanel.setVisible(false);
        revalidatePanel();
    }

    private void showAborted(String message) {
        setStatus(IconUtils.ERROR, message);
        actionsPanel.setVisible(false);
        revalidatePanel();
    }

    // -------------------------------------------------------------------------
    // Layout
    // -------------------------------------------------------------------------

    private void buildLayout() {
        phasesContainer.setLayout(new BoxLayout(phasesContainer, BoxLayout.Y_AXIS));
        phasesContainer.setOpaque(false);

        add(statusLabel, BorderLayout.NORTH);
        add(phasesContainer, BorderLayout.CENTER);
        add(actionsPanel, BorderLayout.SOUTH);
    }

    private void buildPhasesUI(List<Phase> phases) {
        phasesContainer.removeAll();
        phaseRows.clear();
        for (Phase phase : phases) {
            PhaseRow row = new PhaseRow(phase);
            phaseRows.add(row);
            phasesContainer.add(row.panel);
        }
    }

    private void buildActionsPanel() {
        actionsPanel.removeAll();
        actionsPanel.setOpaque(false);

        JButton validateButton = new JButton("Validate", AllIcons.Actions.Execute);
        validateButton.setFocusPainted(false);
        validateButton.addActionListener(e -> triggerValidation());

        JToggleButton autoButton = new JToggleButton("Auto-validate");
        autoButton.setFont(FontUtils.getSmallFont());
        autoButton.setFocusPainted(false);
        autoButton.setToolTipText("Automatically validate and execute each plan phase without confirmation");
        autoButton.addActionListener(e -> {
            if (autoButton.isSelected()) {
                int choice = JOptionPane.showConfirmDialog(
                        autoButton,
                        "Auto-validate will execute all agent phases without asking for confirmation.\n"
                                + "File writes, edits, and commands may run immediately.\n\n"
                                + "Enable auto-validate?",
                        "Auto-validate — Confirmation required",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (choice != JOptionPane.YES_OPTION) {
                    autoButton.setSelected(false);
                    return;
                }
            }
            autoValidate = autoButton.isSelected();
        });

        actionsPanel.add(validateButton);
        actionsPanel.add(autoButton);
    }

    private void triggerValidation() {
        actionsPanel.setVisible(false);
        setStatus(IconUtils.OLLAMASSIST_THINKING_ICON, "Executing...");
        revalidatePanel();
        if (currentPlan != null) {
            onValidate.accept(currentPlan);
        }
    }

    private void setStatus(Icon icon, String text) {
        statusLabel.setIcon(icon);
        statusLabel.setText(text);
        statusLabel.setFont(FontUtils.getSmallFont());
    }

    private void revalidatePanel() {
        revalidate();
        repaint();
        // Trigger parent scroll refresh
        Container parent = getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    private static class PhaseRow {
        final Phase phase;
        final JPanel panel;
        final List<StepRow> stepRows = new ArrayList<>();

        PhaseRow(Phase phase) {
            this.phase = phase;
            this.panel = buildPanel();
        }

        boolean allCompleted() {
            return stepRows.stream().allMatch(r -> r.status == StepStatus.SUCCESS);
        }

        private JPanel buildPanel() {
            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setOpaque(false);
            p.setBorder(JBUI.Borders.empty(4, 0, 4, 0));

            JLabel phaseLabel = new JLabel(phase.getDescription());
            phaseLabel.setFont(FontUtils.getSmallFont().deriveFont(Font.BOLD));
            phaseLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(phaseLabel);

            for (Step step : phase.getSteps()) {
                StepRow row = new StepRow(step);
                stepRows.add(row);
                p.add(row.panel);
            }

            return p;
        }
    }

    private static class StepRow {
        final Step step;
        final JPanel panel;
        final JLabel iconLabel;
        final JLabel textLabel;
        StepStatus status = StepStatus.PENDING;

        StepRow(Step step) {
            this.step = step;
            iconLabel = new JLabel(StepStatus.PENDING.icon());
            textLabel = new JLabel(step.getDescription());
            textLabel.setFont(FontUtils.getSmallFont());
            textLabel.setForeground(JBColor.GRAY);

            panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
            panel.setOpaque(false);
            panel.add(iconLabel);
            panel.add(textLabel);
        }

        void setStatus(StepStatus newStatus) {
            this.status = newStatus;
            iconLabel.setIcon(newStatus.icon());
            textLabel.setForeground(newStatus == StepStatus.FAILED ? JBColor.RED
                    : newStatus == StepStatus.SUCCESS ? JBColor.namedColor("Label.successForeground", JBColor.GREEN)
                    : JBColor.GRAY);
        }
    }

    private enum StepStatus {
        PENDING {
            @Override
            public Icon icon() {
                return AllIcons.General.Ellipsis;
            }
        },
        RUNNING {
            @Override
            public Icon icon() {
                return IconUtils.OLLAMASSIST_THINKING_ICON;
            }
        },
        SUCCESS {
            @Override
            public Icon icon() {
                return AllIcons.Actions.Checked;
            }
        },
        FAILED {
            @Override
            public Icon icon() {
                return AllIcons.General.Error;
            }
        };

        public abstract Icon icon();
    }
}
