package fr.baretto.ollamassist.core.agent.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import fr.baretto.ollamassist.core.agent.task.Task;
import lombok.Builder;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Card component for displaying action proposals with validation buttons
 * Modern UI component for agent mode with user validation
 */
@Getter
public class ActionProposalCard extends JPanel {

    private final Project project;
    private final ProposalData proposalData;
    private final List<Task> tasks;
    private final ActionValidator actionValidator;
    private final Object actionLock = new Object();
    // UI Components
    private JBLabel titleLabel;
    private JBTextArea descriptionArea;
    private JPanel previewPanel;
    private JButton approveButton;
    private JButton rejectButton;
    private JButton modifyButton;
    // State management
    private volatile boolean actionInProgress = false;
    // Actions
    private Consumer<ProposalData> onApprove;
    private Consumer<ProposalData> onReject;
    private Consumer<ProposalData> onModify;

    public ActionProposalCard(Project project, ProposalData proposalData) {
        super(new BorderLayout(8, 8));
        this.project = project;
        this.proposalData = proposalData;
        this.tasks = List.of(); // Legacy compatibility
        this.actionValidator = null;

        initializeComponents();
        layoutComponents();
        setupEventHandlers();
        populateData();
    }

    // Constructeur pour l'intégration TDD
    public ActionProposalCard(List<Task> tasks, ActionValidator actionValidator) {
        super(new BorderLayout(8, 8));
        this.project = null;
        this.proposalData = null;
        this.tasks = tasks;
        this.actionValidator = actionValidator;

        initializeComponents();
        layoutComponents();
        setupEventHandlers();
        populateDataFromTasks();
    }

    private void populateDataFromTasks() {
        if (tasks != null && !tasks.isEmpty()) {
            Task firstTask = tasks.get(0);
            titleLabel.setText("Action Proposal: " + firstTask.getType());
            descriptionArea.setText(firstTask.getDescription());
        }
    }

    private void initializeComponents() {
        // Couleur de fond différente selon le thème pour se démarquer
        Color proposalBackground = JBColor.namedColor("ActionProposal.background",
                new JBColor(
                        UIUtil.getPanelBackground().darker(),    // Light theme - plus foncé
                        UIUtil.getPanelBackground().brighter()   // Dark theme - plus clair
                ));

        setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.ORANGE, 2), // Bordure orange pour attirer l'attention
                JBUI.Borders.empty(12)
        ));
        setBackground(proposalBackground);
        setOpaque(true); // Important pour que la couleur de fond soit visible
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));

        // Title with action type icon
        titleLabel = new JBLabel();
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setIcon(getActionIcon(proposalData != null ? proposalData.getActionType() : ActionType.OTHER));

        // Description area
        descriptionArea = new JBTextArea();
        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setOpaque(false);
        descriptionArea.setBorder(JBUI.Borders.empty());
        descriptionArea.setFont(UIUtil.getFont(UIUtil.FontSize.NORMAL, null));

        // Preview panel for code/file changes
        previewPanel = new JPanel(new BorderLayout());
        previewPanel.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border()),
                JBUI.Borders.empty(8)
        ));
        previewPanel.setBackground(UIUtil.getTextFieldBackground());

        // Action buttons
        approveButton = createActionButton("Apply", AllIcons.Actions.Commit, JBColor.GREEN);
        rejectButton = createActionButton("Reject", AllIcons.Actions.Cancel, JBColor.RED);
        modifyButton = createActionButton("Modify", AllIcons.Actions.Edit, JBColor.BLUE);
    }

    private void layoutComponents() {
        // Header panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        // Status indicator (only if proposalData exists)
        if (proposalData != null) {
            JBLabel statusLabel = new JBLabel(proposalData.getStatus().getDisplayName());
            statusLabel.setForeground(getStatusColor(proposalData.getStatus()));
            statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11f));
            headerPanel.add(statusLabel, BorderLayout.EAST);
        }

        // Description panel
        JPanel descriptionPanel = new JPanel(new BorderLayout());
        descriptionPanel.setOpaque(false);
        descriptionPanel.setBorder(JBUI.Borders.empty(8, 0));
        descriptionPanel.add(descriptionArea, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = createButtonPanel();

        // Main layout
        add(headerPanel, BorderLayout.NORTH);
        add(descriptionPanel, BorderLayout.CENTER);

        // Only show preview if there's content
        if (proposalData != null && proposalData.getPreviewContent() != null && !proposalData.getPreviewContent().trim().isEmpty()) {
            populatePreview();
            add(previewPanel, BorderLayout.CENTER);
            add(descriptionPanel, BorderLayout.NORTH);
        }

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setBorder(JBUI.Borders.emptyTop(8));

        // Show buttons based on current status or default for TDD mode
        if (proposalData != null) {
            switch (proposalData.getStatus()) {
                case PENDING_APPROVAL -> {
                    buttonPanel.add(rejectButton);
                    buttonPanel.add(modifyButton);
                    buttonPanel.add(approveButton);
                }
                case APPROVED -> {
                    JBLabel appliedLabel = new JBLabel("Applied", AllIcons.General.InspectionsOK, SwingConstants.LEFT);
                    appliedLabel.setForeground(JBColor.GREEN);
                    buttonPanel.add(appliedLabel);
                }
                case REJECTED -> {
                    JBLabel rejectedLabel = new JBLabel("Rejected", AllIcons.General.Error, SwingConstants.LEFT);
                    rejectedLabel.setForeground(JBColor.RED);
                    buttonPanel.add(rejectedLabel);
                }
            }
        } else {
            buttonPanel.add(rejectButton);
            buttonPanel.add(modifyButton);
            buttonPanel.add(approveButton);
        }

        return buttonPanel;
    }

    private JButton createActionButton(String text, Icon icon, Color accentColor) {
        JButton button = new JButton(text, icon);
        button.setFocusPainted(false);
        button.setBorder(JBUI.Borders.empty(6, 12));

        // Modern button styling
        if (text.equals("Apply")) {
            button.setBackground(accentColor.darker());
            button.setForeground(JBColor.WHITE);
        } else {
            button.setBackground(UIUtil.getPanelBackground());
            button.setBorder(JBUI.Borders.compound(
                    JBUI.Borders.customLine(accentColor),
                    JBUI.Borders.empty(5, 11)
            ));
        }

        return button;
    }

    private void setupEventHandlers() {
        approveButton.addActionListener(e -> {
            executeActionSafely(() -> {
                if (actionValidator != null && tasks != null) {
                    // Mode TDD avec ActionValidator
                    actionValidator.approveActions(tasks);
                    refreshButtonPanel();
                } else if (onApprove != null) {
                    // Mode legacy avec ProposalData
                    proposalData.setStatus(ProposalStatus.APPROVED);
                    onApprove.accept(proposalData);
                    refreshButtonPanel();
                }
            });
        });

        rejectButton.addActionListener(e -> {
            executeActionSafely(() -> {
                if (actionValidator != null && tasks != null) {
                    // Mode TDD avec ActionValidator
                    actionValidator.rejectActions(tasks);
                    refreshButtonPanel();
                } else if (onReject != null) {
                    // Mode legacy avec ProposalData
                    proposalData.setStatus(ProposalStatus.REJECTED);
                    onReject.accept(proposalData);
                    refreshButtonPanel();
                }
            });
        });

        modifyButton.addActionListener(e -> {
            executeActionSafely(() -> {
                if (actionValidator != null && tasks != null) {
                    // Mode TDD avec ActionValidator
                    actionValidator.modifyActions(tasks);
                } else if (onModify != null) {
                    // Mode legacy avec ProposalData
                    onModify.accept(proposalData);
                }
            });
        });
    }

    private void populateData() {
        if (proposalData != null) {
            titleLabel.setText(proposalData.getTitle());
            descriptionArea.setText(proposalData.getDescription());
        }
    }

    private void populatePreview() {
        previewPanel.removeAll();

        if (proposalData.getPreviewContent() != null) {
            JBTextArea previewArea = new JBTextArea(proposalData.getPreviewContent());
            previewArea.setEditable(false);
            previewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            previewArea.setBackground(UIUtil.getTextFieldBackground());
            previewArea.setBorder(JBUI.Borders.empty(8));

            JScrollPane scrollPane = new JScrollPane(previewArea);
            scrollPane.setPreferredSize(new Dimension(-1, Math.min(200, previewArea.getPreferredSize().height)));
            scrollPane.setBorder(null);

            JBLabel previewLabel = new JBLabel("Preview:");
            previewLabel.setFont(previewLabel.getFont().deriveFont(Font.BOLD, 11f));
            previewLabel.setBorder(JBUI.Borders.emptyBottom(4));

            previewPanel.add(previewLabel, BorderLayout.NORTH);
            previewPanel.add(scrollPane, BorderLayout.CENTER);
        }
    }

    private void refreshButtonPanel() {
        // Remove and recreate button panel to update based on new status
        Component[] components = getComponents();
        for (Component comp : components) {
            if (comp instanceof JPanel panel && isButtonPanel(panel)) {
                remove(comp);
                break;
            }
        }
        add(createButtonPanel(), BorderLayout.SOUTH);
        revalidate();
        repaint();
    }

    private boolean isButtonPanel(JPanel panel) {
        // Simple check to identify button panel
        return panel.getLayout() instanceof FlowLayout;
    }

    private Icon getActionIcon(ActionType actionType) {
        return switch (actionType) {
            case CODE_MODIFICATION -> AllIcons.Actions.Edit;
            case FILE_CREATION -> AllIcons.Actions.New;
            case FILE_DELETION -> AllIcons.Actions.Cancel;
            case BUILD_EXECUTION -> AllIcons.Actions.Compile;
            case GIT_OPERATION -> AllIcons.Vcs.CommitNode;
            case REFACTORING -> AllIcons.Actions.RefactoringBulb;
            default -> AllIcons.General.Information;
        };
    }

    private Color getStatusColor(ProposalStatus status) {
        return switch (status) {
            case PENDING_APPROVAL -> JBColor.ORANGE;
            case APPROVED -> JBColor.GREEN;
            case REJECTED -> JBColor.RED;
        };
    }

    // Builder methods for event handlers
    public ActionProposalCard onApprove(Consumer<ProposalData> handler) {
        this.onApprove = handler;
        return this;
    }

    public ActionProposalCard onReject(Consumer<ProposalData> handler) {
        this.onReject = handler;
        return this;
    }

    public ActionProposalCard onModify(Consumer<ProposalData> handler) {
        this.onModify = handler;
        return this;
    }

    /**
     * Executes an action safely with multiple click prevention
     */
    private void executeActionSafely(Runnable action) {
        synchronized (actionLock) {
            if (actionInProgress) {
                return; // Already executed, ignore this click permanently
            }
            actionInProgress = true;
            disableAllButtons();
        }

        try {
            action.run();
        } catch (Exception e) {
            // Log error but keep buttons disabled to prevent retry
            throw e;
        }
        // DO NOT call finishAction() - buttons should stay disabled after action execution
    }

    /**
     * Immediately disable all action buttons
     */
    private void disableAllButtons() {
        approveButton.setEnabled(false);
        rejectButton.setEnabled(false);
        modifyButton.setEnabled(false);
    }

    /**
     * This method should only be called in exceptional cases where buttons need to be re-enabled
     * After normal action execution, buttons should remain permanently disabled
     */
    private void finishAction() {
        synchronized (actionLock) {
            actionInProgress = false;
        }

        // Only re-enable buttons if still in pending state
        boolean shouldEnable = (proposalData == null ||
                proposalData.getStatus() == ProposalStatus.PENDING_APPROVAL);

        if (shouldEnable) {
            SwingUtilities.invokeLater(() -> {
                approveButton.setEnabled(true);
                rejectButton.setEnabled(true);
                modifyButton.setEnabled(true);
            });
        }
    }

    // Getters pour les tests TDD
    public List<Task> getTasks() {
        return tasks;
    }

    public JButton getApproveButton() {
        return approveButton;
    }

    public JButton getRejectButton() {
        return rejectButton;
    }

    public JButton getModifyButton() {
        return modifyButton;
    }

    /**
     * Types of actions that can be proposed
     */
    public enum ActionType {
        CODE_MODIFICATION("Code Modification"),
        FILE_CREATION("File Creation"),
        FILE_DELETION("File Deletion"),
        BUILD_EXECUTION("Build Execution"),
        GIT_OPERATION("Git Operation"),
        REFACTORING("Refactoring"),
        OTHER("Other");

        private final String displayName;

        ActionType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Status of action proposal
     */
    public enum ProposalStatus {
        PENDING_APPROVAL("Pending Approval"),
        APPROVED("Approved"),
        REJECTED("Rejected");

        private final String displayName;

        ProposalStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Interface for validating actions proposed by the agent
     */
    public interface ActionValidator {
        void approveActions(List<Task> tasks);

        void rejectActions(List<Task> tasks);

        void modifyActions(List<Task> tasks);
    }

    /**
     * Data class for action proposal
     */
    @Builder
    @Getter
    public static class ProposalData {
        private final String id;
        private final String title;
        private final String description;
        private final ActionType actionType;
        private final String previewContent;
        private final Task relatedTask;
        @lombok.Builder.Default
        private ProposalStatus status = ProposalStatus.PENDING_APPROVAL;

        public void setStatus(ProposalStatus status) {
            this.status = status;
        }
    }
}