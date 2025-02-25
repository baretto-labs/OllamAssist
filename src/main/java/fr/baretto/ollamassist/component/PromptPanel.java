package fr.baretto.ollamassist.component;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import fr.baretto.ollamassist.chat.ui.IconUtils;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

@Getter
public class PromptPanel extends JPanel implements Disposable {

    private EditorTextField editorTextField;
    private JButton sendButton;
    private KeyStroke enterKey;
    private KeyStroke shiftEnterKey;


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

        editorTextField = new EditorTextField();
        editorTextField.setFocusable(true);
        editorTextField.setBackground(UIUtil.getTextFieldBackground());
        editorTextField.setForeground(UIUtil.getTextFieldForeground());
        editorTextField.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JBScrollPane scrollPane = new JBScrollPane(editorTextField);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        sendButton = createStyledButton();

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonPanel.setAlignmentX(RIGHT_ALIGNMENT);
        buttonPanel.setOpaque(false);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0)); // Padding droit ajouté ici
        buttonPanel.add(sendButton);

        JPanel container = new JPanel(new BorderLayout());
        container.add(scrollPane, BorderLayout.CENTER);
        container.add(buttonPanel, BorderLayout.SOUTH);

        add(container, BorderLayout.CENTER);
    }


    private JButton createStyledButton() {
        JButton btn = new JButton(IconUtils.SUBMIT);
        btn.setBackground(UIUtil.getPanelBackground());
        btn.setForeground(UIUtil.getLabelForeground());
        btn.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 12)); // Augmentation du padding gauche/droit
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setMargin(JBUI.emptyInsets());
        return btn;
    }

    public void addActionMap(ActionListener listener) {
        editorTextField.addSettingsProvider(editor -> {
            JComponent editorComponent = editor.getContentComponent();
            InputMap inputMap = editorComponent.getInputMap(JComponent.WHEN_FOCUSED);
            ActionMap actionMap = editorComponent.getActionMap();

            enterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
            shiftEnterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK);

            inputMap.put(enterKey, "sendMessage");
            actionMap.put("sendMessage", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (editorComponent.hasFocus()) {
                        triggerAction(listener);
                    }
                }
            });
            inputMap.put(shiftEnterKey, "insertNewline");
            actionMap.put("insertNewline", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (editorComponent.hasFocus()) {
                        insertNewLine(editor);
                    }
                }
            });
        });

        sendButton.addActionListener(e -> triggerAction(listener));
    }



    private void insertNewLine(Editor editor) {
        Document doc = editor.getDocument();
        CaretModel caret = editor.getCaretModel();
        doc.insertString(caret.getOffset(), "\n");
    }

    public void triggerAction(ActionListener listener) {
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
                editorComponent.getInputMap(JComponent.WHEN_FOCUSED).remove(enterKey);
                editorComponent.getInputMap(JComponent.WHEN_FOCUSED).remove(shiftEnterKey);
                editorComponent.getActionMap().remove("sendMessage");
                editorComponent.getActionMap().remove("insertNewline");
            }
        }
    }

}