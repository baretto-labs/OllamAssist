package fr.baretto.ollamassist.component;

import com.intellij.openapi.Disposable;
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

@Getter
public class PromptPanel extends JPanel implements Disposable {

    private JTextArea textArea;
    private JButton sendButton;

    public PromptPanel() {
        super(new BorderLayout());
        setupUI();
    }

    private void setupUI() {
        setBackground(UIUtil.getPanelBackground());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        textArea = new JTextArea();
        textArea.setLineWrap(true);
        textArea.setFocusable(true);
        textArea.setWrapStyleWord(true);
        textArea.setBackground(UIUtil.getTextFieldBackground());
        textArea.setForeground(UIUtil.getTextFieldForeground());
        textArea.setBorder(BorderFactory.createEmptyBorder(0, 0, 30, 0));

        JBScrollPane scrollPane = new JBScrollPane(textArea);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        sendButton = createStyledButton();

        JPanel overlayPanel = new JPanel(new BorderLayout());
        overlayPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonContainer = new JPanel(new BorderLayout());
        buttonContainer.setOpaque(false);

        JPanel buttonWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonWrapper.setOpaque(false);
        buttonWrapper.add(sendButton);

        buttonContainer.add(buttonWrapper, BorderLayout.EAST);
        overlayPanel.add(buttonContainer, BorderLayout.SOUTH);
        add(overlayPanel, BorderLayout.CENTER);
    }

    private JButton createStyledButton() {
        JButton btn = new JButton(IconUtils.SUBMIT);
        btn.setBackground(UIUtil.getPanelBackground());
        btn.setForeground(UIUtil.getLabelForeground());
        btn.setBorder(null);
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setMargin(JBUI.insets(2));
        return btn;
    }

    public void addActionMap(ActionListener listener) {
        textArea.getInputMap().put(KeyStroke.getKeyStroke("shift ENTER"), "insertNewline");
        textArea.getActionMap().put("insertNewline", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                textArea.append("\n");
            }
        });

        textArea.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "sendMessage");
        textArea.getActionMap().put("sendMessage", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                triggerAction(listener);
            }
        });

        sendButton.addActionListener(e -> triggerAction(listener));
    }

    public void triggerAction(ActionListener listener) {
        listener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null));
    }

    public void clear() {
        textArea.setText("");
    }

    public String getUserPrompt() {
        return textArea.getText();
    }

    @Override
    public void dispose() {

    }
}