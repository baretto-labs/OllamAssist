package fr.baretto.ollamassist.chat.ui;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UserMessage extends JPanel {

    private final JPanel mainPanel;

    public UserMessage(String userMessage) {
        setLayout(new BorderLayout());
        setOpaque(false);

        // Modern header with user icon on the right
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(JBUI.Borders.empty(4, 8));
        headerPanel.add(createHeaderLabel(), BorderLayout.EAST);

        // Main content panel with modern bubble design
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(JBColor.namedColor("Component.focusedBorderColor", new JBColor(0xE8F4FF, 0x2D3E50)));
        mainPanel.setBorder(new CompoundBorder(
                JBUI.Borders.customLine(JBColor.namedColor("Component.borderColor", JBColor.border()), 1),
                JBUI.Borders.empty(12, 16)
        ));

        add(headerPanel, BorderLayout.NORTH);
        add(mainPanel, BorderLayout.CENTER);
        setBorder(JBUI.Borders.empty(8, 12));

        parseAndRender(userMessage);
    }

    private void parseAndRender(String message) {
        Pattern pattern = Pattern.compile("```(\\w+)?\\s*\\n([\\s\\S]*?)\\n?```");
        Matcher matcher = pattern.matcher(message);

        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String before = message.substring(lastEnd, matcher.start()).strip();
                if (!before.isEmpty()) {
                    addTextBlock(before);
                }
            }

            String language = matcher.group(1) != null ? matcher.group(1) : "";
            String code = matcher.group(2);
            addCodeBlock(code, language);

            lastEnd = matcher.end();
        }

        if (lastEnd < message.length()) {
            String remaining = message.substring(lastEnd).strip();
            if (!remaining.isEmpty()) {
                addTextBlock(remaining);
            }
        }
    }

    private void addTextBlock(String text) {
        JTextArea textArea = new JTextArea(text);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setOpaque(false);
        textArea.setFont(UIUtil.getLabelFont().deriveFont(13f));
        textArea.setBorder(JBUI.Borders.empty(4, 0));
        mainPanel.add(textArea);
    }

    private void addCodeBlock(String code, String language) {
        SyntaxHighlighterPanel codePanel = new SyntaxHighlighterPanel(null, null); // adapter selon ton usage
        String style = detectSyntaxStyle(language);
        if (!style.isEmpty()) {
            codePanel.applyStyle(style);
        }
        codePanel.appendText(code.strip());
        codePanel.adjustSizeToContent();
        mainPanel.add(codePanel);
    }

    private String detectSyntaxStyle(String language) {
        return switch (language.toLowerCase()) {
            case "java" -> SyntaxConstants.SYNTAX_STYLE_JAVA;
            case "python" -> SyntaxConstants.SYNTAX_STYLE_PYTHON;
            case "js", "javascript" -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
            case "html" -> SyntaxConstants.SYNTAX_STYLE_HTML;
            case "xml" -> SyntaxConstants.SYNTAX_STYLE_XML;
            case "css" -> SyntaxConstants.SYNTAX_STYLE_CSS;
            case "csv" -> SyntaxConstants.SYNTAX_STYLE_CSV;
            case "json" -> SyntaxConstants.SYNTAX_STYLE_JSON;
            case "md", "markdown" -> SyntaxConstants.SYNTAX_STYLE_MARKDOWN;
            case "makefile" -> SyntaxConstants.SYNTAX_STYLE_MAKEFILE;
            default -> "";
        };
    }

    private @NotNull JBLabel createHeaderLabel() {
        JBLabel header = new JBLabel("User", IconUtils.USER_ICON, SwingConstants.RIGHT);
        header.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, 12f));
        header.setForeground(JBColor.namedColor("Label.infoForeground", JBColor.GRAY));
        header.setBorder(JBUI.Borders.empty());
        return header;
    }
}