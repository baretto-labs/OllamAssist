package fr.baretto.ollamassist.chat.ui;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
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
        setOpaque(false);
        this.context = context;

        // Modern header with icon and label
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(JBUI.Borders.empty(4, 8, 4, 8));
        currentHeaderPanel = createHeaderLabel();
        headerPanel.add(currentHeaderPanel, BorderLayout.WEST);

        // Main content panel with modern bubble design
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(JBColor.namedColor("EditorPane.background", new JBColor(0xF7F8FA, 0x2B2D30)));
        mainPanel.setBorder(new CompoundBorder(
                JBUI.Borders.customLine(JBColor.border(), 1),
                JBUI.Borders.empty(12, 16)
        ));

        add(headerPanel, BorderLayout.NORTH);
        JScrollPane scrollPane = new JBScrollPane(mainPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(JBUI.Borders.empty());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setOpaque(false);
        panel.add(scrollPane, BorderLayout.NORTH);
        add(panel, BorderLayout.CENTER);
        setBorder(JBUI.Borders.empty(8, 12, 8, 12));

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
        currentTextArea.setFont(UIUtil.getLabelFont().deriveFont(13f));
        currentTextArea.setBorder(JBUI.Borders.empty(4, 0));
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
        JBLabel header = new JBLabel("OllamAssist", IconUtils.OLLAMASSIST_THINKING_ICON, SwingConstants.LEFT);
        header.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, 12f));
        header.setForeground(JBColor.namedColor("Label.infoForeground", JBColor.GRAY));
        header.setBorder(JBUI.Borders.empty());
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