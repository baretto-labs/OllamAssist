package fr.baretto.ollamassist.completion;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import fr.baretto.ollamassist.setting.ActionsSettings;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;


@Slf4j
public class InlineCompletionAction extends AnAction {

    /**
     * Marks an editor whose key listener is already attached. Without it a listener was added
     * on every invocation and never removed, so the editor accumulated one listener per
     * completion request for the whole session.
     */
    private static final Key<Boolean> KEY_LISTENER_ATTACHED =
            Key.create("ollamassist.completion.keyListenerAttached");

    private EnhancedCompletionService enhancedCompletionService;
    private final MultiSuggestionManager multiSuggestionManager;

    public InlineCompletionAction() {
        this.multiSuggestionManager = new MultiSuggestionManager();
        // Enhanced completion service will be initialized per project
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        log.debug("InlineCompletionAction.actionPerformed() called");
        if (!isCodeCompletionEnabled()) {
            log.debug("Inline code completion is disabled in settings");
            return;
        }
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

        // Attach the navigation key listener once per editor, not once per request.
        if (editor.getUserData(KEY_LISTENER_ATTACHED) == null) {
            editor.getContentComponent().addKeyListener(
                    new EnhancedSuggestionKeyListener(multiSuggestionManager, editor)
            );
            editor.putUserData(KEY_LISTENER_ATTACHED, Boolean.TRUE);
        }

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
        e.getPresentation().setEnabledAndVisible(
                isCodeCompletionEnabled() && project != null && getActiveEditor() != null);
    }

    /**
     * Whether the user wants inline suggestions at all.
     *
     * <p>Fail-safe: if the settings service cannot be reached, completion stays off rather
     * than interfering with typing — the whole point of this switch is that a developer who
     * does not want suggestions is never given any.
     */
    private static boolean isCodeCompletionEnabled() {
        try {
            ActionsSettings settings = ActionsSettings.getInstance();
            return settings != null && settings.isCodeCompletionEnabled();
        } catch (Exception e) {
            log.debug("Settings unavailable, treating code completion as disabled", e);
            return false;
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}