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
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import dev.langchain4j.model.ollama.OllamaModel;
import dev.langchain4j.model.ollama.OllamaModels;
import fr.baretto.ollamassist.auth.AuthenticationHelper;
import fr.baretto.ollamassist.chat.ui.IconUtils;
import fr.baretto.ollamassist.events.StoreNotifier;
import fr.baretto.ollamassist.setting.ModelListener;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import fr.baretto.ollamassist.setting.OllamaSettings;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;


@Getter
public class PromptPanel extends JPanel implements Disposable {

    private static final Border DEFAULT_EDITOR_BORDER = BorderFactory.createEmptyBorder(6, 6, 6, 6);
    private static final Border FOCUSED_EDITOR_BORDER = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIUtil.getFocusedBorderColor(), 1),
            BorderFactory.createEmptyBorder(0, 0, 0, 0)
    );
    private static final String ENABLE_WEB_SEARCH_WITH_DUCK_DUCK_GO = "Enable web search with DuckDuckGO";
    private static final String ENABLE_RAG = "Enable RAG search";
    private static final String WEB_SEARCH_ENABLED = "Web search enabled with DuckDuckGO";
    private static final String RAG_SEARCH_ENABLED = "RAG search enabled";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BASIC_AUTH_FORMAT = "Basic %s";
    private transient Project project;
    private transient ActionListener listener;

    private EditorTextField editorTextField;
    private JButton sendButton;
    private ModelSelector modelSelector;
    private JButton stopButton;
    private boolean isGenerating = false;
    private JToggleButton webSearchButton;
    private JToggleButton ragSearchhButton;
    private boolean webSearchEnabled = OllamAssistSettings.getInstance().webSearchEnabled();
    private boolean ragEnabled = OllamAssistSettings.getInstance().ragEnabled();

    public PromptPanel() {
        super(new BorderLayout());
        setupUI();
        setActions();
    }

    public PromptPanel(final Project project) {
        super(new BorderLayout());
        this.project = project;
        setupUI();
        setActions();
        subscribeToModelChanges();
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

    private void subscribeToModelChanges() {
        ApplicationManager.getApplication().getMessageBus()
                .connect(this)
                .subscribe(ModelListener.TOPIC, this::updateModelSelector);
    }

    private void updateModelSelector() {
        // Update the ModelSelector display with the new model name from settings
        SwingUtilities.invokeLater(() -> {
            String newModelName = OllamaSettings.getInstance().getChatModelName();
            if (modelSelector != null && newModelName != null && !newModelName.isEmpty()) {
                modelSelector.setSelectedModel(newModelName);
            }
        });
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

        modelSelector = new ModelSelector();
        modelSelector.setSelectedModel(OllamAssistSettings.getInstance().getChatModelName());
        modelSelector.setModelLoader(this::fetchAvailableModels);

        sendButton = createSubmitButton();
        stopButton = createStopButton();
        stopButton.setVisible(false);

        ComponentCustomizer.applyHoverEffect(sendButton);
        ComponentCustomizer.applyHoverEffect(stopButton);


        JPanel controlPanel = new JPanel(new BorderLayout(10, 0));
        controlPanel.setOpaque(false);


        JPanel rightControlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightControlPanel.setOpaque(false);
        rightControlPanel.add(modelSelector);
        rightControlPanel.add(sendButton);
        rightControlPanel.add(stopButton);


        if (project != null) {
            JPanel leftControlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            leftControlPanel.setOpaque(false);
            ragSearchhButton = createRagSearchButton();
            webSearchButton = createWebSearchButton();
            leftControlPanel.add(webSearchButton);
            leftControlPanel.add(ragSearchhButton);
            controlPanel.add(leftControlPanel, BorderLayout.WEST);
        }

        controlPanel.add(rightControlPanel, BorderLayout.EAST);

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

    private JToggleButton createRagSearchButton() {
        JToggleButton button = new JToggleButton(IconUtils.RAG_SEARCH_DISABLED);
        button.setSelected(ragEnabled);
        button.setToolTipText(ENABLE_RAG);
        button.setPreferredSize(new Dimension(30, 30));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        button.setMargin(JBUI.emptyInsets());

        button.addActionListener(e -> {
            ragEnabled = button.isSelected();
            updateRagSearchButtonState(button);
        });

        updateRagSearchButtonState(button);

        return button;
    }

    private void updateRagSearchButtonState(JToggleButton button) {
        OllamAssistSettings
                .getInstance()
                .setRAGEnabled(ragEnabled);
        if (ragEnabled) {
            button.setIcon(IconUtils.RAG_SEARCH_ENABLED);
            button.setToolTipText(RAG_SEARCH_ENABLED);
            project.getMessageBus()
                    .syncPublisher(StoreNotifier.TOPIC)
                    .clearDatabaseAndRunIndexation();
        } else {
            button.setIcon(IconUtils.RAG_SEARCH_DISABLED);
            button.setToolTipText(ENABLE_RAG);
        }
    }

    private JToggleButton createWebSearchButton() {
        JToggleButton button = new JToggleButton(IconUtils.WEB_SEARCH_DISABLED);
        button.setSelected(webSearchEnabled);
        button.setToolTipText(ENABLE_WEB_SEARCH_WITH_DUCK_DUCK_GO);
        button.setPreferredSize(new Dimension(30, 30));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        button.setMargin(JBUI.emptyInsets());

        button.addActionListener(e -> {
            webSearchEnabled = button.isSelected();
            updateWebSearchButtonState(button);
        });

        updateWebSearchButtonState(button);

        return button;
    }

    private void updateWebSearchButtonState(JToggleButton button) {
        if (webSearchEnabled) {
            button.setIcon(IconUtils.WEB_SEARCH_ENABLED);
            button.setToolTipText(WEB_SEARCH_ENABLED);
        } else {
            button.setIcon(IconUtils.WEB_SEARCH_DISABLED);
            button.setToolTipText(ENABLE_WEB_SEARCH_WITH_DUCK_DUCK_GO);
        }
        OllamAssistSettings
                .getInstance()
                .setWebSearchEnabled(webSearchEnabled);
    }


    private List<String> fetchAvailableModels() {
        try {
            OllamaModels.OllamaModelsBuilder builder = OllamaModels.builder()
                    .baseUrl(OllamAssistSettings.getInstance().getChatOllamaUrl());
            
            // Add authentication if configured
            if (AuthenticationHelper.isAuthenticationConfigured()) {
                Map<String, String> customHeaders = new HashMap<>();
                customHeaders.put(AUTHORIZATION_HEADER, String.format(BASIC_AUTH_FORMAT, AuthenticationHelper.createBasicAuthHeader()));
                builder.customHeaders(customHeaders);
            }
            
            return new ArrayList<>(builder.build()
                    .availableModels()
                    .content()
                    .stream()
                    .map(OllamaModel::getName)
                    .toList());
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

    private JButton createStopButton() {
        JButton stop = new JButton(IconUtils.STOP);
        stop.setPreferredSize(new Dimension(100, 30));
        stop.setMinimumSize(new Dimension(100, 30));
        stop.setMaximumSize(new Dimension(100, 30));
        stop.setBackground(UIUtil.getPanelBackground());
        stop.setForeground(UIUtil.getLabelForeground());
        stop.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(4, 8, 4, 8),
                stop.getBorder()
        ));
        stop.setFocusPainted(false);
        stop.setOpaque(true);
        stop.setMargin(JBUI.emptyInsets());
        stop.setToolTipText("Stop current generation");
        return stop;
    }

    public void toggleGenerationState(boolean isGenerationInProcess) {
        SwingUtilities.invokeLater(() -> {
            this.isGenerating = isGenerationInProcess;
            sendButton.setVisible(!isGenerating);
            stopButton.setVisible(isGenerating);
            editorTextField.setEnabled(!isGenerating);
        });
    }

    public void addActionMap(ActionListener listener) {
        this.listener = listener;
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
        if (listener != null && !isGenerating) {
            listener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null));
        }
    }

    public void addStopActionListener(ActionListener listener) {
        stopButton.addActionListener(listener);
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