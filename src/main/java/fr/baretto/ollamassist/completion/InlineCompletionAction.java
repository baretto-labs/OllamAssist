package fr.baretto.ollamassist.completion;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;


public class InlineCompletionAction extends AnAction {

    private final AICompletionService aiCompletionService;

    public InlineCompletionAction() {
        this.aiCompletionService = new AICompletionService(new SuggestionManager());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        Editor editor = getActiveEditor();
        if (editor == null) {
            return;
        }
        ApplicationManager.getApplication()
                .invokeLater(() -> aiCompletionService.handleSuggestion(editor));
    }

    private Editor getActiveEditor() {
        Editor[] editors = EditorFactory.getInstance().getAllEditors();
        for (Editor editor : editors) {
            if (editor.getContentComponent().isFocusOwner()) {
                return editor;
            }
        }
        return null;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        e.getPresentation().setEnabledAndVisible(project != null && getActiveEditor() != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}