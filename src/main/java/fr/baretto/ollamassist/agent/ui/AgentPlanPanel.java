package fr.baretto.ollamassist.agent.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import fr.baretto.ollamassist.agent.AgentProgressEvent;
import fr.baretto.ollamassist.agent.StepRetryDecision;
import fr.baretto.ollamassist.agent.tools.ToolRegistry;
import fr.baretto.ollamassist.setting.OllamaSettings;
import lombok.extern.slf4j.Slf4j;
import fr.baretto.ollamassist.agent.plan.AgentPlan;
import fr.baretto.ollamassist.agent.plan.Phase;
import fr.baretto.ollamassist.agent.plan.Step;
import fr.baretto.ollamassist.chat.ui.IconUtils;
import fr.baretto.ollamassist.utils.FontUtils;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final Runnable onCancel;

    private final JLabel statusLabel = new JLabel();
    private final JPanel phasesContainer = new JPanel();
    private final JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private JButton cancelButton;

    /**
     * Validation mode for plan execution.
     * <ul>
     *   <li>MANUAL — user must click Validate before any execution starts.</li>
     *   <li>SMART  — auto-validate if the plan contains only READ_ONLY tools;
     *                require manual validation otherwise.</li>
     *   <li>FULL_AUTO — always execute without asking, regardless of tool types.</li>
     * </ul>
     */
    public enum AutoValidateMode {
        MANUAL("Manual"),
        SMART("Smart"),
        FULL_AUTO("Full auto");

        final String label;
        AutoValidateMode(String label) { this.label = label; }

        static AutoValidateMode fromSetting(String value) {
            for (AutoValidateMode m : values()) {
                if (m.name().equals(value)) return m;
            }
            return MANUAL;
        }
    }

    private AgentPlan currentPlan;
    private final List<PhaseRow> phaseRows = new ArrayList<>();
    private AutoValidateMode autoValidateMode = loadModeFromSettings();
    @Nullable private final Project project;
    /** Tracks whether the last execution wrote any files (enables the Undo button). */
    private boolean executionHadWrites = false;

    public AgentPlanPanel(Consumer<AgentPlan> onValidate, Runnable onCancel) {
        this(onValidate, onCancel, null);
    }

    public AgentPlanPanel(Consumer<AgentPlan> onValidate, Runnable onCancel, @Nullable Project project) {
        super(new BorderLayout(0, 8));
        this.onValidate = onValidate;
        this.onCancel = onCancel;
        this.project = project;
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
                case PLANNING -> {
                    executionHadWrites = false;
                    showPlanning();
                }
                case PLAN_READY -> showPlan(event.getPlan());
                case STEP_STARTED -> {
                    int completed = (int) phaseRows.stream()
                            .flatMap(r -> r.stepRows.stream())
                            .filter(sr -> sr.status == StepStatus.SUCCESS || sr.status == StepStatus.FAILED)
                            .count();
                    int total = phaseRows.stream().mapToInt(r -> r.stepRows.size()).sum();
                    // Find which phase contains the running step to display "phase Y/Z" (U-4)
                    int phaseIdx = -1;
                    Step runningStep = event.getStep();
                    if (runningStep != null) {
                        for (int i = 0; i < phaseRows.size(); i++) {
                            boolean containsStep = phaseRows.get(i).stepRows.stream()
                                    .anyMatch(sr -> sr.step.getId().equals(runningStep.getId()));
                            if (containsStep) { phaseIdx = i + 1; break; }
                        }
                    }
                    String progress;
                    if (total > 0) {
                        progress = "Running — step " + (completed + 1) + "/" + total;
                        if (phaseIdx > 0) {
                            progress += " · phase " + phaseIdx + "/" + phaseRows.size();
                        }
                    } else {
                        progress = "Running...";
                    }
                    setStatus(IconUtils.OLLAMASSIST_THINKING_ICON, progress);
                    updateStep(event.getStep(), StepStatus.RUNNING);
                    showCancelButton();
                }
                case STEP_COMPLETED -> {
                    updateStep(event.getStep(), StepStatus.SUCCESS, event.getOutput());
                    if (event.getStep() != null && isWriteToolId(event.getStep().getToolId())) {
                        executionHadWrites = true;
                    }
                }
                case STEP_FAILED -> updateStep(event.getStep(), StepStatus.FAILED, event.getOutput());
                case CRITIC_THINKING -> setStatus(IconUtils.OLLAMASSIST_THINKING_ICON, event.getMessage());
                case PLAN_ADAPTED -> adaptPlan(event.getPlan());
                case COMPLETED -> {
                    showCompleted(event.getMessage());
                    showUndoButtonIfNeeded();
                }
                case ABORTED -> {
                    showAborted(event.getMessage());
                    showUndoButtonIfNeeded();
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Retry decision UI
    // -------------------------------------------------------------------------

    /**
     * Attaches [Retry] / [Skip] buttons to the failed step row identified by {@code step.getId()}.
     * Must be called from any thread — switches to the EDT internally.
     *
     * @param step       the failed step
     * @param onDecision callback invoked with the user's decision
     */
    public void showRetryButtons(Step step, Consumer<StepRetryDecision> onDecision) {
        SwingUtilities.invokeLater(() -> {
            boolean found = phaseRows.stream()
                    .flatMap(r -> r.stepRows.stream())
                    .filter(sr -> sr.step.getId().equals(step.getId()))
                    .findFirst()
                    .map(sr -> { sr.showRetryButtons(onDecision); return true; })
                    .orElse(false);
            if (!found) {
                log.warn("showRetryButtons: no StepRow found for step id={}", step.getId());
                onDecision.accept(StepRetryDecision.ABORT_PHASE);
            }
            revalidatePanel();
        });
    }

    // -------------------------------------------------------------------------
    // State transitions
    // -------------------------------------------------------------------------

    private void showPlanning() {
        setStatus(IconUtils.OLLAMASSIST_THINKING_ICON, "Generating execution plan...");
        phasesContainer.setVisible(false);
        // Show Cancel immediately: planning can take 30-120s on local models, and the user
        // must be able to abort without clicking Stop in PromptPanel (which leaves zombie state).
        showCancelButton();
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

        if (autoValidateMode == AutoValidateMode.FULL_AUTO
                || (autoValidateMode == AutoValidateMode.SMART && isReadOnlyPlan(plan))) {
            triggerValidation();
        }
    }

    private void updateStep(Step step, StepStatus status) {
        updateStep(step, status, null);
    }

    private void updateStep(Step step, StepStatus status, @Nullable String contextText) {
        if (step == null) return;
        boolean found = false;
        for (PhaseRow phaseRow : phaseRows) {
            for (StepRow sr : phaseRow.stepRows) {
                if (sr.step.getId().equals(step.getId())) {
                    sr.setStatus(status, contextText);
                    // Auto-collapse the phase once all its steps have succeeded.
                    // The small invokeLater delay lets the last green checkmark render
                    // before the phase folds, giving the user a brief visual confirmation.
                    if (status == StepStatus.SUCCESS && phaseRow.allCompleted() && !phaseRow.collapsed) {
                        SwingUtilities.invokeLater(phaseRow::toggleCollapse);
                    }
                    found = true;
                    break;
                }
            }
            if (found) break;
        }
        if (!found) {
            log.warn("updateStep: no StepRow found for step id={} toolId={} description='{}'",
                    step.getId(), step.getToolId(), step.getDescription());
        }
    }

    private void adaptPlan(AgentPlan revisedPlan) {
        if (revisedPlan == null) return;
        this.currentPlan = revisedPlan;
        setStatus(AllIcons.Actions.Refresh, "Plan adapted — " + revisedPlan.totalSteps() + " remaining step(s)");

        // Snapshot collapse/expand state before rebuilding so it is preserved across ADAPT (U-5).
        // Key = phase description (stable across ADAPT revisions of the same phase).
        Map<String, Boolean> collapseSnapshot = new HashMap<>();
        for (PhaseRow r : phaseRows) {
            collapseSnapshot.put(r.phase.getDescription(), r.collapsed);
        }

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
            PhaseRow row = new PhaseRow(phase, this::onStepEdited, this::onStepDeleted);
            // Restore collapse state if this phase was already visible before the ADAPT (U-5).
            Boolean wasCollapsed = collapseSnapshot.get(phase.getDescription());
            if (wasCollapsed != null && wasCollapsed != row.collapsed) {
                row.toggleCollapse();
            }
            phaseRows.add(row);
            phasesContainer.add(row.panel);
        }

        revalidatePanel();
    }

    private void showCompleted(String message) {
        setStatus(IconUtils.VALIDATE, message);
        actionsPanel.setVisible(false);
        revalidatePanel();
    }

    private void showAborted(String message) {
        setStatus(IconUtils.ERROR, message);
        actionsPanel.setVisible(false);
        revalidatePanel();
    }

    /** Shows the "Undo last execution" button if the execution wrote or deleted files. */
    private void showUndoButtonIfNeeded() {
        if (!executionHadWrites || project == null) return;
        UndoManager undoManager = UndoManager.getInstance(project);
        if (undoManager == null || !undoManager.isUndoAvailable(null)) return;
        actionsPanel.removeAll();
        actionsPanel.setOpaque(false);
        JButton undoBtn = new JButton("Undo execution", AllIcons.Actions.Rollback);
        undoBtn.setFocusPainted(false);
        undoBtn.setFont(FontUtils.getSmallFont());
        undoBtn.setToolTipText("Undo all file writes and deletes from this agent execution (Ctrl+Z also works)");
        undoBtn.addActionListener(e -> {
            undoBtn.setEnabled(false);
            // Undo all agent write commands that share the same groupId (correlationId).
            // Since WriteCommandAction was called with the correlationId as groupId, IntelliJ
            // merges them into one undo entry — a single undo call reverses the entire execution.
            if (undoManager.isUndoAvailable(null)) {
                undoManager.undo(null);
            }
            actionsPanel.setVisible(false);
            revalidatePanel();
        });
        actionsPanel.add(undoBtn);
        actionsPanel.setVisible(true);
        revalidatePanel();
    }

    private static boolean isWriteToolId(String toolId) {
        return "FILE_WRITE".equals(toolId) || "FILE_EDIT".equals(toolId) || "FILE_DELETE".equals(toolId);
    }

    private void showCancelButton() {
        if (cancelButton != null && cancelButton.isVisible()) return;
        actionsPanel.removeAll();
        actionsPanel.setOpaque(false);
        cancelButton = new JButton("Cancel", AllIcons.Actions.Suspend);
        cancelButton.setFocusPainted(false);
        cancelButton.setFont(FontUtils.getSmallFont());
        cancelButton.addActionListener(e -> {
            cancelButton.setEnabled(false);
            if (onCancel != null) onCancel.run();
        });
        actionsPanel.add(cancelButton);
        actionsPanel.setVisible(true);
        revalidatePanel();
    }

    // -------------------------------------------------------------------------
    // Layout
    // -------------------------------------------------------------------------

    private void buildLayout() {
        phasesContainer.setLayout(new BoxLayout(phasesContainer, BoxLayout.Y_AXIS));
        phasesContainer.setOpaque(false);

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setOpaque(false);
        topPanel.add(buildPreviewBanner());
        topPanel.add(statusLabel);

        add(topPanel, BorderLayout.NORTH);
        add(phasesContainer, BorderLayout.CENTER);
        add(actionsPanel, BorderLayout.SOUTH);
    }

    // Same key as PromptPanel — set to true once the user dismisses the first-launch info dialog.
    private static final String AGENT_PREVIEW_SHOWN_KEY = "ollamassist.agent.preview.shown";

    private static JPanel buildPreviewBanner() {
        JPanel banner = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
        banner.setOpaque(false);
        banner.setBorder(JBUI.Borders.empty(0, 0, 4, 0));

        JLabel icon = new JLabel(AllIcons.General.Beta);

        // After the user has acknowledged the preview warning (first-launch dialog in PromptPanel),
        // replace the verbose warning with a compact "Agent mode" label to reduce visual noise.
        boolean acknowledged = PropertiesComponent.getInstance()
                .getBoolean(AGENT_PREVIEW_SHOWN_KEY, false);
        String labelText = acknowledged
                ? "Agent mode"
                : "Agent mode — Preview (experimental, use with care)";
        JLabel text = new JLabel(labelText);
        text.setFont(text.getFont().deriveFont(Font.ITALIC, text.getFont().getSize() - 1f));
        text.setForeground(JBColor.namedColor("Component.infoForeground", JBColor.GRAY));

        banner.add(icon);
        banner.add(text);
        return banner;
    }

    private void buildPhasesUI(List<Phase> phases) {
        phasesContainer.removeAll();
        phaseRows.clear();
        for (Phase phase : phases) {
            PhaseRow row = new PhaseRow(phase, this::onStepEdited, this::onStepDeleted);
            phaseRows.add(row);
            phasesContainer.add(row.panel);
        }
    }

    /** Called when a step description is edited by the user. Rebuilds currentPlan. */
    private void onStepEdited() {
        rebuildPlanFromRows();
        revalidatePanel();
    }

    /** Called when a step is deleted. Removes empty phases and rebuilds currentPlan. */
    private void onStepDeleted() {
        // Remove phase rows that have no remaining steps
        List<PhaseRow> toRemove = phaseRows.stream()
                .filter(r -> r.stepRows.isEmpty())
                .collect(Collectors.toList());
        for (PhaseRow empty : toRemove) {
            phaseRows.remove(empty);
            phasesContainer.remove(empty.panel);
        }
        rebuildPlanFromRows();
        updateStatusAfterEdit();
        revalidatePanel();
    }

    private void rebuildPlanFromRows() {
        if (currentPlan == null) return;
        List<Phase> phases = phaseRows.stream()
                .map(r -> new Phase(r.phase.getDescription(),
                        r.stepRows.stream().map(sr -> sr.step).collect(Collectors.toList())))
                .collect(Collectors.toList());
        currentPlan = new AgentPlan(currentPlan.getGoal(), currentPlan.getReasoning(), phases);
    }

    private void updateStatusAfterEdit() {
        int totalSteps = phaseRows.stream().mapToInt(r -> r.stepRows.size()).sum();
        setStatus(AllIcons.Actions.Commit,
                "Plan ready — " + totalSteps + " step(s) across " + phaseRows.size() + " phase(s)");
    }

    private void buildActionsPanel() {
        actionsPanel.removeAll();
        actionsPanel.setOpaque(false);

        JButton validateButton = new JButton("Run", AllIcons.Actions.Execute);
        validateButton.setFocusPainted(false);
        validateButton.setToolTipText("Execute this plan");
        validateButton.addActionListener(e -> triggerValidation());

        JComboBox<String> modeCombo = new JComboBox<>(
                new String[]{AutoValidateMode.MANUAL.label, AutoValidateMode.SMART.label, AutoValidateMode.FULL_AUTO.label});
        modeCombo.setFont(FontUtils.getSmallFont());
        modeCombo.setSelectedIndex(autoValidateMode.ordinal());
        modeCombo.setToolTipText(
                "<html><b>Manual</b>: always confirm before executing.<br>"
                + "<b>Smart</b>: auto-execute read-only plans, confirm mutating ones.<br>"
                + "<b>Full auto</b>: execute immediately without confirmation (use with care).</html>");
        modeCombo.addActionListener(e -> {
            int idx = modeCombo.getSelectedIndex();
            AutoValidateMode selected = AutoValidateMode.values()[idx];
            if (selected == AutoValidateMode.FULL_AUTO && autoValidateMode != AutoValidateMode.FULL_AUTO) {
                int choice = JOptionPane.showConfirmDialog(
                        modeCombo,
                        "Full auto will execute all agent phases immediately without asking for confirmation.\n"
                                + "File writes, edits, and commands may run immediately.\n\n"
                                + "Enable Full auto?",
                        "Full auto — Confirmation required",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (choice != JOptionPane.YES_OPTION) {
                    modeCombo.setSelectedIndex(autoValidateMode.ordinal());
                    return;
                }
            }
            autoValidateMode = selected;
            saveModeToSettings(autoValidateMode);
        });

        actionsPanel.add(validateButton);
        actionsPanel.add(modeCombo);
    }

    private static AutoValidateMode loadModeFromSettings() {
        try {
            return AutoValidateMode.fromSetting(OllamaSettings.getInstance().getAgentAutoValidateMode());
        } catch (Exception e) {
            return AutoValidateMode.MANUAL;
        }
    }

    private static void saveModeToSettings(AutoValidateMode mode) {
        try {
            OllamaSettings.getInstance().setAgentAutoValidateMode(mode.name());
        } catch (Exception e) {
            // settings not available in test context — ignore
        }
    }

    /** Returns true if every step in the plan uses a READ_ONLY tool. */
    private static boolean isReadOnlyPlan(AgentPlan plan) {
        return plan.getPhases().stream()
                .flatMap(p -> p.getSteps().stream())
                .allMatch(s -> ToolRegistry.READ_ONLY_TOOL_IDS.contains(s.getToolId()));
    }

    private void triggerValidation() {
        actionsPanel.setVisible(false);
        // Lock edit/delete buttons immediately — before execution starts asynchronously.
        // Without this, there is a race window between this call and the first STEP_STARTED
        // event (which hides buttons in setStatus()), during which the user could corrupt
        // the running plan (U-2).
        lockStepEditButtons();
        // Status will be updated to "Running — step 1/N" as soon as the first STEP_STARTED arrives.
        // Show a neutral "Queued" message in the interim so the label doesn't linger on "Plan ready".
        setStatus(IconUtils.OLLAMASSIST_THINKING_ICON, "Queued for execution...");
        revalidatePanel();
        if (currentPlan != null) {
            onValidate.accept(currentPlan);
        }
    }

    /** Hides all edit/delete buttons across all step rows immediately. */
    private void lockStepEditButtons() {
        for (PhaseRow phaseRow : phaseRows) {
            for (StepRow stepRow : phaseRow.stepRows) {
                stepRow.lockEditButtons();
            }
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

    private class PhaseRow {
        final Phase phase;
        final JPanel panel;
        final List<StepRow> stepRows = new ArrayList<>();
        private final Runnable onEdit;
        private final Runnable onDelete;
        private boolean collapsed = false;
        private JPanel stepsContainer;
        private JLabel collapseIcon;

        PhaseRow(Phase phase, Runnable onEdit, Runnable onDelete) {
            this.phase = phase;
            this.onEdit = onEdit;
            this.onDelete = onDelete;
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

            // Steps container — hidden when phase is collapsed
            stepsContainer = new JPanel();
            stepsContainer.setLayout(new BoxLayout(stepsContainer, BoxLayout.Y_AXIS));
            stepsContainer.setOpaque(false);

            // Phase header row: collapse arrow + phase description
            JPanel headerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            headerRow.setOpaque(false);
            headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            headerRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            headerRow.setToolTipText("Click to collapse/expand this phase");

            collapseIcon = new JLabel(AllIcons.General.ArrowDown);
            JLabel phaseLabel = new JLabel(phase.getDescription());
            phaseLabel.setFont(FontUtils.getSmallFont().deriveFont(Font.BOLD));

            headerRow.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    toggleCollapse();
                }
            });

            headerRow.add(collapseIcon);
            headerRow.add(phaseLabel);
            p.add(headerRow);

            for (Step step : phase.getSteps()) {
                StepRow row = new StepRow(step, this, onEdit, onDelete);
                stepRows.add(row);
                stepsContainer.add(row.panel);
            }
            p.add(stepsContainer);

            return p;
        }

        void toggleCollapse() {
            collapsed = !collapsed;
            stepsContainer.setVisible(!collapsed);
            collapseIcon.setIcon(collapsed ? AllIcons.General.ArrowRight : AllIcons.General.ArrowDown);
            Container parent = panel.getParent();
            if (parent != null) {
                parent.revalidate();
                parent.repaint();
            }
        }

        void removeStepRow(StepRow stepRow) {
            stepRows.remove(stepRow);
            stepsContainer.remove(stepRow.panel);
        }

        void replaceStep(StepRow stepRow, Step newStep) {
            int idx = stepRows.indexOf(stepRow);
            if (idx >= 0) {
                stepRows.set(idx, new StepRow(newStep, this, onEdit, onDelete));
            }
        }
    }

    /** Max chars shown inline below a step. Click the label to see the full output. */
    private static final int MAX_OUTPUT_DISPLAY_CHARS = 80;

    private static class StepRow {
        Step step;
        final JPanel panel;
        final JLabel iconLabel;
        final JLabel textLabel;
        /** One-line preview label; click opens a popup with the full output. */
        final JLabel outputLabel;
        StepStatus status = StepStatus.PENDING;
        /** Full tool output — kept so the popup can display it without truncation. */
        @Nullable
        private String fullOutput;

        StepRow(Step step, PhaseRow parent, Runnable onEdit, Runnable onDelete) {
            this.step = step;
            iconLabel = new JLabel(StepStatus.PENDING.icon());
            textLabel = new JLabel(step.getDescription());
            textLabel.setFont(FontUtils.getSmallFont());
            textLabel.setForeground(JBColor.GRAY);

            outputLabel = new JLabel();
            outputLabel.setFont(FontUtils.getSmallFont().deriveFont(Font.ITALIC));
            outputLabel.setForeground(JBColor.namedColor("Component.infoForeground", JBColor.GRAY));
            outputLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            outputLabel.setVisible(false);
            outputLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (fullOutput != null) showOutputPopup(outputLabel, fullOutput);
                }
            });

            // Edit button — visible in pre-execution state only
            JButton editBtn = new JButton(AllIcons.Actions.Edit);
            editBtn.setPreferredSize(new Dimension(16, 16));
            editBtn.setBorderPainted(false);
            editBtn.setContentAreaFilled(false);
            editBtn.setFocusPainted(false);
            editBtn.setToolTipText("Edit step description");
            editBtn.addActionListener(e -> {
                String newDesc = JOptionPane.showInputDialog(
                        editBtn, "Edit step description:", step.getDescription());
                if (newDesc != null && !newDesc.isBlank() && !newDesc.equals(step.getDescription())) {
                    this.step = step.withDescription(newDesc.trim());
                    textLabel.setText(this.step.getDescription());
                    onEdit.run();
                }
            });

            // Delete button — visible in pre-execution state only
            JButton deleteBtn = new JButton(AllIcons.General.Remove);
            deleteBtn.setPreferredSize(new Dimension(16, 16));
            deleteBtn.setBorderPainted(false);
            deleteBtn.setContentAreaFilled(false);
            deleteBtn.setFocusPainted(false);
            deleteBtn.setToolTipText("Remove this step");
            deleteBtn.addActionListener(e -> {
                parent.removeStepRow(this);
                onDelete.run();
            });

            panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setOpaque(false);

            JPanel mainRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
            mainRow.setOpaque(false);
            mainRow.add(iconLabel);
            mainRow.add(textLabel);
            mainRow.add(editBtn);
            mainRow.add(deleteBtn);
            panel.add(mainRow);
            panel.add(outputLabel);
        }

        void setStatus(StepStatus newStatus) {
            setStatus(newStatus, null);
        }

        void setStatus(StepStatus newStatus, @Nullable String contextText) {
            this.status = newStatus;
            iconLabel.setIcon(newStatus.icon());
            textLabel.setForeground(newStatus == StepStatus.FAILED ? JBColor.RED
                    : newStatus == StepStatus.SUCCESS ? JBColor.namedColor("Label.successForeground", JBColor.GREEN)
                    : JBColor.GRAY);
            // Hide edit/delete buttons once execution starts
            for (Component c : ((JPanel) panel.getComponent(0)).getComponents()) {
                if (c instanceof JButton) c.setVisible(false);
            }
            if (contextText != null && !contextText.isBlank()
                    && (newStatus == StepStatus.SUCCESS || newStatus == StepStatus.FAILED)) {
                this.fullOutput = contextText;
                boolean truncated = contextText.length() > MAX_OUTPUT_DISPLAY_CHARS;
                String preview = truncated
                        ? contextText.substring(0, MAX_OUTPUT_DISPLAY_CHARS).replace('\n', ' ') + "… ▶"
                        : contextText.replace('\n', ' ');
                outputLabel.setForeground(newStatus == StepStatus.FAILED
                        ? JBColor.RED
                        : JBColor.namedColor("Component.infoForeground", JBColor.GRAY));
                outputLabel.setToolTipText(truncated ? "Click to see full output" : null);
                outputLabel.setText("  " + preview);
                outputLabel.setVisible(true);
            }
        }

        /** Opens a scrollable popup showing the full tool output with a copy-to-clipboard button. */
        private static void showOutputPopup(Component anchor, String content) {
            JTextArea textArea = new JTextArea(content);
            textArea.setEditable(false);
            textArea.setFont(FontUtils.getSmallFont());
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setBackground(anchor.getBackground());

            JBScrollPane scrollPane = new JBScrollPane(textArea);

            JButton copyBtn = new JButton("Copy");
            copyBtn.setFont(FontUtils.getSmallFont());
            copyBtn.setFocusPainted(false);
            copyBtn.addActionListener(e -> {
                StringSelection selection = new StringSelection(content);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                copyBtn.setText("Copied");
                copyBtn.setEnabled(false);
            });

            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
            toolbar.setOpaque(false);
            toolbar.add(copyBtn);

            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.add(scrollPane, BorderLayout.CENTER);
            wrapper.add(toolbar, BorderLayout.SOUTH);
            wrapper.setPreferredSize(new Dimension(600, 350));

            JBPopupFactory.getInstance()
                    .createComponentPopupBuilder(wrapper, textArea)
                    .setTitle("Step output")
                    .setMovable(true)
                    .setResizable(true)
                    .setRequestFocus(true)
                    .createPopup()
                    .showUnderneathOf(anchor);
        }

        /**
         * Hides edit/delete buttons immediately. Called at execution start to close the race
         * window between triggerValidation() and the first STEP_STARTED event (U-2).
         */
        void lockEditButtons() {
            JPanel mainRow = (JPanel) panel.getComponent(0);
            for (Component c : mainRow.getComponents()) {
                if (c instanceof JButton) c.setVisible(false);
            }
        }

        /**
         * Attaches [Retry] and [Skip] action buttons to this failed step row.
         * Must be called on the EDT.
         */
        void showRetryButtons(Consumer<StepRetryDecision> onDecision) {
            JPanel mainRow = (JPanel) panel.getComponent(0);

            JButton retryBtn = new JButton("Retry", AllIcons.Actions.Restart);
            retryBtn.setFont(FontUtils.getSmallFont());
            retryBtn.setFocusPainted(false);

            JButton skipBtn = new JButton("Skip");
            skipBtn.setFont(FontUtils.getSmallFont());
            skipBtn.setFocusPainted(false);

            retryBtn.addActionListener(e -> {
                retryBtn.setEnabled(false);
                skipBtn.setEnabled(false);
                // Show spinner again so the user knows a retry is in progress (U-9)
                iconLabel.setIcon(StepStatus.RUNNING.icon());
                textLabel.setForeground(JBColor.GRAY);
                onDecision.accept(StepRetryDecision.RETRY);
            });
            skipBtn.addActionListener(e -> {
                skipBtn.setEnabled(false);
                retryBtn.setEnabled(false);
                // Show "skipped" state visually (U-9)
                iconLabel.setIcon(com.intellij.icons.AllIcons.Actions.Forward);
                textLabel.setForeground(JBColor.GRAY);
                onDecision.accept(StepRetryDecision.SKIP);
            });

            mainRow.add(retryBtn);
            mainRow.add(skipBtn);
            mainRow.revalidate();
            mainRow.repaint();
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
