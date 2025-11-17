package fr.baretto.ollamassist.chat.ui;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.function.Consumer;

public class ApprovalMessage extends JPanel {

    private static final String FILE_PREFIX = "File: ";
    private static final String TRUNCATION_SUFFIX = "\n... (truncated)";

    private final transient Consumer<Boolean> onDecision;
    private final JPanel buttonPanel;

    public ApprovalMessage(String title, String filePath, String content, Consumer<Boolean> onDecision) {
        this.onDecision = onDecision;
        setLayout(new BorderLayout());

        boolean isAutoCreated = title.startsWith("✅");

        if (isAutoCreated) {
            setBackground(new JBColor(new Color(220, 255, 220), new Color(45, 90, 45)));
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new JBColor(Color.GREEN, new Color(50, 150, 50)), 2),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));
        } else {
            setBackground(new JBColor(new Color(255, 250, 205), new Color(70, 60, 30)));
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new JBColor(Color.ORANGE, new Color(200, 150, 50)), 2),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));
        }

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        Icon headerIcon = isAutoCreated ? IconUtils.OLLAMASSIST_ICON : IconUtils.OLLAMASSIST_WARN_ICON;
        JBLabel headerLabel = new JBLabel(title, headerIcon, SwingConstants.LEFT);
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 12f));
        headerPanel.add(headerLabel, BorderLayout.WEST);
        add(headerPanel, BorderLayout.NORTH);

        // Content panel
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);

        // File path
        JLabel pathLabel = new JLabel(FILE_PREFIX + filePath);
        pathLabel.setFont(pathLabel.getFont().deriveFont(Font.BOLD, 11f));
        pathLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 10, 0));
        contentPanel.add(pathLabel);

        // Code preview
        String previewContent = content.length() > 1000
            ? content.substring(0, 1000) + TRUNCATION_SUFFIX
            : content;

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

        JBScrollPane codeScrollPane = new JBScrollPane(codeArea);
        codeScrollPane.setPreferredSize(new Dimension(600, 300));
        codeScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
        contentPanel.add(codeScrollPane);

        add(contentPanel, BorderLayout.CENTER);

        // Buttons
        buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttonPanel.setOpaque(false);

        if (!isAutoCreated) {
            // Only show buttons if not auto-created
            JButton approveButton = new JButton("Approve");
            approveButton.setBackground(new JBColor(new Color(144, 238, 144), new Color(50, 100, 50)));
            approveButton.setForeground(JBColor.BLACK);
            approveButton.setFocusPainted(false);
            approveButton.addActionListener(e -> handleDecision(true));

            JButton rejectButton = new JButton("Reject");
            rejectButton.setBackground(new JBColor(new Color(255, 160, 160), new Color(100, 50, 50)));
            rejectButton.setForeground(JBColor.BLACK);
            rejectButton.setFocusPainted(false);
            rejectButton.addActionListener(e -> handleDecision(false));

            buttonPanel.add(approveButton);
            buttonPanel.add(rejectButton);

            add(buttonPanel, BorderLayout.SOUTH);
        }
    }

    private void handleDecision(boolean approved) {
        // Disable buttons
        for (Component comp : buttonPanel.getComponents()) {
            comp.setEnabled(false);
        }

        // Update UI to show decision
        if (approved) {
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new JBColor(Color.GREEN, new Color(50, 150, 50)), 2),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));
        } else {
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new JBColor(Color.RED, new Color(150, 50, 50)), 2),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));
        }

        // Notify decision
        onDecision.accept(approved);
    }

    private String detectSyntaxStyle(String filePath) {
        String extension = filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase();
        return switch (extension) {
            case "java" -> SyntaxConstants.SYNTAX_STYLE_JAVA;
            case "py" -> SyntaxConstants.SYNTAX_STYLE_PYTHON;
            case "js" -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
            case "ts" -> SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT;
            case "html" -> SyntaxConstants.SYNTAX_STYLE_HTML;
            case "xml" -> SyntaxConstants.SYNTAX_STYLE_XML;
            case "css" -> SyntaxConstants.SYNTAX_STYLE_CSS;
            case "json" -> SyntaxConstants.SYNTAX_STYLE_JSON;
            case "md" -> SyntaxConstants.SYNTAX_STYLE_MARKDOWN;
            case "kt" -> SyntaxConstants.SYNTAX_STYLE_KOTLIN;
            case "gradle" -> SyntaxConstants.SYNTAX_STYLE_GROOVY;
            default -> SyntaxConstants.SYNTAX_STYLE_NONE;
        };
    }
}
