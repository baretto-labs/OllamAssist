package fr.baretto.ollamassist.chat.askfromcode;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import fr.baretto.ollamassist.component.PromptPanel;
import fr.baretto.ollamassist.events.NewUserMessageNotifier;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class AskFromCodeAction implements ActionListener {
    private final Editor editor;
    private final String selectedText;
    private PromptPanel promptPanel;


    public AskFromCodeAction(Editor editor, PromptPanel panel, String selectedText) {
        this.editor = editor;
        this.promptPanel = panel;
        this.selectedText = selectedText;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(editor.getProject()).getToolWindow("OllamAssist");
        if (toolWindow != null) {
            toolWindow.show();
        }
        String userMessage = promptPanel.getUserPrompt();

        editor.getSelectionModel().removeSelection();
        SelectionGutterIcon.removeGutterIcon(editor);
        promptPanel.setVisible(false);

        editor.getProject().getMessageBus()
                .syncPublisher(NewUserMessageNotifier.TOPIC)
                .newUserMessage(userMessage
                        .concat(": ")
                        .concat(selectedText));
    }
}
