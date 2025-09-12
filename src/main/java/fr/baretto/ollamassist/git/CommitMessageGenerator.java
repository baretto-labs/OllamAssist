package fr.baretto.ollamassist.git;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CommitMessageI;
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

@Slf4j
public class CommitMessageGenerator extends AnAction {


    private static final Icon OLLAMASSIST_ICON = IconUtils.OLLAMASSIST_ICON;
    private static final Icon LOADING_ICON = IconUtils.OLLAMASSIST_THINKING_ICON;


    public CommitMessageGenerator() {
        super(OLLAMASSIST_ICON);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        new Task.Backgroundable(getEventProject(e), "Analyzing changes to prepare commit message…", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    e.getPresentation().setIcon(LOADING_ICON);
                    Project project = e.getProject();
                    if (project == null) return;

                    CommitMessageI commitPanel = getVcsPanel(e);

                    if (commitPanel != null) {
                        String commitMessage = generateCommitMessage(project);

                        ApplicationManager.getApplication().invokeLater(() ->
                                commitPanel.setCommitMessage(commitMessage)
                        );
                    }
                } catch (Exception exception) {
                    log.error("Exception during commit message generation", exception);
                } finally {
                    e.getPresentation().setIcon(OLLAMASSIST_ICON);
                }
            }
        }.queue();

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


    public String generateCommitMessage(Project project) {
        Collection<Change> changes = ChangeListManager.getInstance(project).getAllChanges();
        String gitDiff = DiffGenerator.getDiff(changes, ChangeListManager.getInstance(project).getUnversionedFilesPaths());
        return MessageCleaner.clean(LightModelAssistant.get().writecommitMessage(gitDiff));
    }

}