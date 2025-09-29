package fr.baretto.ollamassist.chat.ui;

import fr.baretto.ollamassist.component.PromptPanel;
import fr.baretto.ollamassist.events.NewUserMessageNotifier;
import lombok.extern.slf4j.Slf4j;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@Slf4j
public class AskToChatAction implements ActionListener {
    private final PromptPanel promptPanel;
    private final Context context;

    public AskToChatAction(PromptPanel promptInput, Context context) {
        this.promptPanel = promptInput;
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String userMessage = promptPanel.getUserPrompt();
        if (userMessage.isEmpty()) {
            return;
        }

        // Vider le prompt immédiatement après soumission
        promptPanel.clearUserPrompt();

        // Activer l'état "génération en cours" (désactive le prompt et montre le bouton stop)
        promptPanel.toggleGenerationState(true);

        // Publier le message pour traitement par l'agent
        context.project().getMessageBus()
                .syncPublisher(NewUserMessageNotifier.TOPIC)
                .newUserMessage(userMessage);
    }
}