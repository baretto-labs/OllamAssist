package fr.baretto.ollamassist.chat.ui;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.TokenStream;
import fr.baretto.ollamassist.chat.service.OllamaService;
import fr.baretto.ollamassist.component.ComponentCustomizer;
import fr.baretto.ollamassist.component.PromptPanel;
import fr.baretto.ollamassist.component.WorkspaceFileSelector;
import fr.baretto.ollamassist.events.ModelAvailableNotifier;
import fr.baretto.ollamassist.events.NewUserMessageNotifier;
import fr.baretto.ollamassist.prerequiste.PrerequisitesPanel;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Slf4j
public class OllamaContent {

    private final Context context;
    @Getter
    private final JPanel contentPanel = new JPanel();
    private final PromptPanel promptInput;
    private final WorkspaceFileSelector filesSelector;
    private final MessagesPanel outputPanel = new MessagesPanel();
    private boolean isAvailable = false;
    private ChatThread currentChatThread;


    public OllamaContent(@NotNull ToolWindow toolWindow) {
        this.context = new Context(toolWindow.getProject());
        promptInput = new PromptPanel(toolWindow.getProject());
        filesSelector = new WorkspaceFileSelector(toolWindow.getProject());
        PrerequisitesPanel prerequisitesPanel = new PrerequisitesPanel(toolWindow.getProject());
        AskToChatAction askToChatAction = new AskToChatAction(promptInput, context);
        promptInput.addActionMap(askToChatAction);
        outputPanel.addContexte(context);
        contentPanel.add(prerequisitesPanel);

        MessageBusConnection connection = context.project().getMessageBus()
                .connect();

        subscribe(connection);
        promptInput.addStopActionListener(e -> stopGeneration());
        Disposer.register(toolWindow.getDisposable(), connection);
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


        connection.subscribe(NewUserMessageNotifier.TOPIC, (NewUserMessageNotifier) message -> {
            if (currentChatThread != null) {
                currentChatThread.stop();
            }
            outputPanel.cancelMessage();
            outputPanel.addUserMessage(message);
            outputPanel.addNewAIMessage();

            currentChatThread = ChatThread.builder()
                    .tokenStream(context.project()
                            .getService(OllamaService.class)
                            .getAssistant()
                            .chat(message))
                    .onNext(this::publish)
                    .onError(this::logException)
                    .onCompleteResponse(this::done)
                    .build()
                    .start();
            promptInput.clear();
            promptInput.toggleGenerationState(true);
        });


    }

    private void initUI() {
        this.isAvailable = true;
        contentPanel.setLayout(new BorderLayout());
        contentPanel.add(createConversationPanel(), BorderLayout.NORTH);
        contentPanel.add(createSplitter(), BorderLayout.CENTER);
    }

    private JPanel createSplitter() {
        OnePixelSplitter splitter = new OnePixelSplitter(true, 0.70f);

        JPanel messagesPanel = new JPanel(new BorderLayout());
        messagesPanel.add(outputPanel, BorderLayout.CENTER);

        JComponent inputPanel = createInputPanel();

        splitter.setFirstComponent(messagesPanel);
        splitter.setSecondComponent(inputPanel);
        splitter.setHonorComponentsMinimumSize(true);
        splitter.setResizeEnabled(true);

        return splitter;
    }

    private JComponent createInputPanel() {
        JBScrollPane scrollPane = new JBScrollPane(promptInput);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        JPanel filePanel = createCollapsiblePanel(filesSelector);

        OnePixelSplitter splitter = new OnePixelSplitter(true, 0.0f);
        splitter.setFirstComponent(filePanel);
        splitter.setSecondComponent(scrollPane);
        splitter.setHonorComponentsMinimumSize(true);

        int[] initialSize = {150};

        AbstractButton toggleButton = (AbstractButton) ((JPanel) filePanel.getComponent(0)).getComponent(0);
        toggleButton.addActionListener(e -> {
            boolean currentlyCollapsed = !filePanel.getComponent(1).isVisible();
            filePanel.getComponent(1).setVisible(!currentlyCollapsed);

            SwingUtilities.invokeLater(() -> {
                if (currentlyCollapsed) {
                    splitter.setProportion(initialSize[0] / (float) Math.max(splitter.getHeight(), initialSize[0] + 100));
                } else {
                    int headerHeight = filePanel.getComponent(0).getPreferredSize().height;
                    splitter.setProportion(headerHeight / (float) Math.max(splitter.getHeight(), headerHeight + 100));
                }

                filePanel.revalidate();
                splitter.revalidate();
            });
        });

        splitter.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                initialSize[0] = splitter.getFirstComponent().getHeight();
            }
        });

        return splitter;
    }

    private JPanel createCollapsiblePanel(WorkspaceFileSelector fileSelector) {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.X_AXIS));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JButton toggleButton = new JButton();
        toggleButton.setBorderPainted(false);
        toggleButton.setFocusPainted(false);
        toggleButton.setContentAreaFilled(false);
        toggleButton.setHorizontalAlignment(SwingConstants.LEFT);

        JButton addButton = new JButton(IconUtils.ADD_TO_CONTEXT);
        addButton.setToolTipText("Add to context");
        addButton.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        addButton.setContentAreaFilled(false);
        addButton.setFocusPainted(false);
        addButton.setPreferredSize(new Dimension(24, 24));
        addButton.addActionListener(fileSelector::addFilesAction);
        ComponentCustomizer.applyHoverEffect(addButton);

        JButton removeButton = new JButton(IconUtils.REMOVE_TO_CONTEXT);
        removeButton.setToolTipText("Remove from context");
        removeButton.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        removeButton.setContentAreaFilled(false);
        removeButton.setFocusPainted(false);
        removeButton.setPreferredSize(new Dimension(24, 24));
        removeButton.setEnabled(false);
        removeButton.addActionListener(fileSelector::removeFilesAction);
        ComponentCustomizer.applyHoverEffect(removeButton);

        JLabel tokenCountLabel = new JLabel("Tokens: 0");
        tokenCountLabel.setBorder(JBUI.Borders.empty(0, 5));
        tokenCountLabel.setFont(tokenCountLabel.getFont().deriveFont(Font.PLAIN, 11f));
        tokenCountLabel.setForeground(JBColor.GRAY);
        fileSelector.getFileTable().getModel().addTableModelListener(e ->
                updateTokenCount(fileSelector, tokenCountLabel)
        );

        fileSelector.getFileTable().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                removeButton.setEnabled(fileSelector.getFileTable().getSelectedRowCount() > 0);
            }
        });

        headerPanel.add(toggleButton);
        headerPanel.add(tokenCountLabel);
        headerPanel.add(Box.createHorizontalGlue());
        headerPanel.add(addButton);
        headerPanel.add(Box.createRigidArea(new Dimension(5, 0)));
        headerPanel.add(removeButton);
        updateTokenCount(fileSelector, tokenCountLabel);

        JPanel contentContainer = new JPanel(new BorderLayout());
        JBScrollPane fileScrollPane = new JBScrollPane(fileSelector.getFileTable());
        fileScrollPane.setMinimumSize(new Dimension(0, 100));
        fileScrollPane.setPreferredSize(new Dimension(0, 150));
        fileScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        fileScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        contentContainer.add(fileScrollPane, BorderLayout.CENTER);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(contentContainer, BorderLayout.CENTER);

        boolean[] isCollapsed = {OllamAssistSettings.getInstance().getUIState()};

        contentContainer.setVisible(!isCollapsed[0]);
        toggleButton.setText((isCollapsed[0] ? "► " : "▼ ") + "Context");

        toggleButton.addActionListener(e -> {
            isCollapsed[0] = !isCollapsed[0];
            contentContainer.setVisible(!isCollapsed[0]);
            toggleButton.setText((isCollapsed[0] ? "► " : "▼ ") + "Context");
            OllamAssistSettings.getInstance().setUIState(isCollapsed[0]);
        });

        return panel;
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

    private JPanel createConversationPanel() {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

        JPanel conversationPanel = new JPanel(new BorderLayout());
        conversationPanel.setLayout(new BoxLayout(conversationPanel, BoxLayout.Y_AXIS));

        conversationPanel.setPreferredSize(new Dimension(0, 24));
        JBScrollPane scrollPane = new JBScrollPane(container);

        ConversationSelectorPanel conversationSelectorPanel = new ConversationSelectorPanel();
        conversationPanel.add(conversationSelectorPanel, BorderLayout.NORTH);
        conversationPanel.add(scrollPane, BorderLayout.CENTER);
        return conversationPanel;
    }

    private void stopGeneration() {
        if (currentChatThread != null) {
            currentChatThread.stop();
            outputPanel.cancelMessage();
        }

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