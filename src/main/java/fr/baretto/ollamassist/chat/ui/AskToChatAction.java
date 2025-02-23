package fr.baretto.ollamassist.chat.ui;

import com.intellij.openapi.project.Project;
import dev.langchain4j.model.chat.response.ChatResponse;
import fr.baretto.ollamassist.chat.service.OllamaService;
import fr.baretto.ollamassist.component.PromptPanel;
import fr.baretto.ollamassist.events.NewUserMessageNotifier;
import lombok.extern.slf4j.Slf4j;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@Slf4j
public class AskToChatAction implements ActionListener {
    private final PromptPanel promptPanel;
    private final Project project;

    public AskToChatAction(PromptPanel promptInput, Project project) {
        this.promptPanel = promptInput;
        this.project = project;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String userMessage = promptPanel.getUserPrompt();
        project.getMessageBus().syncPublisher(NewUserMessageNotifier.TOPIC).newUserMessage(userMessage);
    }
}
