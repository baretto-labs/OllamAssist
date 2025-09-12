package fr.baretto.ollamassist.completion;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@Slf4j
public class LightModelService {
    private final SuggestionManager suggestionManager;
    private final EnhancedContextProvider contextProvider;

    public LightModelService(SuggestionManager suggestionManager) {
        this.suggestionManager = suggestionManager;
        this.contextProvider = null; // Will be initialized with project
    }
    
    public LightModelService(SuggestionManager suggestionManager, EnhancedContextProvider contextProvider) {
        this.suggestionManager = suggestionManager;
        this.contextProvider = contextProvider;
    }

    public void handleSuggestion(Editor editor) {
        ApplicationManager.getApplication().invokeLater(() -> {
            // Show immediate loading feedback in the editor
            int caretOffset = editor.getCaretModel().getOffset();
            suggestionManager.showLoading(editor, caretOffset, "Generating suggestion");
            
            new Task.Backgroundable(editor.getProject(), "OllamAssist: Generating intelligent suggestion...", true) {

                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    try {
                        // Use enhanced context if available, otherwise fallback to basic context
                        if (contextProvider != null) {
                            handleEnhancedSuggestion(editor, indicator);
                        } else {
                            handleBasicSuggestion(editor, indicator);
                        }
                    } catch (Exception e) {
                        log.warn("Error generating suggestion, falling back to basic mode", e);
                        handleBasicSuggestion(editor, indicator);
                    }
                }
                
                @Override
                public void onCancel() {
                    // Clean up loading indicator if user cancels
                    ApplicationManager.getApplication().invokeLater(() -> {
                        suggestionManager.disposeLoadingInlay();
                    });
                }
                
                @Override
                public void onThrowable(@NotNull Throwable error) {
                    // Clean up loading indicator on error
                    ApplicationManager.getApplication().invokeLater(() -> {
                        suggestionManager.disposeLoadingInlay();
                    });
                    log.error("Suggestion generation failed", error);
                }
            }.queue();
        });
    }
    
    /**
     * Handles suggestion generation with enhanced context (RAG + project analysis).
     */
    private void handleEnhancedSuggestion(Editor editor, ProgressIndicator indicator) {
        indicator.setText("Building enhanced context...");
        
        // Build completion context asynchronously
        CompletableFuture<CompletionContext> contextFuture = contextProvider.buildCompletionContextAsync(editor);
        
        contextFuture.thenAccept(completionContext -> {
            if (indicator.isCanceled()) {
                return;
            }
            
            indicator.setText("Generating AI suggestion...");
            
            try {
                String lineStartContent = getLineStartContent(editor).trim();
                
                // Use enhanced completion with rich context
                String rawSuggestion = LightModelAssistant.get().complete(
                    completionContext.getImmediateContext(),
                    completionContext.getFileExtension(),
                    completionContext.getProjectContext(),
                    completionContext.getSimilarPatterns()
                );
                
                String suggestion = extractCode(rawSuggestion, lineStartContent);
                
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!indicator.isCanceled()) {
                        suggestionManager.showSuggestion(editor, editor.getCaretModel().getOffset(), suggestion);
                        attachKeyListener(editor, editor.getCaretModel().getOffset());
                    }
                });
                
            } catch (Exception e) {
                log.warn("Enhanced suggestion failed, trying basic fallback", e);
                handleBasicSuggestion(editor, indicator);
            }
            
        }).exceptionally(throwable -> {
            log.warn("Context building failed, using basic suggestion", throwable);
            handleBasicSuggestion(editor, indicator);
            return null;
        });
    }
    
    /**
     * Handles basic suggestion generation (original implementation).
     */
    private void handleBasicSuggestion(Editor editor, ProgressIndicator indicator) {
        Document document = editor.getDocument();
        SelectionModel selectionModel = editor.getSelectionModel();

        String context;
        int caretOffset = editor.getCaretModel().getOffset();

        if (selectionModel.hasSelection()) {
            context = selectionModel.getSelectedText();
        } else {
            int startOffset = Math.max(0, caretOffset - 200);
            int endOffset = Math.min(document.getTextLength(), caretOffset);
            context = document.getText().substring(startOffset, endOffset);
        }
        
        try {
            indicator.setText("Generating basic suggestion...");
            
            String lineStartContent = getLineStartContent(editor).trim();
            String rawSuggestion = LightModelAssistant.get().completeBasic(context, getFileExtension(editor));
            String suggestion = extractCode(rawSuggestion, lineStartContent);
            
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!indicator.isCanceled()) {
                    suggestionManager.showSuggestion(editor, editor.getCaretModel().getOffset(), suggestion);
                    attachKeyListener(editor, editor.getCaretModel().getOffset());
                }
            });
            
        } catch (Exception e) {
            log.error("Basic suggestion generation failed", e);
            // Even basic suggestion failed - show error or do nothing
        }
    }


    public String getFileExtension(Editor editor) {
        try {
            FileEditorManager fileEditorManager = FileEditorManager.getInstance(editor.getProject());
            VirtualFile file = fileEditorManager.getSelectedFiles().length > 0 ? fileEditorManager.getSelectedFiles()[0] : null;

            if (file != null) {
                return file.getExtension();
            }
            return "";
        } catch (Exception e) {
            return "";
        }

    }

    public String extractCode(String code, String snippet) {
        if (code.contains("```")) {
            code = removeAfterSecondBackticks(code);
            code = code.replaceAll("```\\w+\\s*", "")
                    .replace("```", "")
                    .trim();
        }
        if (code.contains(snippet)) {
            return code.substring(code.indexOf(snippet) + snippet.length(), code.length() - 3);
        }
        return code;
    }

    public String removeAfterSecondBackticks(String input) {
        int firstIndex = input.indexOf("```");
        if (firstIndex != -1) {
            int secondIndex = input.indexOf("```", firstIndex + 3);
            if (secondIndex != -1) {
                return input.substring(0, secondIndex + 3);
            }
        }
        return input;
    }


    private String getLineStartContent(Editor editor) {
        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            Document document = editor.getDocument();

            int offset = editor.getCaretModel().getOffset();
            int lineStartOffset = document.getLineStartOffset(document.getLineNumber(offset));
            return document.getText().substring(lineStartOffset, offset);
        });
    }

    private void attachKeyListener(Editor editor, int offset) {
        editor.getContentComponent().addKeyListener(new SuggestionKeyListener(suggestionManager, editor));

        ApplicationManager.getApplication().invokeLater(() -> {
            EditorActionManager actionManager = EditorActionManager.getInstance();
            EditorActionHandler originalEnterHandler = actionManager.getActionHandler("EditorEnter");

            SuggestionEnterAction enterAction = new SuggestionEnterAction(suggestionManager, offset, originalEnterHandler);
            actionManager.setActionHandler("EditorEnter", enterAction.getHandler());
        });

    }

}