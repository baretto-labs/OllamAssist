package fr.baretto.ollamassist.component;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import dev.langchain4j.model.ollama.OllamaModel;
import dev.langchain4j.model.ollama.OllamaModels;
import fr.baretto.ollamassist.chat.ui.IconUtils;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.util.Collections;
import java.util.List;

import static fr.baretto.ollamassist.setting.OllamAssistSettings.DEFAULT_URL;

@Getter
public class PromptPanel extends JPanel implements Disposable {

    private static final Border DEFAULT_EDITOR_BORDER = BorderFactory.createEmptyBorder(6, 6, 6, 6);
    private static final Border FOCUSED_EDITOR_BORDER = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIUtil.getFocusedBorderColor(), 1),
            BorderFactory.createEmptyBorder(0, 0, 0, 0)
    );

    private EditorTextField editorTextField;
    private JButton sendButton;
    private ComboBox<String> modelSelector;
    private ActionListener listener;

    public PromptPanel() {
        super(new BorderLayout());
        setupUI();
        setActions();
    }

    private void setActions() {
        AnAction sendAction = new AnAction("Ask to OllamAssist") {
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

    private void setupUI() {
        editorTextField = new ScrollableEditorTextField();
        editorTextField.setFocusable(true);
        editorTextField.setOneLineMode(false);
        editorTextField.setBackground(UIUtil.getTextFieldBackground());
        editorTextField.setForeground(UIUtil.getTextFieldForeground());
        editorTextField.setBorder(DEFAULT_EDITOR_BORDER);
        editorTextField.addSettingsProvider(editor -> {
            EditorSettings settings = editor.getSettings();
            settings.setUseSoftWraps(true);
        });


        modelSelector = createModelSelector();
        sendButton = createSubmitButton();
        ComponentCustomizer.applyHoverEffect(sendButton);


        JPanel controlPanel = new JPanel(new BorderLayout(10, 0));
        controlPanel.setOpaque(false);

        JPanel comboButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        comboButtonPanel.setOpaque(false);
       // comboButtonPanel.add(modelSelector);
        comboButtonPanel.add(sendButton);

        controlPanel.add(comboButtonPanel, BorderLayout.EAST);


        JPanel container = new JPanel(new BorderLayout());
        container.add(editorTextField, BorderLayout.CENTER);
        container.add(controlPanel, BorderLayout.SOUTH);

        editorTextField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                editorTextField.setBorder(FOCUSED_EDITOR_BORDER);
                editorTextField.revalidate();
                editorTextField.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                editorTextField.setBorder(DEFAULT_EDITOR_BORDER);
                editorTextField.revalidate();
                editorTextField.repaint();
            }
        });

        setBackground(UIUtil.getPanelBackground());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));

        add(container, BorderLayout.CENTER);
    }

    private ComboBox<String> createModelSelector() {
        ComboBox<String> combo = new ComboBox<>();
        combo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

            }
        });

        combo.setEditable(false);
        combo.setPreferredSize(new Dimension(120, 30));
        combo.setMinimumSize(new Dimension(80, 30));
        combo.setBackground(UIUtil.getTextFieldBackground());
        combo.setToolTipText("Select chat model");
        return combo;
    }

    public void updateModelList(List<String> models, String currentModel) {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        for (String m : models) {
            model.addElement(m);
        }

        if(models.contains(currentModel)) {
            model.setSelectedItem(currentModel);
        } else {
            model.addElement(currentModel);
            model.setSelectedItem(currentModel);
        }

        modelSelector.setModel(model);
    }

    public String getCurrentModel() {
        return (String) modelSelector.getSelectedItem();
    }

    public void setCurrentModel(String modelName) {
        modelSelector.setSelectedItem(modelName);
        if(modelSelector.getSelectedIndex() == -1) {
            DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) modelSelector.getModel();
            model.addElement(modelName);
            modelSelector.setSelectedItem(modelName);
        }
    }

    private List<String> fetchAvailableModels() {
        try {
            return OllamaModels.builder()
                    .baseUrl(OllamAssistSettings.getInstance().getOllamaUrl())
                    .build()
                    .availableModels()
                    .content()
                    .stream()
                    .map(OllamaModel::getName)
                    .toList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private JButton createSubmitButton() {
        JButton submit = new JButton(IconUtils.SUBMIT);
        submit.setPreferredSize(new Dimension(100, 30));
        submit.setMinimumSize(new Dimension(100, 30));
        submit.setMaximumSize(new Dimension(100, 30));
        submit.setBackground(UIUtil.getPanelBackground());
        submit.setForeground(UIUtil.getLabelForeground());
        submit.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(4, 8, 4, 8),
                submit.getBorder()
        ));
        submit.setFocusPainted(false);
        submit.setOpaque(true);
        submit.setMargin(JBUI.emptyInsets());
        submit.setToolTipText("Submit user message");
        return submit;
    }

    public void addActionMap(ActionListener listener) {
        this.listener = listener;
    }

    public void setAvailableModels(List<String> models) {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        models.forEach(model::addElement);
        modelSelector.setModel(model);
    }

    public String getSelectedModel() {
        return (String) modelSelector.getSelectedItem();
    }

    public void setSelectedModel(String modelName) {
        modelSelector.setSelectedItem(modelName);
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
        if (listener != null) {
            listener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null));
        }
    }

    public void clear() {
        editorTextField.setText("");
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

    public void removeListeners() {
        for (MouseListener ml : this.getMouseListeners()) {
            this.removeMouseListener(ml);
        }
        for (KeyListener kl : this.getKeyListeners()) {
            this.removeKeyListener(kl);
        }
        for (ComponentListener cl : this.getComponentListeners()) {
            this.removeComponentListener(cl);
        }
    }

    public void clearUserPrompt() {
        editorTextField.setText("");
    }

    public String getUserPrompt() {
        return editorTextField.getText();
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(200, super.getMinimumSize().height);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(
                super.getPreferredSize().width,
                editorTextField.getPreferredSize().height + 70
        );
    }
}