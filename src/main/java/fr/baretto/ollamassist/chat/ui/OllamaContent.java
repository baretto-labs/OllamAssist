package fr.baretto.ollamassist.chat.ui;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.messages.MessageBusConnection;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.TokenStream;
import fr.baretto.ollamassist.chat.service.OllamaService;
import fr.baretto.ollamassist.component.PromptPanel;
import fr.baretto.ollamassist.component.WorkspaceFileSelector;
import fr.baretto.ollamassist.events.ModelAvailableNotifier;
import fr.baretto.ollamassist.events.NewUserMessageNotifier;
import fr.baretto.ollamassist.prerequiste.PrerequisitesPanel;
import fr.baretto.ollamassist.setting.SettingsListener;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Slf4j
public class OllamaContent {

    private final Context context;
    @Getter
    private final JPanel contentPanel = new JPanel();
    private final PromptPanel promptInput = new PromptPanel();
    private final WorkspaceFileSelector filesSelector;
    private final MessagesPanel outputPanel = new MessagesPanel();
    private final PrerequisitesPanel prerequisitesPanel;
    private final AskToChatAction askToChatAction;
    private boolean isAvailable = false;
    private ChatThread currentChatThread;


    public OllamaContent(@NotNull ToolWindow toolWindow) {
        this.context = new Context(toolWindow.getProject());
        filesSelector = new WorkspaceFileSelector(toolWindow.getProject());
        prerequisitesPanel = new PrerequisitesPanel(toolWindow.getProject());
        askToChatAction = new AskToChatAction(promptInput, context);
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
        // Panel pour la saisie de prompt
        JBScrollPane scrollPane = new JBScrollPane(promptInput);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        // Panneau des fichiers repliable
        JPanel filePanel = createCollapsiblePanel("Fichiers", new WorkspaceFileSelector(context.project()));

        // On place les deux dans un JSplitPane vertical
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, filePanel, scrollPane);
        splitPane.setResizeWeight(0.0); // priorité à la zone du bas (prompt)
        splitPane.setOneTouchExpandable(false); // ou true si tu veux les flèches
        splitPane.setBorder(null);

        // Taille initiale raisonnable
        splitPane.setDividerLocation(120);

        // 🔁 Callback quand on replie le panneau fichiers
        AbstractButton toggleButton = (AbstractButton) ((JPanel) filePanel.getComponent(0)).getComponent(0);
        toggleButton.addActionListener(e -> {
            boolean collapsed = !filePanel.getComponent(1).isVisible();
            filePanel.getComponent(1).setVisible(collapsed); // toggle visibility

            // Si fermé, donne toute la hauteur au prompt
            SwingUtilities.invokeLater(() -> {
                if (!collapsed) {
                    splitPane.setDividerLocation(0); // tout en bas = prompt prend tout
                } else {
                    splitPane.setDividerLocation(120); // valeur par défaut
                }
                filePanel.revalidate();
                splitPane.revalidate();
            });
        });

        return splitPane;
    }

    private JPanel createCollapsiblePanel(String title, WorkspaceFileSelector fileSelector) {
        JPanel panel = new JPanel(new BorderLayout());


        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.X_AXIS));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JButton toggleButton = new JButton("▼ " + title);
        toggleButton.setBorderPainted(false);
        toggleButton.setFocusPainted(false);
        toggleButton.setContentAreaFilled(false);
        toggleButton.setHorizontalAlignment(SwingConstants.LEFT);

        JButton addButton = new JButton("Add to context...");
        addButton.setIcon(UIManager.getIcon("FileChooser.newFolderIcon"));
        addButton.addActionListener(fileSelector::addFilesAction);

        JButton removeButton = new JButton("Supprimer");
        removeButton.setIcon(UIManager.getIcon("Tree.closedIcon"));
        removeButton.setEnabled(false);
        removeButton.addActionListener(fileSelector::removeFilesAction);

        fileSelector.getFileList().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                removeButton.setEnabled(fileSelector.getFileList().getSelectedIndex() != -1);
            }
        });

        headerPanel.add(toggleButton);
        headerPanel.add(Box.createHorizontalGlue());
        headerPanel.add(addButton);
        headerPanel.add(Box.createRigidArea(new Dimension(5, 0)));
        headerPanel.add(removeButton);


        JPanel contentPanel = new JPanel(new BorderLayout());
        JBScrollPane fileScrollPane = new JBScrollPane(fileSelector.getFileList());


        fileScrollPane.setPreferredSize(new Dimension(Integer.MAX_VALUE, 120));
        fileScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        fileScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        contentPanel.add(fileScrollPane, BorderLayout.CENTER);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(contentPanel, BorderLayout.CENTER);


        final boolean[] isCollapsed = {false};

        toggleButton.addActionListener(e -> {
            isCollapsed[0] = !isCollapsed[0];
            contentPanel.setVisible(!isCollapsed[0]);
            toggleButton.setText((isCollapsed[0] ? "► " : "▼ ") + title);


            if (isCollapsed[0]) {
                fileScrollPane.setPreferredSize(new Dimension(Integer.MAX_VALUE, 0));
            } else {
                fileScrollPane.setPreferredSize(new Dimension(Integer.MAX_VALUE, 120));
            }

            SwingUtilities.invokeLater(() -> {
                panel.revalidate();
                panel.repaint();
                Container parent = panel.getParent();
                if (parent != null) {
                    parent.revalidate();
                    parent.repaint();
                }
            });
        });

        return panel;
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

}