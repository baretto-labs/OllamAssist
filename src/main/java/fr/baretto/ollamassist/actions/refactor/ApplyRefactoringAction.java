package fr.baretto.ollamassist.actions.refactor;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ApplyRefactoringAction extends AnAction {

    private final Editor editor;
    private final String refactoredText;

    public ApplyRefactoringAction(@NotNull Editor editor, @NotNull String refactoredText) {
        super("Apply Refactoring", "Replace selection with the refactored code", AllIcons.General.Gear);
        this.editor = editor;
        this.refactoredText = refactoredText;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        

        WriteCommandAction.runWriteCommandAction(project, "Apply OllamAssist Refactoring", null, () -> {
            Document document = editor.getDocument();
            SelectionModel selectionModel = editor.getSelectionModel();

            if (selectionModel.hasSelection()) {
                int startOffset = selectionModel.getSelectionStart() - 1;
                int endOffset = selectionModel.getSelectionEnd();
                document.replaceString(startOffset, endOffset, refactoredText);
            }
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(true);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return super.getActionUpdateThread();
    }
}