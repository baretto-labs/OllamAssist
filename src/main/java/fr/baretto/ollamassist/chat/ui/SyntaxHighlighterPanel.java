package fr.baretto.ollamassist.chat.ui;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.ui.JBColor;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.InputStream;

public class SyntaxHighlighterPanel extends JPanel {
    private final RSyntaxTextArea codeBlock;
    private final JPanel parentPanel;
    private final Context context;
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

        JPanel headerPanel = new JPanel(new BorderLayout());

        languageLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        headerPanel.add(languageLabel, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton insertButton = new JButton("Insert");
        JButton copyButton = new JButton("Copy");

        buttonPanel.add(insertButton);
        buttonPanel.add(copyButton);
        headerPanel.add(buttonPanel, BorderLayout.EAST);

        insertButton.addActionListener(e -> insertCode());

        copyButton.addActionListener(e -> copyToClipboard());

        add(headerPanel, BorderLayout.NORTH);
        add(codeBlock, BorderLayout.CENTER);
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
            e.printStackTrace();
        }
    }

    public void adjustSizeToContent() {
        FontMetrics fontMetrics = codeBlock.getFontMetrics(codeBlock.getFont());
        String[] lines = codeBlock.getText().split("\n");
        int maxWidth = (int) (parentPanel.getWidth() * 0.9);
        int lineHeight = fontMetrics.getHeight();
        int preferredHeight = lineHeight * lines.length + 10;

        codeBlock.setPreferredSize(new Dimension(maxWidth, preferredHeight));
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
}