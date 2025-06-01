package fr.baretto.ollamassist.chat.ui;

import com.intellij.ui.components.JBLabel;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UserMessage extends JPanel {

    private final JPanel mainPanel;

    public UserMessage(String userMessage) {
        setLayout(new BorderLayout());
        setBackground(UIManager.getColor("Button.background").brighter());

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.add(createHeaderLabel(), BorderLayout.EAST);

        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setOpaque(false);

        add(headerPanel, BorderLayout.NORTH);
        add(mainPanel, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

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
        textArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
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
        JBLabel header = new JBLabel("User", IconUtils.USER_ICON, SwingConstants.LEFT);
        header.setFont(header.getFont().deriveFont(10f));
        header.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        return header;
    }
}