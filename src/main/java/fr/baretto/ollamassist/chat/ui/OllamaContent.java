package fr.baretto.ollamassist.chat.ui;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.TokenStream;
import fr.baretto.ollamassist.chat.service.OllamaService;
import fr.baretto.ollamassist.component.ComponentCustomizer;
import fr.baretto.ollamassist.component.PromptPanel;
import fr.baretto.ollamassist.component.WorkspaceFileSelector;
import fr.baretto.ollamassist.core.agent.AgentChatIntegration;
import fr.baretto.ollamassist.core.agent.AgentCoordinator;
import fr.baretto.ollamassist.core.agent.ui.AgentStatusPanel;
import fr.baretto.ollamassist.events.ModelAvailableNotifier;
import fr.baretto.ollamassist.prerequiste.PrerequisitesPanel;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Slf4j
public class OllamaContent {

    private final Context context;
    @Getter
    private final BorderLayoutPanel contentPanel = new BorderLayoutPanel();
    private final PromptPanel promptInput;
    private final WorkspaceFileSelector filesSelector;
    private final MessagesPanel outputPanel = new MessagesPanel();
    private final AgentChatIntegration agentChatIntegration;
    private final AgentStatusPanel agentStatusPanel;
    private boolean isAvailable = false;
    private ChatThread currentChatThread;
    private JPanel contextContentPanel;


    public OllamaContent(@NotNull ToolWindow toolWindow) {
        this.context = new Context(toolWindow.getProject());
        promptInput = new PromptPanel(toolWindow.getProject());
        filesSelector = new WorkspaceFileSelector(toolWindow.getProject());

        // Initialiser l'intégration agent avec MessagesPanel
        AgentCoordinator agentCoordinator = toolWindow.getProject().getService(AgentCoordinator.class);
        agentCoordinator.setMessagesPanel(outputPanel);
        this.agentChatIntegration = new AgentChatIntegration(toolWindow.getProject(), agentCoordinator);

        // Initialiser le panel de status agent
        this.agentStatusPanel = new AgentStatusPanel(toolWindow.getProject());

        PrerequisitesPanel prerequisitesPanel = new PrerequisitesPanel(toolWindow.getProject());
        AskToChatAction askToChatAction = new AskToChatAction(promptInput, context);
        promptInput.addActionMap(askToChatAction);
        outputPanel.addContexteAndPrompt(context, promptInput);
        contentPanel.add(prerequisitesPanel);

        MessageBusConnection connection = context.project().getMessageBus()
                .connect();

        subscribe(connection);
        promptInput.addStopActionListener(e -> stopGeneration());
        Disposer.register(toolWindow.getDisposable(), connection);

        // Enregistrer dispose de l'agent et du status panel
        Disposer.register(toolWindow.getDisposable(), agentChatIntegration::dispose);
        Disposer.register(toolWindow.getDisposable(), agentStatusPanel::dispose);

        // TEMPORAIRE: Configurer le fallback pour debug
        agentChatIntegration.setChatFallbackHandler(this::processUserMessage);
    }

    private void subscribe(MessageBusConnection connection) {
        connection.subscribe(ModelAvailableNotifier.TOPIC, (ModelAvailableNotifier) () -> {
            if (!isAvailable) {
                SwingUtilities.invokeLater(() -> {
                    contentPanel.removeAll();
                    initUI();
                    contentPanel.revalidate();
                    contentPanel.repaint();
                });
            }
        });


        // Note: NewUserMessageNotifier est géré par AgentChatIntegration
        // qui s'abonne dans son constructeur et fait le fallback vers processUserMessage()


    }

    private void initUI() {
        this.isAvailable = true;
        contentPanel.setLayout(new BorderLayout());
        contentPanel.add(createMainChatInterface(), BorderLayout.CENTER);
    }

    private JPanel createMainChatInterface() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(UIUtil.getPanelBackground());

        // Panel de status agent en haut (si activé)
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        topPanel.add(agentStatusPanel, BorderLayout.NORTH);

        // Header avec conversation selector
        JPanel headerPanel = createHeaderPanel();
        topPanel.add(headerPanel, BorderLayout.CENTER);

        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Chat area avec splitter vertical
        OnePixelSplitter mainSplitter = new OnePixelSplitter(true, 0.75f);
        mainSplitter.setFirstComponent(createMessagesArea());
        mainSplitter.setSecondComponent(createInputArea());
        mainSplitter.setHonorComponentsMinimumSize(true);
        mainSplitter.setResizeEnabled(true);

        mainPanel.add(mainSplitter, BorderLayout.CENTER);
        return mainPanel;
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(UIUtil.getPanelBackground());
        headerPanel.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
                JBUI.Borders.empty(8, 12)
        ));

        ConversationSelectorPanel conversationSelector = new ConversationSelectorPanel();
        headerPanel.add(conversationSelector, BorderLayout.CENTER);


        return headerPanel;
    }


    private JPanel createMessagesArea() {
        JPanel messagesArea = new JPanel(new BorderLayout());
        messagesArea.setBackground(UIUtil.getPanelBackground());
        messagesArea.setBorder(JBUI.Borders.empty(8, 12));
        messagesArea.add(outputPanel, BorderLayout.CENTER);
        return messagesArea;
    }

    private JComponent createInputArea() {
        JPanel inputArea = new JPanel(new BorderLayout());
        inputArea.setBackground(UIUtil.getPanelBackground());
        inputArea.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
                JBUI.Borders.empty(8, 12)
        ));

        // Context panel (collapsible)
        JPanel contextPanel = createModernContextPanel();
        inputArea.add(contextPanel, BorderLayout.NORTH);

        // Input panel
        JPanel promptArea = createPromptArea();
        inputArea.add(promptArea, BorderLayout.CENTER);

        return inputArea;
    }

    private JPanel createPromptArea() {
        JPanel promptArea = new JPanel(new BorderLayout());
        promptArea.setBackground(UIUtil.getPanelBackground());
        promptArea.setBorder(JBUI.Borders.emptyTop(8));

        JBScrollPane scrollPane = new JBScrollPane(promptInput);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border()),
                JBUI.Borders.empty(4)
        ));

        promptArea.add(scrollPane, BorderLayout.CENTER);
        return promptArea;
    }

    private JPanel createModernContextPanel() {
        JPanel contextPanel = new JPanel(new BorderLayout());
        contextPanel.setBackground(UIUtil.getPanelBackground());

        // Header avec design moderne
        JPanel headerPanel = createContextHeader();
        contextPanel.add(headerPanel, BorderLayout.NORTH);

        // Content area avec meilleur style
        JPanel contextContent = createContextContent();
        contextPanel.add(contextContent, BorderLayout.CENTER);

        return contextPanel;
    }

    private JPanel createContextHeader() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(UIUtil.getPanelBackground());
        headerPanel.setBorder(JBUI.Borders.empty(4, 0, 8, 0));

        // Toggle button avec style moderne
        JButton toggleButton = new JButton();
        toggleButton.setBorderPainted(false);
        toggleButton.setFocusPainted(false);
        toggleButton.setContentAreaFilled(false);
        toggleButton.setHorizontalAlignment(SwingConstants.LEFT);
        toggleButton.setBorder(JBUI.Borders.empty(4, 0));
        toggleButton.setFont(toggleButton.getFont().deriveFont(Font.BOLD, 12f));

        // Token count avec meilleur style
        JBLabel tokenCountLabel = new JBLabel("Tokens: 0");
        tokenCountLabel.setFont(tokenCountLabel.getFont().deriveFont(Font.PLAIN, 11f));
        tokenCountLabel.setForeground(JBColor.GRAY);

        // Action buttons avec spacing amélioré
        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actionsPanel.setOpaque(false);

        JButton addButton = createModernActionButton(IconUtils.ADD_TO_CONTEXT, "Add to context");
        addButton.addActionListener(filesSelector::addFilesAction);

        JButton removeButton = createModernActionButton(IconUtils.REMOVE_TO_CONTEXT, "Remove from context");
        removeButton.setEnabled(false);
        removeButton.addActionListener(filesSelector::removeFilesAction);

        actionsPanel.add(addButton);
        actionsPanel.add(removeButton);

        // Layout du header
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.setOpaque(false);
        leftPanel.add(toggleButton);
        leftPanel.add(Box.createHorizontalStrut(8));
        leftPanel.add(tokenCountLabel);

        headerPanel.add(leftPanel, BorderLayout.WEST);
        headerPanel.add(actionsPanel, BorderLayout.EAST);

        // Setup du toggle
        boolean[] isCollapsed = {OllamAssistSettings.getInstance().getUIState()};
        updateToggleButton(toggleButton, isCollapsed[0]);

        toggleButton.addActionListener(e -> {
            isCollapsed[0] = !isCollapsed[0];
            updateToggleButton(toggleButton, isCollapsed[0]);
            updateContextVisibility(isCollapsed[0]);
            OllamAssistSettings.getInstance().setUIState(isCollapsed[0]);
        });

        // Listeners pour les actions
        filesSelector.getFileTable().getModel().addTableModelListener(e ->
                updateTokenCount(filesSelector, tokenCountLabel)
        );

        filesSelector.getFileTable().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                removeButton.setEnabled(filesSelector.getFileTable().getSelectedRowCount() > 0);
            }
        });

        updateTokenCount(filesSelector, tokenCountLabel);

        return headerPanel;
    }

    private JPanel createContextContent() {
        contextContentPanel = new JPanel(new BorderLayout());
        contextContentPanel.setBackground(UIUtil.getPanelBackground());

        JBScrollPane fileScrollPane = new JBScrollPane(filesSelector.getFileTable());
        fileScrollPane.setMinimumSize(new Dimension(0, 120));
        fileScrollPane.setPreferredSize(new Dimension(0, 150));
        fileScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        fileScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        fileScrollPane.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border()),
                JBUI.Borders.empty()
        ));

        contextContentPanel.add(fileScrollPane, BorderLayout.CENTER);

        // Initialiser la visibilité
        boolean isCollapsed = OllamAssistSettings.getInstance().getUIState();
        contextContentPanel.setVisible(!isCollapsed);

        return contextContentPanel;
    }

    private JButton createModernActionButton(Icon icon, String tooltip) {
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        button.setBorder(JBUI.Borders.empty(4, 8));
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setPreferredSize(JBUI.size(28, 28));
        ComponentCustomizer.applyHoverEffect(button);
        return button;
    }

    private void updateToggleButton(JButton toggleButton, boolean isCollapsed) {
        toggleButton.setText((isCollapsed ? "▶ " : "▼ ") + "Context Files");
    }

    private void updateContextVisibility(boolean isCollapsed) {
        if (contextContentPanel != null) {
            contextContentPanel.setVisible(!isCollapsed);
            SwingUtilities.invokeLater(() -> {
                contextContentPanel.getParent().revalidate();
                contextContentPanel.getParent().repaint();
            });
        }
    }

    /**
     * Traite un message utilisateur en mode chat classique (fallback de l'agent)
     */
    public void processUserMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        // Récupérer le service OllamaService
        OllamaService ollamaService = context.project().getService(OllamaService.class);
        if (ollamaService == null || ollamaService.getAssistant() == null) {
            log.warn("OllamaService ou Assistant non disponible");
            return;
        }

        // Arrêter la génération précédente si elle existe
        stopGeneration();

        // Démarrer une nouvelle génération
        promptInput.toggleGenerationState(true);

        try {
            // Créer une nouvelle session de chat via TokenStream
            TokenStream tokenStream = ollamaService.getAssistant().chat(message);

            currentChatThread = ChatThread.builder()
                    .tokenStream(tokenStream)
                    .onNext(this::publish)
                    .onError(this::logException)
                    .onCompleteResponse(this::done)
                    .build()
                    .start();

        } catch (Exception e) {
            log.error("Erreur lors du démarrage du chat", e);
            logException(e);
        }
    }

    private void updateTokenCount(WorkspaceFileSelector fileSelector, JLabel tokenLabel) {
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() {
                return fileSelector.getTotalTokens();
            }

            @Override
            protected void done() {
                try {
                    long tokenCount = get();
                    tokenLabel.setText("Tokens: " + tokenCount);
                } catch (Exception e) {
                    tokenLabel.setText("Tokens: ?");
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }


    private void stopGeneration() {
        // Arrêter l'ancien système de chat
        if (currentChatThread != null) {
            currentChatThread.stop();
            outputPanel.cancelMessage();
        }

        // Arrêter l'agent unifié
        if (agentChatIntegration != null) {
            AgentCoordinator agentCoordinator = context.project().getService(AgentCoordinator.class);
            if (agentCoordinator != null) {
                agentCoordinator.cancelAllTasks();
            }
        }

        // Réactiver le prompt
        promptInput.toggleGenerationState(false);
    }

    private void logException(Throwable throwable) {
        log.error("Exception: " + throwable);
        done(ChatResponse.builder().finishReason(FinishReason.OTHER).aiMessage(AiMessage.from(throwable.getMessage())).build());
    }

    private void done(ChatResponse chatResponse) {
        outputPanel.finalizeMessage(chatResponse);
        promptInput.toggleGenerationState(false);
    }

    private void publish(String token) {
        outputPanel.appendToken(token);
    }

    @Builder
    private static class ChatThread {

        private final AtomicBoolean stopped = new AtomicBoolean(false);
        private final Lock lock = new ReentrantLock();
        private final TokenStream tokenStream;
        private final Consumer<String> onNext;
        private final Consumer<Throwable> onError;
        private final Consumer<ChatResponse> onCompleteResponse;

        public ChatThread start() {
            new Thread(this::run).start();
            return this;
        }

        private void run() {
            if (onNext != null) {
                tokenStream.onPartialResponse(stoppable(onNext));
            }
            if (onError != null) {
                tokenStream.onError(stoppable(onError));
            }
            if (onCompleteResponse != null) {
                tokenStream.onCompleteResponse(stoppable(onCompleteResponse));
            }
            tokenStream.start();
        }

        public void stop() {
            try {
                lock.lock();
                stopped.set(true);
            } finally {
                lock.unlock();
            }
        }

        private <T> Consumer<T> stoppable(java.util.function.Consumer<T> onNext) {
            return (T t) -> {
                try {
                    lock.lock();
                    if (!stopped.get()) {
                        onNext.accept(t);
                    }
                } finally {
                    lock.unlock();
                }
            };
        }

    }


}