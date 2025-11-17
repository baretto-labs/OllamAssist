package fr.baretto.ollamassist.chat.ui;

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class OllamaMessage extends JPanel {

    private final transient Context context;
    private final JPanel mainPanel;
    private final JLabel currentHeaderPanel;
    private boolean inCodeBlock = false;
    private SyntaxHighlighterPanel latestCodeBlock;
    private JTextArea currentTextArea;
    private boolean isLanguageNotDetected = true;

    public OllamaMessage(Context context) {
        setLayout(new BorderLayout());
        this.context = context;
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        currentHeaderPanel = createHeaderLabel();
        headerPanel.add(currentHeaderPanel, BorderLayout.WEST);

        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setOpaque(false);

        add(headerPanel, BorderLayout.NORTH);
        JScrollPane scrollPane = new JBScrollPane(mainPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(scrollPane, BorderLayout.NORTH);
        add(panel, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        startNewTextArea();
        mainPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                mainPanel.revalidate();
                mainPanel.repaint();
            }
        });
    }

    public void append(String token) {
        SwingUtilities.invokeLater(() -> {
            String[] parts = token.split("``", -1);
            for (int i = 0; i < parts.length; i++) {
                processPart(parts[i], i < parts.length - 1);
            }
            revalidate();
            repaint();
        });
    }

    private void processPart(String part, boolean hasNextPart) {
        part = part.replace("`", "");

        if (inCodeBlock) {
            handleCodeBlockPart(part, hasNextPart);
        } else {
            handleTextPart(part, hasNextPart);
        }
    }

    private void handleCodeBlockPart(String part, boolean hasNextPart) {
        if (isLanguageNotDetected) {
            String syntaxStyle = detectSyntaxStyle(part);
            if (!syntaxStyle.isEmpty()) {
                latestCodeBlock.applyStyle(syntaxStyle);
                isLanguageNotDetected = false;
            }
        }

        latestCodeBlock.appendText(part);
        latestCodeBlock.adjustSizeToContent();

        if (hasNextPart) {
            endCodeBlock();
        }
    }

    private void handleTextPart(String part, boolean hasNextPart) {
        currentTextArea.append(part);
        currentTextArea.setCaretPosition(currentTextArea.getDocument().getLength());

        if (hasNextPart) {
            startCodeBlock();
        }
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
        JBLabel header = new JBLabel("OllamAssist", IconUtils.OLLAMASSIST_THINKING_ICON, SwingConstants.RIGHT);
        header.setFont(header.getFont().deriveFont(10f));
        header.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));
        return header;
    }

    public void finalizeResponse(ChatResponse chatResponse) {
        if (currentHeaderPanel != null) {
            if (chatResponse.finishReason() == FinishReason.OTHER) {
                currentHeaderPanel.setIcon(IconUtils.OLLAMASSIST_ERROR_ICON);
                currentTextArea.append("...");
                endCodeBlock();
                append("There was an error processing your request. Please try again.");
                if (chatResponse.aiMessage() != null) {
                    currentHeaderPanel.setToolTipText(chatResponse.aiMessage().text());
                }
            } else {
                currentHeaderPanel.setIcon(IconUtils.OLLAMASSIST_ICON);
                currentHeaderPanel.setToolTipText("Input Tokens: %s<br/>Output Tokens: %s".formatted(chatResponse.tokenUsage().inputTokenCount(), chatResponse.tokenUsage().outputTokenCount()));
            }
        }
    }

    public void cancel() {
        if (currentHeaderPanel != null) {
            currentHeaderPanel.setIcon(IconUtils.OLLAMASSIST_WARN_ICON);
            currentTextArea.append("...");
            endCodeBlock();
            append("The request was interrupted.");
        }
    }

    public void stopSilently() {
        if (currentHeaderPanel != null) {
            currentHeaderPanel.setIcon(IconUtils.OLLAMASSIST_ICON);
            endCodeBlock();
        }
    }
}