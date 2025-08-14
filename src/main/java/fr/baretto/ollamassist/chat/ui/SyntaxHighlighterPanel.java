package fr.baretto.ollamassist.chat.ui;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import fr.baretto.ollamassist.component.ComponentCustomizer;
import lombok.extern.slf4j.Slf4j;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.InputStream;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

@Slf4j
public class SyntaxHighlighterPanel extends JPanel {
    private static final String INSERT = "Insert";
    private static final String COPY_TO_CLIPBOARD = "Copy to clipboard";
    private final RSyntaxTextArea codeBlock;
    private final JScrollPane scrollPane;
    private final JPanel parentPanel;
    private final transient Context context;
    private final JLabel languageLabel = new JLabel();


    public SyntaxHighlighterPanel(JPanel parentPanel, Context context) {
        this.parentPanel = parentPanel;
        this.context = context;
        setLayout(new BorderLayout());

        codeBlock = new RSyntaxTextArea();
        codeBlock.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        codeBlock.setCodeFoldingEnabled(false);
        codeBlock.setAutoIndentEnabled(true);
        codeBlock.setEditable(false);
        updateTheme();

        scrollPane = new JBScrollPane(codeBlock, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED);

        JPanel headerPanel = new JPanel(new BorderLayout());

        languageLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        headerPanel.add(languageLabel, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton insertButton = createButton(IconUtils.INSERT, INSERT);
        insertButton.setToolTipText("Insert code");
        JButton copyButton = createButton(IconUtils.COPY, COPY_TO_CLIPBOARD);
        copyButton.setToolTipText("Copy code");
        ComponentCustomizer.applyHoverEffect(insertButton);
        ComponentCustomizer.applyHoverEffect(copyButton);
        buttonPanel.add(insertButton);
        buttonPanel.add(copyButton);
        headerPanel.add(buttonPanel, BorderLayout.EAST);

        insertButton.addActionListener(e -> insertCode());
        copyButton.addActionListener(e -> copyToClipboard());

        add(headerPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void copyToClipboard() {
        String code = codeBlock.getText();
        StringSelection selection = new StringSelection(code);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, null);
    }

    private void insertCode() {
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(context.project());
        Editor editor = fileEditorManager.getSelectedTextEditor();
        if (editor != null) {
            Document document = editor.getDocument();
            CaretModel caretModel = editor.getCaretModel();
            WriteCommandAction.runWriteCommandAction(context.project(), () -> {
                try {
                    document.insertString(caretModel.getOffset(), codeBlock.getText());
                } catch (Exception e) {
                    Logger.getInstance(getClass()).error(e.getMessage());
                }
            });
        }
    }

    private void updateTheme() {
        String themeFile = !JBColor.isBright() ? "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"
                : "/org/fife/ui/rsyntaxtextarea/themes/idea.xml";

        try (InputStream in = getClass().getResourceAsStream(themeFile)) {
            Theme theme = Theme.load(in);
            theme.apply(codeBlock);
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    public void adjustSizeToContent() {
        codeBlock.setPreferredSize(null);
        scrollPane.setMinimumSize(new Dimension(0, 0));
        scrollPane.setPreferredSize(null);

        revalidate();
        repaint();
    }

    public void appendText(String text) {
        codeBlock.append(text);
        codeBlock.setCaretPosition(codeBlock.getDocument().getLength());
        adjustSizeToContent();
        revalidate();
        repaint();
    }

    public void applyStyle(String syntaxtStyle) {
        languageLabel.setText(syntaxtStyle.split("/")[1]);
        codeBlock.setSyntaxEditingStyle(syntaxtStyle);
    }


    private JButton createButton(Icon icon, String toolTipText) {
        JButton newButton = new JButton(icon);
        newButton.setPreferredSize(new Dimension(16, 16));
        newButton.setMargin(JBUI.insets(2));
        newButton.setFocusPainted(false);
        newButton.setBorderPainted(false);
        newButton.setContentAreaFilled(false);
        newButton.setToolTipText(toolTipText);
        return newButton;
    }
}