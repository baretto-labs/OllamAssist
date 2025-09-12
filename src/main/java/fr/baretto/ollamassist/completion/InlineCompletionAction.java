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

    private EnhancedCompletionService enhancedCompletionService;
    private final MultiSuggestionManager multiSuggestionManager;

    public InlineCompletionAction() {
        this.multiSuggestionManager = new MultiSuggestionManager();
        // Enhanced completion service will be initialized per project
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        System.err.println("🚀 [ERROR-DEBUG] InlineCompletionAction.actionPerformed() called!");
        Project project = e.getProject();
        if (project == null) {
            System.err.println("❌ [ERROR-DEBUG] No project found!");
            return;
        }

        Editor editor = getActiveEditor();
        if (editor == null) {
            System.err.println("❌ [ERROR-DEBUG] No active editor found!");
            return;
        }
        System.err.println("✅ [ERROR-DEBUG] Found editor: " + editor.getClass().getSimpleName());
        
        // Initialize enhanced completion service for this project if not already done
        if (enhancedCompletionService == null || shouldReinitializeForProject(project)) {
            EnhancedContextProvider contextProvider = new EnhancedContextProvider(project);
            enhancedCompletionService = new EnhancedCompletionService(multiSuggestionManager, contextProvider);
        }
        
        // Attach enhanced key listener for Tab navigation
        editor.getContentComponent().addKeyListener(
            new EnhancedSuggestionKeyListener(multiSuggestionManager, editor)
        );
        
        // Request completion with all optimizations
        System.err.println("🎯 [ERROR-DEBUG] About to call enhancedCompletionService.requestCompletion()");
        enhancedCompletionService.requestCompletion(editor);
        System.err.println("✅ [ERROR-DEBUG] enhancedCompletionService.requestCompletion() called successfully!");
    }
    
    /**
     * Check if we need to reinitialize the service for a different project.
     */
    private boolean shouldReinitializeForProject(Project project) {
        // For simplicity, always reinitialize. In production, you might want to cache per project.
        return true;
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