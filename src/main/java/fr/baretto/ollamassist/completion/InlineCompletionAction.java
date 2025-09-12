package fr.baretto.ollamassist.completion;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;


@Slf4j
public class InlineCompletionAction extends AnAction {

    private EnhancedCompletionService enhancedCompletionService;
    private final MultiSuggestionManager multiSuggestionManager;

    public InlineCompletionAction() {
        this.multiSuggestionManager = new MultiSuggestionManager();
        // Enhanced completion service will be initialized per project
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        log.debug("InlineCompletionAction.actionPerformed() called");
        Project project = e.getProject();
        if (project == null) {
            log.debug("No project found");
            return;
        }

        Editor editor = getActiveEditor();
        if (editor == null) {
            log.debug("No active editor found");
            return;
        }
        log.debug("Found editor: {}", editor.getClass().getSimpleName());

        // Initialize enhanced completion service for this project if not already done
        if (enhancedCompletionService == null) {
            EnhancedContextProvider contextProvider = new EnhancedContextProvider(project);
            enhancedCompletionService = new EnhancedCompletionService(multiSuggestionManager, contextProvider);
        }

        // Attach enhanced key listener for Tab navigation
        editor.getContentComponent().addKeyListener(
                new EnhancedSuggestionKeyListener(multiSuggestionManager, editor)
        );

        // Request completion with all optimizations
        log.debug("About to call enhancedCompletionService.requestCompletion()");
        enhancedCompletionService.requestCompletion(editor);
        log.debug("enhancedCompletionService.requestCompletion() called successfully");
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