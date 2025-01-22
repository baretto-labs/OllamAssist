package fr.baretto.ollamassist.chat;

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import javax.swing.*;
import java.awt.*;

public class OllamaMessage extends JPanel {
    private final Context context;
    private final JPanel mainPanel;
    private boolean inCodeBlock = false;
    private SyntaxHighlighterPanel latestCodeBlock;
    private JTextArea currentTextArea;
    private boolean isLanguageNotDetected = true;

    public OllamaMessage(Context context) {
        setLayout(new BorderLayout());
        this.context = context;
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.add(createHeaderLabel(), BorderLayout.WEST);
        //headerPanel.add(createDeleteButton(), BorderLayout.EAST);

        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setOpaque(false);

        add(headerPanel, BorderLayout.NORTH);
        JScrollPane scrollPane = new JBScrollPane(mainPanel);
        add(scrollPane, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        startNewTextArea();
    }

    public void append(String token) {
        SwingUtilities.invokeLater(() -> {
            String[] parts = token.split("``", -1);

            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                part = part.replace("`", "");
                if (inCodeBlock) {
                    if (isLanguageNotDetected) {
                        String syntaxtStyle = detectSyntaxStyle(token);
                        if (!syntaxtStyle.isEmpty()) {
                            latestCodeBlock.applyStyle(syntaxtStyle);
                            isLanguageNotDetected = false;
                            break;
                        }
                    }
                    latestCodeBlock.appendText(part);
                    latestCodeBlock.adjustSizeToContent();

                    if (i < parts.length - 1) {
                        endCodeBlock();
                    }
                } else {
                    String cleanedPart = part.replace("`", "");

                    currentTextArea.append(cleanedPart);
                    currentTextArea.setCaretPosition(currentTextArea.getDocument().getLength());

                    if (i < parts.length - 1) {
                        startCodeBlock();
                    }
                }
            }
            revalidate();
            repaint();
        });
    }

    private String detectSyntaxStyle(String language) {
        return switch (language.toLowerCase()) {
            case "java" -> SyntaxConstants.SYNTAX_STYLE_JAVA;
            case "python" -> SyntaxConstants.SYNTAX_STYLE_PYTHON;
            case "javascript" -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
            case "html" -> SyntaxConstants.SYNTAX_STYLE_HTML;
            case "xml" -> SyntaxConstants.SYNTAX_STYLE_XML;
            case "css" -> SyntaxConstants.SYNTAX_STYLE_CSS;
            case "csv" -> SyntaxConstants.SYNTAX_STYLE_CSV;
            case "json" -> SyntaxConstants.SYNTAX_STYLE_JSON;
            case "md" -> SyntaxConstants.SYNTAX_STYLE_MARKDOWN;
            case "makefile" -> SyntaxConstants.SYNTAX_STYLE_MAKEFILE;
            default -> "";
        };
    }

    private void startNewTextArea() {
        currentTextArea = new JTextArea();
        currentTextArea.setLineWrap(true);
        currentTextArea.setWrapStyleWord(true);
        currentTextArea.setEditable(false);
        currentTextArea.setOpaque(false);
        currentTextArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        mainPanel.add(currentTextArea);
    }

    private void startCodeBlock() {
        if (!inCodeBlock) {
            latestCodeBlock = new SyntaxHighlighterPanel(this, context);
            mainPanel.add(latestCodeBlock);
            inCodeBlock = true;
        }
    }

    private void endCodeBlock() {
        inCodeBlock = false;
        isLanguageNotDetected = false;
        latestCodeBlock = null;
        startNewTextArea();
    }

    private JLabel createHeaderLabel() {
        JBLabel header = new JBLabel("OllamaAsist", ImageUtil.OLLAMASSIST_ICON, SwingConstants.RIGHT);
        header.setFont(header.getFont().deriveFont(10f));
        header.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));
        return header;
    }
}