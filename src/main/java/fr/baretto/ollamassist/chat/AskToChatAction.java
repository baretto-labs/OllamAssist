package fr.baretto.ollamassist.chat;

import com.intellij.openapi.application.ApplicationManager;
import fr.baretto.ollamassist.ai.OllamaService;
import fr.baretto.ollamassist.ai.store.LuceneEmbeddingStore;
import lombok.extern.slf4j.Slf4j;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@Slf4j
public class AskToChatAction implements ActionListener {
    private final PromptPanel promptPanel;
    private final MessagesPanel outputPanel;
    private final Context context;

    public AskToChatAction(PromptPanel promptInput, MessagesPanel outputPanel, Context context) {
        this.promptPanel = promptInput;
        this.outputPanel = outputPanel;
        this.context = context;
    }

    private static void logException(Throwable throwable) {
        log.error("Exception: " + throwable);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String userMessage = promptPanel.getUserPrompt();
        outputPanel.addUserMessage(userMessage);
        outputPanel.addNewAIMessage();

        new Thread(() -> ApplicationManager.getApplication()
                .getService(OllamaService.class)
                .getAssistant()
                .chat(userMessage)
                .onNext(this::publish)
                .onError(AskToChatAction::logException)
                .start()
        ).start();
        promptPanel.clear();
    }

    private void publish(String token) {
        outputPanel.appendToken(token);
    }
}
