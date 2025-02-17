package fr.baretto.ollamassist.askfromcode;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import fr.baretto.ollamassist.chat.ui.ImageUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.HierarchyEvent;
import java.util.Map;
import java.util.WeakHashMap;

public class SelectionGutterIcon {
    private static final Map<Editor, Disposable> editorDisposables = new WeakHashMap<>();
    private static final Map<Editor, RangeHighlighter> activeHighlighters = new WeakHashMap<>();

    public static void addGutterIcon(@NotNull Editor editor, int startOffset) {
        MarkupModel markupModel = editor.getMarkupModel();
        int lineNumber = editor.getDocument().getLineNumber(startOffset);

        removeGutterIcon(editor);

        if (lineNumber < 0 || lineNumber >= editor.getDocument().getLineCount()) {
            return;
        }

        // Création d'un disposable parent pour cet éditeur
        Disposable parentDisposable = getOrCreateEditorDisposable(editor);

        RangeHighlighter highlighter = markupModel.addLineHighlighter(
                lineNumber,
                5000,
                new TextAttributes()
        );

        highlighter.setGutterIconRenderer(createIconRenderer(editor, lineNumber));
        activeHighlighters.put(editor, highlighter);

        // Lier le highlighter au disposable
        Disposer.register(parentDisposable, () -> {
            markupModel.removeHighlighter(highlighter);
            activeHighlighters.remove(editor);
        });

        setupListeners(editor, parentDisposable);
    }

    private static Disposable getOrCreateEditorDisposable(Editor editor) {
        return editorDisposables.computeIfAbsent(editor, k -> {
            Disposable disposable = Disposer.newDisposable("GutterIconCleanup");

            // Nettoyage quand l'éditeur est fermé
            Disposer.register(getProjectDisposable(editor), disposable);

            // Détection visuelle de fermeture
            editor.getComponent().addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) {
                    if (editor.getComponent().getParent() == null) {
                        Disposer.dispose(disposable);
                    }
                }
            });

            return disposable;
        });
    }

    private static Disposable getProjectDisposable(Editor editor) {
        Project project = editor.getProject();
        return project != null ? project : Disposer.newDisposable("app");
    }

    private static void setupListeners(Editor editor, Disposable parentDisposable) {
        SelectionListener selectionListener = new SelectionListener() {
            @Override
            public void selectionChanged(@NotNull SelectionEvent e) {
                if (e.getNewRange().getLength() == 0) {
                    removeGutterIcon(editor);
                }
            }
        };
        editor.getSelectionModel().addSelectionListener(selectionListener);
        Disposer.register(parentDisposable, () ->
                editor.getSelectionModel().removeSelectionListener(selectionListener)
        );

        // Listener de caret
        CaretListener caretListener = new CaretListener() {
            @Override
            public void caretPositionChanged(@NotNull CaretEvent event) {
                RangeHighlighter highlighter = activeHighlighters.get(editor);
                if (highlighter != null && event.getCaret() != null) {
                    int currentLine = editor.getDocument().getLineNumber(event.getCaret().getOffset());
                    int highlighterLine = editor.getDocument().getLineNumber(highlighter.getStartOffset());

                    if (currentLine != highlighterLine) {
                        removeGutterIcon(editor);
                    }
                }
            }
        };
        editor.getCaretModel().addCaretListener(caretListener);
        Disposer.register(parentDisposable, () ->
                editor.getCaretModel().removeCaretListener(caretListener)
        );
    }

    public static void removeGutterIcon(@NotNull Editor editor) {
        Disposable disposable = editorDisposables.get(editor);
        if (disposable != null && !Disposer.isDisposed(disposable)) {
            Disposer.dispose(disposable);
            editorDisposables.remove(editor);
        }
    }

    private static GutterIconRenderer createIconRenderer(Editor editor, int lineNumber) {
        return new GutterIconRenderer() {
            @Override
            public @NotNull Icon getIcon() {
                return ImageUtil.OLLAMASSIST_ICON;
            }

            @Override
            public AnAction getClickAction() {
                return new AnAction() {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        OverlayPromptPanel.showOverlayPromptPanel(editor, lineNumber);
                    }
                };
            }

            @Override
            public boolean equals(Object obj) {
                return obj instanceof GutterIconRenderer;
            }

            @Override
            public int hashCode() {
                return getIcon().hashCode();
            }
        };
    }
}