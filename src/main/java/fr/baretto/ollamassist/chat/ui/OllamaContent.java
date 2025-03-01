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
    private final MessagesPanel outputPanel = new MessagesPanel();
    private final PrerequisitesPanel prerequisitesPanel;
    private final AskToChatAction askToChatAction;
    private boolean isAvailable = false;
    private ChatThread currentChatThread;

    public OllamaContent(@NotNull ToolWindow toolWindow) {
        this.context = new Context(toolWindow.getProject());
        prerequisitesPanel = new PrerequisitesPanel(toolWindow.getProject());
        askToChatAction = new AskToChatAction(promptInput, context);
        promptInput.addActionMap(askToChatAction);
        outputPanel.addContexte(context);
        contentPanel.add(prerequisitesPanel);

        MessageBusConnection connection = context.project().getMessageBus()
                .connect();

        subscribe(connection);

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

        connection
                .subscribe(SettingsListener.TOPIC, (SettingsListener) newState -> context.project()
                        .getService(OllamaService.class)
                        .forceInit());

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
        });


    }

    private void initUI() {
        this.isAvailable = true;
        contentPanel.setLayout(new BorderLayout());
        contentPanel.add(createConversationPanel(), BorderLayout.NORTH);
        contentPanel.add(createSplitter(), BorderLayout.CENTER);
    }

    private JPanel createSplitter() {
        OnePixelSplitter splitter = new OnePixelSplitter(true, 0.75f);
        splitter.setFirstComponent(outputPanel);
        splitter.setSecondComponent(createInputPanel());
        splitter.setHonorComponentsMinimumSize(true);
        return splitter;
    }

    private JComponent createInputPanel() {
        JPanel submitPanel = new JPanel(new BorderLayout());
        submitPanel.setMinimumSize(new Dimension(Integer.MAX_VALUE, 100));
        submitPanel.setPreferredSize(new Dimension(Integer.MAX_VALUE, 100));
        JPanel promptsPanel = new JPanel();//@TODO add context file management
        submitPanel.add(promptsPanel, BorderLayout.NORTH);
        JBScrollPane scrollPane = new JBScrollPane(promptInput);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER); // Pas de scrollbar horizontale
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER); // Pas de scrollbar verticale
        submitPanel.add(scrollPane, BorderLayout.CENTER);
        return submitPanel;
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
                tokenStream.onNext(stoppable(onNext));
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
    }

    private void publish(String token) {
        outputPanel.appendToken(token);
    }

}