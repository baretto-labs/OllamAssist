package fr.baretto.ollamassist.actions.refactor;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayProperties;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.ui.JBColor;
import dev.langchain4j.service.TokenStream;
import fr.baretto.ollamassist.chat.service.OllamaService;
import fr.baretto.ollamassist.setting.PromptSettings;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.Objects;

@Slf4j
public class RefactorAction extends AnAction {

    private static final Key<Inlay<?>> INLAY_KEY = Key.create("OllamAssist.RefactorInlay");
    private static final Key<LineDiffHighlighter> HIGHLIGHTER_KEY = Key.create("OllamAssist.RefactorHighlighter");
    private static final Key<MouseAdapter> MOUSE_LISTENER_KEY = Key.create("OllamAssist.RefactorMouseListener");
    private static final Key<MouseMotionAdapter> MOUSE_MOTION_LISTENER_KEY = Key.create("OllamAssist.RefactorMouseMotionListener");

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        boolean isTextSelected = editor != null && editor.getSelectionModel().hasSelection();
        e.getPresentation().setEnabledAndVisible(project != null && isTextSelected);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (project == null || editor == null || psiFile == null) return;

        dismiss(editor);

        final TextRange originalSelection = new TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd());
        String selectedText = editor.getDocument().getText(originalSelection);

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "OllamAssist: Refactoring...", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    final String language = psiFile.getLanguage().getDisplayName();

                    StringBuilder responseBuilder = new StringBuilder();
                    java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

                    String refactorPrompt = PromptSettings.getInstance().getRefactorUserPrompt();
                    TokenStream stream = Objects.requireNonNull(e.getProject()).getService(OllamaService.class)
                            .getAssistant()
                            .refactor(refactorPrompt, selectedText, language);

                    stream.onPartialResponse(responseBuilder::append)
                            .onCompleteResponse(chatResponse -> latch.countDown())
                            .onError(error ->
                                    latch.countDown()
                            );
                    stream.start();
                    latch.await();

                    String refactoredCode = cleanLlmResponse(responseBuilder.toString());
                    if (refactoredCode.trim().isEmpty()) {
                        return;
                    }

                    ApplicationManager.getApplication().invokeLater(() ->
                            displayInlayUI(project, editor, refactoredCode, originalSelection)
                    );

                } catch (Exception ex) {
                    log.error("Exception during refactoring", ex);
                    Thread.currentThread().interrupt();
                }
            }
        });
    }


    private void displayInlayUI(@NotNull Project project, @NotNull Editor editor, @NotNull String refactoredCode, @NotNull TextRange originalSelection) {
        int offset = originalSelection.getStartOffset();

        LineDiffHighlighter highlighter = new LineDiffHighlighter(editor, originalSelection);
        highlighter.apply();
        editor.putUserData(HIGHLIGHTER_KEY, highlighter);

        RefactoringInlayRenderer newCodeRenderer = new RefactoringInlayRenderer(refactoredCode, new JBColor(new Color(220, 255, 220), new Color(45, 90, 45)));
        Inlay<?> newInlay = editor.getInlayModel().addBlockElement(offset - 1, new InlayProperties(), newCodeRenderer);
        editor.putUserData(INLAY_KEY, newInlay);

        MouseAdapter mouseListener = createMouseAdapter(project, editor, refactoredCode, originalSelection, newCodeRenderer);

        MouseMotionAdapter motionListener = createMouseMotionAdapter(editor, newCodeRenderer);

        editor.getContentComponent().addMouseListener(mouseListener);
        editor.getContentComponent().addMouseMotionListener(motionListener);
        editor.putUserData(MOUSE_LISTENER_KEY, mouseListener);
        editor.putUserData(MOUSE_MOTION_LISTENER_KEY, motionListener);
    }

    private static @NotNull MouseMotionAdapter createMouseMotionAdapter(@NotNull Editor editor, RefactoringInlayRenderer newCodeRenderer) {
        return new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                Inlay<?> inlay = editor.getUserData(INLAY_KEY);
                if (inlay == null || inlay.getBounds() == null) return;

                Point relativePoint = new Point(event.getX() - inlay.getBounds().x, event.getY() - inlay.getBounds().y);
                newCodeRenderer.setMousePosition(relativePoint);

                if ((newCodeRenderer.acceptBounds != null && newCodeRenderer.acceptBounds.contains(relativePoint)) ||
                        (newCodeRenderer.declineBounds != null && newCodeRenderer.declineBounds.contains(relativePoint))) {
                    editor.getContentComponent().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else {
                    editor.getContentComponent().setCursor(Cursor.getDefaultCursor());
                }
                inlay.repaint();
            }
        };
    }

    private @NotNull MouseAdapter createMouseAdapter(@NotNull Project project, @NotNull Editor editor, @NotNull String refactoredCode, @NotNull TextRange originalSelection, RefactoringInlayRenderer newCodeRenderer) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                Inlay<?> inlay = editor.getUserData(INLAY_KEY);
                if (inlay == null || inlay.getBounds() == null) return;

                Point relativePoint = new Point(event.getX() - inlay.getBounds().x, event.getY() - inlay.getBounds().y);
                if (newCodeRenderer.acceptBounds != null && newCodeRenderer.acceptBounds.contains(relativePoint)) {
                    applyChanges(project, editor, refactoredCode, originalSelection);
                    dismiss(editor);
                } else if (newCodeRenderer.declineBounds != null && newCodeRenderer.declineBounds.contains(relativePoint)) {
                    dismiss(editor);
                }
            }
        };
    }


    private void applyChanges(@NotNull Project project, @NotNull Editor editor, @NotNull String refactoredText, @NotNull TextRange range) {
        WriteCommandAction.runWriteCommandAction(project, "Apply OllamAssist Refactoring", null, () ->
                editor.getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), refactoredText)
        );
    }

    private void dismiss(@NotNull Editor editor) {
        MouseAdapter mouseListener = editor.getUserData(MOUSE_LISTENER_KEY);
        if (mouseListener != null) {
            editor.getContentComponent().removeMouseListener(mouseListener);
            editor.putUserData(MOUSE_LISTENER_KEY, null);
        }
        MouseMotionAdapter motionListener = editor.getUserData(MOUSE_MOTION_LISTENER_KEY);
        if (motionListener != null) {
            editor.getContentComponent().removeMouseMotionListener(motionListener);
            editor.putUserData(MOUSE_MOTION_LISTENER_KEY, null);
        }
        editor.getContentComponent().setCursor(Cursor.getDefaultCursor());

        LineDiffHighlighter highlighter = editor.getUserData(HIGHLIGHTER_KEY);
        if (highlighter != null) {
            highlighter.clear();
            editor.putUserData(HIGHLIGHTER_KEY, null);
        }

        Inlay<?> inlay = editor.getUserData(INLAY_KEY);
        if (inlay != null) {
            Disposer.dispose(inlay);
            editor.putUserData(INLAY_KEY, null);
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return super.getActionUpdateThread();
    }

    private static String cleanLlmResponse(String response) {
        if (response == null) return "";
        return response.replaceAll("(?s)```.*?\n(.*?)\n```", "$1").trim();
    }

}