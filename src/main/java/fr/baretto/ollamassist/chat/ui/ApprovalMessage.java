package fr.baretto.ollamassist.chat.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import fr.baretto.ollamassist.events.FileApprovalNotifier;
import fr.baretto.ollamassist.utils.FontUtils;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.function.Consumer;

public class ApprovalMessage extends JPanel {

    private static final String FILE_PREFIX = "File: ";

    private final transient Consumer<FileApprovalNotifier.ApprovalDecision> onDecision;
    private final JPanel buttonPanel;
    private boolean decided = false;

    public ApprovalMessage(String title, String filePath, String content,
                           Consumer<FileApprovalNotifier.ApprovalDecision> onDecision) {
        this.onDecision = onDecision;
        setLayout(new BorderLayout());

        setBackground(new JBColor(new Color(255, 250, 205), new Color(70, 60, 30)));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new JBColor(Color.ORANGE, new Color(200, 150, 50)), 2),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JBLabel headerLabel = new JBLabel(title, IconUtils.OLLAMASSIST_WARN_ICON, SwingConstants.LEFT);
        headerLabel.setFont(FontUtils.getNormalFont());
        headerPanel.add(headerLabel, BorderLayout.WEST);
        add(headerPanel, BorderLayout.NORTH);

        // Content panel
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);

        JLabel pathLabel = new JLabel(FILE_PREFIX + filePath);
        pathLabel.setFont(FontUtils.getSmallFont());
        pathLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 10, 0));
        contentPanel.add(pathLabel);

        String previewContent = content;

        JComponent contentView;
        if (isDiffContent(previewContent)) {
            contentView = buildDiffPane(previewContent);
        } else {
            RSyntaxTextArea codeArea = new RSyntaxTextArea(previewContent);
            codeArea.setSyntaxEditingStyle(detectSyntaxStyle(filePath));
            codeArea.setCodeFoldingEnabled(true);
            codeArea.setEditable(false);
            codeArea.setRows(Math.min(20, previewContent.split("\n").length));
            try {
                Theme theme = Theme.load(getClass().getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/dark.xml"));
                theme.apply(codeArea);
            } catch (IOException e) {
                // Fallback to default theme
            }
            contentView = codeArea;
        }

        JBScrollPane codeScrollPane = new JBScrollPane(contentView);
        codeScrollPane.setPreferredSize(new Dimension(600, 300));
        codeScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
        contentPanel.add(codeScrollPane);

        add(contentPanel, BorderLayout.CENTER);

        // Buttons
        buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttonPanel.setOpaque(false);

        JButton approveButton = new JButton("Approve");
        approveButton.setBackground(new JBColor(new Color(144, 238, 144), new Color(50, 100, 50)));
        approveButton.setForeground(JBColor.BLACK);
        approveButton.setFocusPainted(false);
        approveButton.setToolTipText("Approve (Enter)");
        approveButton.addActionListener(e -> handleApprove());

        JButton rejectButton = new JButton("Reject");
        rejectButton.setBackground(new JBColor(new Color(255, 160, 160), new Color(100, 50, 50)));
        rejectButton.setForeground(JBColor.BLACK);
        rejectButton.setFocusPainted(false);
        rejectButton.setToolTipText("Reject (Esc)");
        rejectButton.addActionListener(e -> showRejectReasonPanel());

        buttonPanel.add(approveButton);
        buttonPanel.add(rejectButton);
        add(buttonPanel, BorderLayout.SOUTH);

        // U-7: keyboard shortcuts — Enter approves, Escape opens reject panel
        InputMap inputMap = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actionMap = getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "approve");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "reject");
        actionMap.put("approve", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { handleApprove(); }
        });
        actionMap.put("reject", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { showRejectReasonPanel(); }
        });

        // Auto-focus the Approve button so Enter/Space work immediately
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                SwingUtilities.invokeLater(approveButton::requestFocusInWindow);
            }
        });
    }

    private void handleApprove() {
        if (decided) return;
        decided = true;
        lockButtons();
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new JBColor(Color.GREEN, new Color(50, 150, 50)), 2),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        onDecision.accept(FileApprovalNotifier.ApprovalDecision.allow());
    }

    /**
     * Replaces the Approve/Reject buttons with an inline reason field + icon confirm button.
     * The reason is fed back to the LLM as part of the tool error observation so it can adapt.
     */
    private void showRejectReasonPanel() {
        if (decided) return;
        decided = true;

        // Disable Enter-to-approve — from now on Enter in the text field confirms rejection
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "none");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "none");

        lockButtons();

        JPanel rejectPanel = new JPanel(new BorderLayout(6, 0));
        rejectPanel.setOpaque(false);

        JTextField reasonField = new JTextField();
        reasonField.setToolTipText("Reason for rejection (optional) — shown to the AI");
        reasonField.setFont(FontUtils.getSmallFont());

        JButton confirmButton = new JButton(AllIcons.Actions.Commit);
        confirmButton.setToolTipText("Confirm rejection");
        confirmButton.setBorderPainted(false);
        confirmButton.setContentAreaFilled(false);
        confirmButton.setFocusPainted(false);
        confirmButton.setOpaque(false);
        confirmButton.addActionListener(e -> handleReject(reasonField, confirmButton));

        reasonField.addActionListener(e -> handleReject(reasonField, confirmButton));

        rejectPanel.add(reasonField, BorderLayout.CENTER);
        rejectPanel.add(confirmButton, BorderLayout.EAST);

        buttonPanel.removeAll();
        buttonPanel.setLayout(new BorderLayout());
        buttonPanel.add(rejectPanel, BorderLayout.CENTER);
        buttonPanel.revalidate();
        buttonPanel.repaint();

        reasonField.requestFocusInWindow();
    }

    private void handleReject(JTextField reasonField, JButton confirmButton) {
        reasonField.setEditable(false);
        confirmButton.setEnabled(false);

        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new JBColor(Color.RED, new Color(150, 50, 50)), 2),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        onDecision.accept(FileApprovalNotifier.ApprovalDecision.deny(reasonField.getText()));
    }

    private void lockButtons() {
        for (Component comp : buttonPanel.getComponents()) {
            comp.setEnabled(false);
        }
    }

    private static JTextPane buildDiffPane(String diffText) {
        JTextPane pane = new JTextPane();
        pane.setEditable(false);
        pane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, FontUtils.getSmallFont().getSize()));

        StyledDocument doc = pane.getStyledDocument();

        SimpleAttributeSet removed = new SimpleAttributeSet();
        StyleConstants.setForeground(removed, JBColor.namedColor("Component.errorFocusColor", new Color(200, 60, 60)));
        StyleConstants.setBackground(removed, JBColor.namedColor("FileColor.Red", new Color(255, 235, 235)));

        SimpleAttributeSet added = new SimpleAttributeSet();
        StyleConstants.setForeground(added, JBColor.namedColor("Label.successForeground", new Color(30, 130, 30)));
        StyleConstants.setBackground(added, JBColor.namedColor("FileColor.Green", new Color(235, 255, 235)));

        SimpleAttributeSet meta = new SimpleAttributeSet();
        StyleConstants.setForeground(meta, JBColor.GRAY);
        StyleConstants.setBold(meta, true);

        SimpleAttributeSet normal = new SimpleAttributeSet();
        StyleConstants.setForeground(normal, JBColor.namedColor("Label.foreground", JBColor.BLACK));

        for (String line : diffText.split("\n", -1)) {
            AttributeSet style;
            if (line.startsWith("- ") || line.startsWith("-\t")) style = removed;
            else if (line.startsWith("+ ") || line.startsWith("+\t")) style = added;
            else if (line.startsWith("@@") || line.startsWith("---") || line.startsWith("+++")
                    || line.startsWith("##") || line.startsWith("!!") || line.startsWith("\\")) style = meta;
            else style = normal;
            try {
                doc.insertString(doc.getLength(), line + "\n", style);
            } catch (BadLocationException ignored) {
                // ignored — doc.getLength() is always a valid offset
            }
        }
        return pane;
    }

    static boolean isDiffContent(String content) {
        return content.contains("@@ search → replace @@")
                || content.contains("--- BEFORE:") || content.contains("+++ AFTER:")
                || content.contains("+++ APPENDING:");
    }

    private String detectSyntaxStyle(String filePath) {
        int dot = filePath.lastIndexOf('.');
        if (dot < 0) return SyntaxConstants.SYNTAX_STYLE_NONE;
        String extension = filePath.substring(dot + 1).toLowerCase();
        return switch (extension) {
            case "java"   -> SyntaxConstants.SYNTAX_STYLE_JAVA;
            case "py"     -> SyntaxConstants.SYNTAX_STYLE_PYTHON;
            case "js"     -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
            case "ts"     -> SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT;
            case "html"   -> SyntaxConstants.SYNTAX_STYLE_HTML;
            case "xml"    -> SyntaxConstants.SYNTAX_STYLE_XML;
            case "css"    -> SyntaxConstants.SYNTAX_STYLE_CSS;
            case "json"   -> SyntaxConstants.SYNTAX_STYLE_JSON;
            case "md"     -> SyntaxConstants.SYNTAX_STYLE_MARKDOWN;
            case "kt"     -> SyntaxConstants.SYNTAX_STYLE_KOTLIN;
            case "gradle" -> SyntaxConstants.SYNTAX_STYLE_GROOVY;
            default       -> SyntaxConstants.SYNTAX_STYLE_NONE;
        };
    }
}
