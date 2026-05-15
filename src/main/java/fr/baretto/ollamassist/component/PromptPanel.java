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
import com.intellij.openapi.ui.Messages;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.icons.AllIcons;
import fr.baretto.ollamassist.agent.ui.AgentHistoryPopup;
import fr.baretto.ollamassist.auth.AuthenticationHelper;
import fr.baretto.ollamassist.chat.ui.IconUtils;
import fr.baretto.ollamassist.utils.FontUtils;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private transient Project project;
    private transient ActionListener listener;

    private EditorTextField editorTextField;
    private JButton sendButton;
    private ModelSelector modelSelector;
    /** Dedicated model selector shown when agent mode is active — can differ from the chat model. */
    private ModelSelector agentModelSelector;
    private JButton stopButton;
    private JLabel agentProgressLabel;
    private boolean isGenerating = false;
    private JToggleButton webSearchButton;
    private JToggleButton ragSearchhButton;
    private JToggleButton agentModeButton;
    /** History button — only shown when agent mode is active (U-7). */
    private JButton agentHistoryButton;
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

        // Dedicated model selector for agent mode — defaults to the chat model but independently
        // configurable so users can pick a function-calling-capable model without touching the chat model.
        agentModelSelector = new ModelSelector();
        agentModelSelector.setModelLoader(this::fetchAvailableModels);
        agentModelSelector.reconfigure(
                () -> fr.baretto.ollamassist.setting.OllamaSettings.getInstance().getAgentModelName(),
                name -> fr.baretto.ollamassist.setting.OllamaSettings.getInstance().setAgentModelName(name)
        );
        agentModelSelector.setToolTipText(
                "<html>Agent model — choose a model with function calling support.<br>" +
                "Recommended: qwen3, qwen2.5:7b+, llama3.1:8b+<br>" +
                "Defaults to the chat model when unchanged.</html>"
        );
        agentModelSelector.setVisible(false);

        sendButton = createSubmitButton();
        stopButton = createStopButton();
        stopButton.setVisible(false);

        ComponentCustomizer.applyHoverEffect(sendButton);
        ComponentCustomizer.applyHoverEffect(stopButton);


        JPanel controlPanel = new JPanel(new BorderLayout(10, 0));
        controlPanel.setOpaque(false);


        agentProgressLabel = new JLabel();
        agentProgressLabel.setFont(FontUtils.getSmallFont());
        agentProgressLabel.setForeground(JBColor.GRAY);
        agentProgressLabel.setVisible(false);

        JPanel rightControlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightControlPanel.setOpaque(false);
        rightControlPanel.add(agentProgressLabel);
        rightControlPanel.add(agentModelSelector);
        rightControlPanel.add(modelSelector);
        rightControlPanel.add(sendButton);
        rightControlPanel.add(stopButton);


        if (project != null) {
            JPanel leftControlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            leftControlPanel.setOpaque(false);
            ragSearchhButton = createRagSearchButton();
            webSearchButton = createWebSearchButton();
            agentModeButton = createAgentModeButton();
            agentHistoryButton = createAgentHistoryButton();
            leftControlPanel.add(webSearchButton);
            leftControlPanel.add(ragSearchhButton);
            leftControlPanel.add(agentModeButton);
            leftControlPanel.add(agentHistoryButton);
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

    private static final String AGENT_PREVIEW_SHOWN_KEY = "ollamassist.agent.preview.shown";
    private static final String AGENT_MODE_ENABLED_KEY  = "ollamassist.agent.mode.enabled";
    private static final String AGENT_MODE_ENABLED = "Agent mode enabled — click to switch back to chat";
    private static final String AGENT_MODE_DISABLED =
            "<html>Switch to agent mode (Preview) — the agent plans and executes tasks autonomously.<br>"
            + "Requires a model with reliable structured output (JSON).<br>"
            + "<b>Recommended:</b> qwen2.5:14b+, mistral-nemo, deepseek-coder:33b<br>"
            + "<i>llama3.1:8b and llama3.2 may produce invalid plans.</i></html>";

    private JToggleButton createAgentModeButton() {
        JToggleButton button = new JToggleButton(IconUtils.AGENT_DISABLED);
        // Restore persisted state so the user does not have to re-enable on every IDE restart (U-1).
        boolean persisted = PropertiesComponent.getInstance().getBoolean(AGENT_MODE_ENABLED_KEY, false);
        button.setSelected(persisted);
        button.setToolTipText(AGENT_MODE_DISABLED);
        button.setPreferredSize(new Dimension(30, 30));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(false);
        button.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        button.setMargin(JBUI.emptyInsets());

        button.addActionListener(e -> {
            // Persist the new state immediately so it survives IDE restarts.
            PropertiesComponent.getInstance().setValue(AGENT_MODE_ENABLED_KEY, button.isSelected());
            if (button.isSelected() && !PropertiesComponent.getInstance().getBoolean(AGENT_PREVIEW_SHOWN_KEY, false)) {
                Messages.showInfoMessage(
                        """
                        Agent mode is a Preview feature.

                        The agent can read, edit and create files, run commands, and search your codebase.
                        Always review the plan before validating execution.

                        Model requirement: agent mode uses structured output (JSON). Your chat model must
                        support it reliably. Recommended: qwen2.5:14b+, mistral-nemo, deepseek-coder:33b.
                        Models like llama3.1:8b or llama3.2 may produce invalid plans.""",
                        "Agent Mode — Preview"
                );
                PropertiesComponent.getInstance().setValue(AGENT_PREVIEW_SHOWN_KEY, true);
            }
            updateAgentModeButtonState(button);
        });
        updateAgentModeButtonState(button);

        return button;
    }

    private void updateAgentModeButtonState(JToggleButton button) {
        if (button.isSelected()) {
            button.setIcon(IconUtils.AGENT_ENABLED);
            button.setToolTipText(AGENT_MODE_ENABLED);
            updateModelSelectorForAgentMode(true);
            if (agentHistoryButton != null) agentHistoryButton.setVisible(true);
        } else {
            button.setIcon(IconUtils.AGENT_DISABLED);
            button.setToolTipText(AGENT_MODE_DISABLED);
            updateModelSelectorForAgentMode(false);
            if (agentHistoryButton != null) agentHistoryButton.setVisible(false);
        }
    }

    private JButton createAgentHistoryButton() {
        JButton button = new JButton(AllIcons.Vcs.History);
        button.setPreferredSize(new Dimension(30, 30));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setToolTipText("View agent execution history");
        // Only visible when agent mode is active (toggled by updateAgentModeButtonState)
        button.setVisible(agentModeButton != null && agentModeButton.isSelected());
        button.addActionListener(e -> {
            if (project != null) AgentHistoryPopup.show(project);
        });
        return button;
    }

    /**
     * Swaps the chat model selector for the agent model selector when agent mode is active.
     * Both selectors share the same list of available models (fetched from Ollama) but persist
     * their selection independently — users can pick a function-calling-capable model for the
     * agent without changing their preferred chat model.
     */
    private void updateModelSelectorForAgentMode(boolean agentActive) {
        if (modelSelector == null || agentModelSelector == null) return;
        if (agentActive) {
            modelSelector.setVisible(false);
            agentModelSelector.setVisible(true);
        } else {
            agentModelSelector.setVisible(false);
            modelSelector.setVisible(true);
        }
    }

    public boolean isAgentMode() {
        return agentModeButton != null && agentModeButton.isSelected();
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
            String baseUrl = OllamAssistSettings.getInstance().getChatOllamaUrl();
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create(baseUrl.replaceAll("/$", "") + "/api/tags"))
                    .timeout(Duration.ofSeconds(10));
            if (AuthenticationHelper.isAuthenticationConfigured()) {
                requestBuilder.header(AUTHORIZATION_HEADER,
                        String.format(BASIC_AUTH_FORMAT, AuthenticationHelper.createBasicAuthHeader()));
            }
            HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return Collections.emptyList();
            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            JsonNode models = root.path("models");
            if (!models.isArray()) return Collections.emptyList();
            List<String> names = new ArrayList<>();
            for (JsonNode model : models) {
                String name = model.path("name").asText(null);
                if (name != null && !name.isBlank()) names.add(name);
            }
            return names;
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
            if (!isGenerating && agentProgressLabel != null) {
                agentProgressLabel.setVisible(false);
                agentProgressLabel.setText("");
            }
        });
    }

    /** Updates the agent step counter label shown next to the stop button. */
    public void setAgentProgress(int current, int total) {
        if (agentProgressLabel == null) return;
        SwingUtilities.invokeLater(() -> {
            agentProgressLabel.setText("step " + current + "/" + total);
            agentProgressLabel.setVisible(isGenerating);
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