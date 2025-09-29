package fr.baretto.ollamassist.core.agent.execution;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Exécuteur pour les opérations Git
 */
@Slf4j
public class GitOperationExecutor implements ExecutionEngine.TaskExecutor {

    private final Project project;

    public GitOperationExecutor(Project project) {
        this.project = project;
    }

    @Override
    public TaskResult execute(Task task) {
        log.debug("Executing git operation task: {}", task.getId());

        try {
            String operation = task.getParameter("operation", String.class);
            if (operation == null) {
                return TaskResult.failure("Paramètre 'operation' manquant");
            }

            GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
            VirtualFile projectRoot = project.getBaseDir();
            if (projectRoot == null) {
                return TaskResult.failure("Impossible de déterminer le répertoire racine du projet");
            }

            GitRepository repository = repositoryManager.getRepositoryForRoot(projectRoot);
            if (repository == null) {
                return TaskResult.failure("Ce projet n'est pas un dépôt Git");
            }

            return switch (operation.toLowerCase()) {
                case "add" -> addFiles(task, repository);
                case "commit" -> commitChanges(task, repository);
                case "push" -> pushChanges(repository);
                case "pull" -> pullChanges(repository);
                case "status" -> getStatus(repository);
                default -> TaskResult.failure("Opération Git non supportée: " + operation);
            };

        } catch (Exception e) {
            log.error("Error in git operation execution", e);
            return TaskResult.failure("Erreur lors de l'opération Git", e);
        }
    }

    private TaskResult addFiles(Task task, GitRepository repository) {
        try {
            List<String> files = task.getParameter("files", List.class);
            if (files == null || files.isEmpty()) {
                // Add all changed files
                ChangeListManager changeListManager = ChangeListManager.getInstance(project);
                Git git = Git.getInstance();
                GitLineHandler handler = new GitLineHandler(project, repository.getRoot(), GitCommand.ADD);
                handler.addParameters(".");
                git.runCommand(handler);
                return TaskResult.success("Tous les fichiers modifiés ont été ajoutés au staging");
            } else {
                Git git = Git.getInstance();
                GitLineHandler handler = new GitLineHandler(project, repository.getRoot(), GitCommand.ADD);
                handler.addParameters(files.toArray(new String[0]));
                git.runCommand(handler);
                return TaskResult.success("Fichiers ajoutés au staging: " + String.join(", ", files));
            }
        } catch (Exception e) {
            log.error("Error adding files to git", e);
            return TaskResult.failure("Erreur lors de l'ajout des fichiers: " + e.getMessage());
        }
    }

    private TaskResult commitChanges(Task task, GitRepository repository) {
        try {
            String message = task.getParameter("message", String.class);
            if (message == null || message.trim().isEmpty()) {
                return TaskResult.failure("Paramètre 'message' manquant pour le commit");
            }

            Git git = Git.getInstance();
            GitLineHandler handler = new GitLineHandler(project, repository.getRoot(), GitCommand.COMMIT);
            handler.addParameters("-m", message);
            git.runCommand(handler);

            return TaskResult.success("Commit créé avec le message: " + message);
        } catch (Exception e) {
            log.error("Error committing changes", e);
            return TaskResult.failure("Erreur lors du commit: " + e.getMessage());
        }
    }

    private TaskResult pushChanges(GitRepository repository) {
        try {
            Git git = Git.getInstance();
            GitLineHandler handler = new GitLineHandler(project, repository.getRoot(), GitCommand.PUSH);
            git.runCommand(handler);

            return TaskResult.success("Changes pushed to remote repository");
        } catch (Exception e) {
            log.error("Error pushing changes", e);
            return TaskResult.failure("Erreur lors du push: " + e.getMessage());
        }
    }

    private TaskResult pullChanges(GitRepository repository) {
        try {
            Git git = Git.getInstance();
            GitLineHandler handler = new GitLineHandler(project, repository.getRoot(), GitCommand.PULL);
            git.runCommand(handler);

            return TaskResult.success("Changes pulled from remote repository");
        } catch (Exception e) {
            log.error("Error pulling changes", e);
            return TaskResult.failure("Erreur lors du pull: " + e.getMessage());
        }
    }

    private TaskResult getStatus(GitRepository repository) {
        try {
            Git git = Git.getInstance();
            GitLineHandler handler = new GitLineHandler(project, repository.getRoot(), GitCommand.STATUS);
            handler.addParameters("--porcelain");
            var result = git.runCommand(handler);

            if (result.success()) {
                String status = String.join("\n", result.getOutput());
                return TaskResult.success("Git status:\n" + status);
            } else {
                return TaskResult.failure("Erreur lors de la récupération du status Git");
            }
        } catch (Exception e) {
            log.error("Error getting git status", e);
            return TaskResult.failure("Erreur lors de la récupération du status: " + e.getMessage());
        }
    }

    @Override
    public boolean canExecute(Task task) {
        return task.getType() == Task.TaskType.GIT_OPERATION;
    }

    @Override
    public String getExecutorName() {
        return "GitOperationExecutor";
    }
}