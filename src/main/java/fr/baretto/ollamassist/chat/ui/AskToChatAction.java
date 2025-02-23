package fr.baretto.ollamassist.chat.ui;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.TokenStream;
import fr.baretto.ollamassist.chat.service.OllamaService;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Slf4j
public class AskToChatAction implements ActionListener {
    private final PromptPanel promptPanel;
    private final MessagesPanel outputPanel;
    private final Context context;
    private ChatThread currentChatThread;

    public AskToChatAction(PromptPanel promptInput, MessagesPanel outputPanel, Context context) {
        this.promptPanel = promptInput;
        this.outputPanel = outputPanel;
        this.context = context;
    }

    private void logException(Throwable throwable) {
        log.error("Exception: " + throwable);
        done(ChatResponse.builder().finishReason(FinishReason.OTHER).aiMessage(AiMessage.from(throwable.getMessage())).build());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String userMessage = promptPanel.getUserPrompt();
        if (userMessage.isEmpty()) {
            return;
        }

        if (currentChatThread != null) {
            currentChatThread.stop();
        }
        outputPanel.cancelMessage();
        outputPanel.addUserMessage(userMessage);
        outputPanel.addNewAIMessage();

        currentChatThread = ChatThread.builder()
                .tokenStream(context.project()
                        .getService(OllamaService.class)
                        .getAssistant()
                        .chat(userMessage))
                .onNext(this::publish)
                .onError(this::logException)
                .onCompleteResponse(this::done)
                .build()
                .start();
        promptPanel.clear();
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

        private  void run() {
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

    private void done(ChatResponse chatResponse) {
        outputPanel.finalizeMessage(chatResponse);
    }

    private void publish(String token) {
        outputPanel.appendToken(token);
    }
}
