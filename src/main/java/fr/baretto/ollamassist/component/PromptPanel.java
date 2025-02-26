package fr.baretto.ollamassist.component;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Consumer;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import fr.baretto.ollamassist.chat.ui.IconUtils;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

@Getter
public class PromptPanel extends JPanel implements Disposable {

    private EditorTextField editorTextField;
    private JButton sendButton;
    private ActionListener listener;


    public PromptPanel() {
        super(new BorderLayout());
        setupUI();
    }

    private void setupUI() {
        setBackground(UIUtil.getPanelBackground());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)
        ));

        editorTextField = new ScrollableEditorTextField();
        editorTextField.setFocusable(true);
        editorTextField.setOneLineMode(false);
        editorTextField.setBackground(UIUtil.getTextFieldBackground());
        editorTextField.setForeground(UIUtil.getTextFieldForeground());
        editorTextField.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        sendButton = createSubmitButton();

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonPanel.setAlignmentX(RIGHT_ALIGNMENT);
        buttonPanel.setOpaque(false);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0)); // Padding droit ajouté ici
        buttonPanel.add(sendButton);


        JPanel container = new JPanel(new BorderLayout());
        container.add(editorTextField, BorderLayout.CENTER);
        container.add(buttonPanel, BorderLayout.SOUTH);

        add(container, BorderLayout.CENTER);
    }


    private JButton createSubmitButton() {
        JButton submit = new JButton(IconUtils.SUBMIT);
        submit.setBackground(UIUtil.getPanelBackground());
        submit.setForeground(UIUtil.getLabelForeground());
        submit.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 12));
        submit.setFocusPainted(false);
        submit.setOpaque(true);
        submit.setMargin(JBUI.emptyInsets());
        submit.setToolTipText("Submit user message");
        return submit;
    }

    public void addActionMap(ActionListener listener) {
        this.listener = listener;

        AnAction sendAction = new AnAction("Ask to OllamAssit") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                triggerAction();
            }
        };

        AnAction insertNewLineAction = new AnAction("Insert New Line") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                insertNewLine(editorTextField.getEditor());
            }
        };

        ShortcutSet sendShortcuts = new CustomShortcutSet(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
        );


        ShortcutSet newlineShortcuts = new CustomShortcutSet(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)
        );

        sendAction.registerCustomShortcutSet(sendShortcuts, editorTextField.getComponent());
        insertNewLineAction.registerCustomShortcutSet(newlineShortcuts, editorTextField.getComponent());

        sendButton.addActionListener(e -> triggerAction());
    }


    private void insertNewLine(Editor editor) {
        WriteCommandAction.runWriteCommandAction(editor.getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
            Document document = editor.getDocument();
            CaretModel caretModel = editor.getCaretModel();
            int offset = caretModel.getOffset();

            document.insertString(offset, "\n");

            caretModel.moveToOffset(offset + 1);
        }));

        SwingUtilities.invokeLater(() -> {
            JComponent editorComponent = editor.getContentComponent();
            editorComponent.requestFocusInWindow();
            editorComponent.repaint();
        });
    }

    public void triggerAction() {
        listener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null));
    }

    public void clear() {
        editorTextField.setText("");
    }

    public String getUserPrompt() {
        return editorTextField.getText();
    }

    @Override
    public void dispose() {
        if (editorTextField != null) {
            Editor editor = editorTextField.getEditor();
            if (editor != null) {
                JComponent editorComponent = editor.getContentComponent();
                editorComponent.getActionMap().remove("sendMessage");
                editorComponent.getActionMap().remove("insertNewline");
            }
        }
    }


}