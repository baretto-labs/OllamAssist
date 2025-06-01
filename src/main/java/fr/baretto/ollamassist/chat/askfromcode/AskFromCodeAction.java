package fr.baretto.ollamassist.chat.askfromcode;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import fr.baretto.ollamassist.component.PromptPanel;
import fr.baretto.ollamassist.events.NewUserMessageNotifier;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class AskFromCodeAction implements ActionListener {
    private Editor editor;
    private PromptPanel promptPanel;


    public AskFromCodeAction(PromptPanel panel) {
        this.promptPanel = panel;
    }

    public void fromCodeEditor(Editor editor) {
        this.editor = editor;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(editor.getProject()).getToolWindow("OllamAssist");
        if (toolWindow != null) {
            toolWindow.show();
        }
        String userMessage = promptPanel.getUserPrompt();
        if (userMessage.isEmpty()) {
            return;
        }
        String selectedText = editor.getSelectionModel().getSelectedText();
        cleanPromptPanel();

        if (selectedText != null) {
            VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());

            userMessage = userMessage
                    .concat("```");

            if (file != null) {
                String extension = file.getExtension();
                if (extension != null) {
                    userMessage = userMessage.concat(extension);
                }
            }
            userMessage = userMessage
                    .concat("\n")
                    .concat(selectedText)
                    .concat("\n")
                    .concat("```");
            editor.getProject().getMessageBus()
                    .syncPublisher(NewUserMessageNotifier.TOPIC)
                    .newUserMessage(userMessage);
        }

    }

    private void cleanPromptPanel() {
        editor.getSelectionModel().removeSelection();
        editor.getProject().getService(SelectionGutterIcon.class).removeGutterIcon(editor);
        promptPanel.removeListeners();
        promptPanel.setVisible(false);
    }
}
