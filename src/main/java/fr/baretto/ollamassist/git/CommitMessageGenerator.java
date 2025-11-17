package fr.baretto.ollamassist.git;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CommitMessageI;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.ui.Refreshable;
import fr.baretto.ollamassist.chat.ui.IconUtils;
import fr.baretto.ollamassist.completion.LightModelAssistant;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

@Slf4j
public class CommitMessageGenerator extends AnAction {


    private static final Icon OLLAMASSIST_ICON = IconUtils.OLLAMASSIST_ICON;
    private static final Icon STOP_ICON = IconUtils.STOP;

    private volatile ProgressIndicator currentIndicator = null;

    public CommitMessageGenerator() {
        super(OLLAMASSIST_ICON);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        if (handleCancellationRequest(e)) {
            return;
        }
        startCommitMessageGenerationTask(e);
    }

    private boolean handleCancellationRequest(AnActionEvent e) {
        if (currentIndicator != null) {
            log.debug("Requesting cancellation of current commit message generation task");
            currentIndicator.cancel();
            resetToDefaultState(e);
            return true;
        }
        return false;
    }

    private void startCommitMessageGenerationTask(AnActionEvent e) {
        new Task.Backgroundable(getEventProject(e), "Analyzing changes to prepare commit message…", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                executeCommitMessageGeneration(e, indicator);
            }

            @Override
            public void onCancel() {
                handleTaskCancellation(e);
            }
        }.queue();
    }

    private void executeCommitMessageGeneration(AnActionEvent e, ProgressIndicator indicator) {
        currentIndicator = indicator;

        try {
            updateIconToStopButton(e);

            Project project = e.getProject();
            if (project == null || isCancelledWithLog(indicator, "before starting")) {
                return;
            }

            CommitMessageI commitPanel = getVcsPanel(e);
            if (commitPanel != null) {
                generateAndSetCommitMessage(e, indicator, project, commitPanel);
            }
        } catch (Exception exception) {
            handleException(indicator, exception);
        } finally {
            resetToDefaultState(e);
        }
    }

    private void generateAndSetCommitMessage(AnActionEvent e, ProgressIndicator indicator, Project project, CommitMessageI commitPanel) {
        if (isCancelledWithLog(indicator, "before message generation")) {
            return;
        }

        String commitMessage = generateCommitMessage(project, e);

        if (shouldSetCommitMessage(indicator, commitMessage)) {
            ApplicationManager.getApplication().invokeLater(() ->
                    commitPanel.setCommitMessage(commitMessage)
            );
        }
    }

    private boolean isCancelledWithLog(ProgressIndicator indicator, String stage) {
        if (indicator.isCanceled()) {
            log.debug("Commit message generation was cancelled {}", stage);
            return true;
        }
        return false;
    }

    private boolean shouldSetCommitMessage(ProgressIndicator indicator, String commitMessage) {
        return !indicator.isCanceled() && commitMessage != null && !commitMessage.trim().isEmpty();
    }

    private void updateIconToStopButton(AnActionEvent e) {
        ApplicationManager.getApplication().invokeLater(() ->
                e.getPresentation().setIcon(STOP_ICON)
        );
    }

    private void resetToDefaultState(AnActionEvent e) {
        ApplicationManager.getApplication().invokeLater(() -> {
            e.getPresentation().setIcon(OLLAMASSIST_ICON);
            currentIndicator = null;
        });
    }

    private void handleException(ProgressIndicator indicator, Exception exception) {
        if (!indicator.isCanceled()) {
            log.error("Exception during commit message generation", exception);
        } else {
            log.debug("Commit message generation was cancelled");
        }
    }

    private void handleTaskCancellation(AnActionEvent e) {
        log.debug("Commit message generation task was cancelled");
        resetToDefaultState(e);
    }

    private CommitMessageI getVcsPanel(AnActionEvent e) {
        if (e == null) return null;

        DataContext context = e.getDataContext();
        Object data = Refreshable.PANEL_KEY.getData(context);
        if (data instanceof CommitMessageI commitMessageI) {
            return commitMessageI;
        }
        return VcsDataKeys.COMMIT_MESSAGE_CONTROL.getData(context);
    }


    public String generateCommitMessage(Project project, AnActionEvent e) {
        // Try to get selected changes from commit panel
        SelectedChanges selectedChanges = getSelectedChanges(e);

        Collection<Change> changes;
        Collection<FilePath> unversionedFiles;

        if (selectedChanges.hasSelection()) {
            // Use only selected changes
            changes = selectedChanges.changes();
            unversionedFiles = selectedChanges.unversionedFiles();
            log.debug("Using {} selected changes and {} unversioned files for commit message",
                    changes.size(), unversionedFiles.size());
        } else {
            // Fallback to all changes if no selection
            changes = ChangeListManager.getInstance(project).getAllChanges();
            unversionedFiles = ChangeListManager.getInstance(project).getUnversionedFilesPaths();
            log.debug("No selection found, using all {} changes and {} unversioned files for commit message",
                    changes.size(), unversionedFiles.size());
        }

        String gitDiff = DiffGenerator.getDiff(changes, java.util.List.copyOf(unversionedFiles));
        return MessageCleaner.clean(LightModelAssistant.get().writecommitMessage(gitDiff));
    }

    /**
     * Attempts to retrieve selected changes from the commit panel using reflection.
     * Returns selected changes if available, otherwise returns empty selection.
     */
    private SelectedChanges getSelectedChanges(AnActionEvent e) {
        if (e == null) {
            return SelectedChanges.empty();
        }

        DataContext context = e.getDataContext();

        // Try different strategies using reflection to access internal APIs
        try {
            // Strategy 1: Try COMMIT_WORKFLOW_HANDLER
            Object workflowHandler = VcsDataKeys.COMMIT_WORKFLOW_HANDLER.getData(context);
            if (workflowHandler != null) {
                SelectedChanges result = tryGetChangesViaReflection(workflowHandler, "workflow handler");
                if (result.hasSelection()) {
                    return result;
                }
            }

            // Strategy 2: Try Refreshable.PANEL_KEY  
            Object panel = Refreshable.PANEL_KEY.getData(context);
            if (panel != null) {
                SelectedChanges result = tryGetChangesViaReflection(panel, "panel");
                if (result.hasSelection()) {
                    return result;
                }
            }

        } catch (Exception ex) {
            log.debug("Failed to get selected changes via reflection", ex);
        }

        log.debug("No selected changes found, will use all changes");
        return SelectedChanges.empty();
    }

    /**
     * Attempts to extract changes from an object using reflection
     */
    SelectedChanges tryGetChangesViaReflection(Object source, String sourceName) {
        try {
            Class<?> clazz = source.getClass();

            Collection<Change> changes = extractChanges(clazz, source, sourceName);
            Collection<FilePath> unversionedFiles = extractUnversionedFiles(clazz, source, sourceName);

            return createSelectedChangesIfFound(changes, unversionedFiles, sourceName);

        } catch (Exception ex) {
            log.debug("Reflection failed for {}", sourceName, ex);
        }

        return SelectedChanges.empty();
    }

    private Collection<Change> extractChanges(Class<?> clazz, Object source, String sourceName) {
        String[] changeMethods = {"getIncludedChanges", "getSelectedChanges", "getAllChanges"};

        for (String methodName : changeMethods) {
            Collection<Change> result = tryInvokeMethod(clazz, source, methodName);
            if (result != null) {
                log.debug("Found changes using {}.{}: {} items", sourceName, methodName, result.size());
                return result;
            }
        }
        return null;
    }

    private Collection<FilePath> extractUnversionedFiles(Class<?> clazz, Object source, String sourceName) {
        String[] unversionedMethods = {"getIncludedUnversionedFiles", "getUnversionedFiles"};

        for (String methodName : unversionedMethods) {
            Collection<FilePath> result = tryInvokeMethod(clazz, source, methodName);
            if (result != null) {
                log.debug("Found unversioned files using {}.{}: {} items", sourceName, methodName, result.size());
                return result;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> Collection<T> tryInvokeMethod(Class<?> clazz, Object source, String methodName) {
        try {
            var method = clazz.getMethod(methodName);
            Object result = method.invoke(source);
            if (result instanceof Collection<?> collection) {
                return (Collection<T>) collection;
            }
        } catch (Exception ignored) {
            // Try next method
        }
        return null;
    }

    private SelectedChanges createSelectedChangesIfFound(Collection<Change> changes, Collection<FilePath> unversionedFiles, String sourceName) {
        if ((changes != null && !changes.isEmpty()) || (unversionedFiles != null && !unversionedFiles.isEmpty())) {
            Collection<Change> finalChanges = changes != null ? changes : java.util.Collections.emptyList();
            Collection<FilePath> finalUnversioned = unversionedFiles != null ? unversionedFiles : java.util.Collections.emptyList();

            log.debug("Successfully extracted selection from {}: {} changes, {} unversioned files",
                    sourceName, finalChanges.size(), finalUnversioned.size());
            return new SelectedChanges(finalChanges, finalUnversioned, true);
        }
        return SelectedChanges.empty();
    }

    /**
     * Container for selected changes and unversioned files
     */
    public record SelectedChanges(
            Collection<Change> changes,
            Collection<FilePath> unversionedFiles,
            boolean hasSelection
    ) {
        static SelectedChanges empty() {
            return new SelectedChanges(java.util.Collections.emptyList(), java.util.Collections.emptyList(), false);
        }
    }

}