package fr.baretto.ollamassist.core.agent.execution;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import fr.baretto.ollamassist.core.agent.rollback.ActionSnapshot;
import fr.baretto.ollamassist.core.agent.rollback.SnapshotCapable;
import fr.baretto.ollamassist.core.agent.rollback.SnapshotData;
import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Exécuteur pour les opérations sur fichiers
 */
@Slf4j
public class FileOperationExecutor implements ExecutionEngine.TaskExecutor, SnapshotCapable {

    private final Project project;

    public FileOperationExecutor(Project project) {
        this.project = project;
    }

    @Override
    public TaskResult execute(Task task) {
        log.debug("Executing file operation task: {}", task.getId());

        try {
            String operation = task.getParameter("operation", String.class);
            String filePath = task.getParameter("filePath", String.class);

            if (operation == null) {
                return TaskResult.failure("Paramètre 'operation' manquant");
            }

            if (filePath == null) {
                return TaskResult.failure("Paramètre 'filePath' manquant");
            }

            return switch (operation.toLowerCase()) {
                case "create" -> createFile(task, filePath);
                case "delete" -> deleteFile(filePath);
                case "move" -> moveFile(task, filePath);
                case "copy" -> copyFile(task, filePath);
                default -> TaskResult.failure("Opération non supportée: " + operation);
            };

        } catch (Exception e) {
            log.error("Error in file operation execution", e);
            return TaskResult.failure("Erreur lors de l'opération sur fichier", e);
        }
    }

    private TaskResult createFile(Task task, String filePath) {
        try {
            final String content = task.getParameter("content", String.class) != null
                    ? task.getParameter("content", String.class)
                    : "";

            log.error("FILE OPERATION: Creating file '{}' with content length: {}", filePath, content.length());

            VirtualFile projectRoot = project.getBaseDir();
            if (projectRoot == null) {
                log.error("PROJECT ROOT IS NULL!");
                return TaskResult.failure("Impossible de déterminer le répertoire racine du projet");
            }

            log.error("PROJECT ROOT: {}", projectRoot.getPath());

            // Exécuter dans un WriteCommandAction pour IntelliJ
            final Exception[] writingException = {null};

            WriteCommandAction.runWriteCommandAction(project, () -> {
                try {
                    Path targetPath = Paths.get(filePath);
                    log.error("TARGET PATH: {}", targetPath);

                    // Créer les répertoires parents si nécessaire
                    VirtualFile parentDir = projectRoot;
                    if (targetPath.getParent() != null) {
                        log.error("CREATING PARENT DIRS: {}", targetPath.getParent());
                        parentDir = VfsUtil.createDirectoryIfMissing(projectRoot, targetPath.getParent().toString());
                        log.error("PARENT DIR CREATED: {}", parentDir.getPath());
                    }

                    // Créer le fichier
                    String fileName = targetPath.getFileName().toString();
                    log.error("CREATING FILE: {} in {}", fileName, parentDir.getPath());

                    VirtualFile targetFile = parentDir.createChildData(this, fileName);
                    targetFile.setBinaryContent(content.getBytes());

                    log.error("FILE CREATED SUCCESSFULLY: {}", targetFile.getPath());
                    log.info("File created: {}", filePath);

                } catch (IOException e) {
                    log.error("IOException during file creation: {}", filePath, e);
                    writingException[0] = e;
                    throw new RuntimeException(e);
                } catch (Exception e) {
                    log.error("Unexpected error during file creation: {}", filePath, e);
                    writingException[0] = e;
                    throw new RuntimeException(e);
                }
            });

            if (writingException[0] != null) {
                return TaskResult.failure("Erreur lors de l'écriture: " + writingException[0].getMessage());
            }

            return TaskResult.success("Fichier créé avec succès: " + filePath);

        } catch (Exception e) {
            log.error("Error creating file: {}", filePath, e);
            return TaskResult.failure("Erreur lors de la création du fichier: " + e.getMessage());
        }
    }

    private TaskResult deleteFile(String filePath) {
        try {
            VirtualFile projectRoot = project.getBaseDir();
            if (projectRoot == null) {
                return TaskResult.failure("Impossible de déterminer le répertoire racine du projet");
            }

            VirtualFile fileToDelete = projectRoot.findFileByRelativePath(filePath);
            if (fileToDelete == null) {
                return TaskResult.failure("Fichier non trouvé: " + filePath);
            }

            WriteCommandAction.runWriteCommandAction(project, () -> {
                try {
                    fileToDelete.delete(this);
                    log.info("File deleted: {}", filePath);
                } catch (IOException e) {
                    log.error("Error deleting file: {}", filePath, e);
                    throw new RuntimeException(e);
                }
            });

            return TaskResult.success("Fichier supprimé avec succès: " + filePath);

        } catch (Exception e) {
            log.error("Error deleting file: {}", filePath, e);
            return TaskResult.failure("Erreur lors de la suppression du fichier: " + e.getMessage());
        }
    }

    private TaskResult moveFile(Task task, String filePath) {
        try {
            String targetPath = task.getParameter("targetPath", String.class);
            if (targetPath == null) {
                return TaskResult.failure("Paramètre 'targetPath' manquant pour l'opération move");
            }

            VirtualFile projectRoot = project.getBaseDir();
            if (projectRoot == null) {
                return TaskResult.failure("Impossible de déterminer le répertoire racine du projet");
            }

            VirtualFile sourceFile = projectRoot.findFileByRelativePath(filePath);
            if (sourceFile == null) {
                return TaskResult.failure("Fichier source non trouvé: " + filePath);
            }

            WriteCommandAction.runWriteCommandAction(project, () -> {
                try {
                    Path target = Paths.get(targetPath);
                    VirtualFile targetDir = VfsUtil.createDirectoryIfMissing(projectRoot, target.getParent().toString());
                    sourceFile.move(this, targetDir);
                    if (!sourceFile.getName().equals(target.getFileName().toString())) {
                        sourceFile.rename(this, target.getFileName().toString());
                    }
                    log.info("File moved from {} to {}", filePath, targetPath);
                } catch (IOException e) {
                    log.error("Error moving file from {} to {}", filePath, targetPath, e);
                    throw new RuntimeException(e);
                }
            });

            return TaskResult.success("Fichier déplacé avec succès de " + filePath + " vers " + targetPath);

        } catch (Exception e) {
            log.error("Error moving file from {} to {}", filePath, task.getParameter("targetPath", String.class), e);
            return TaskResult.failure("Erreur lors du déplacement du fichier: " + e.getMessage());
        }
    }

    private TaskResult copyFile(Task task, String filePath) {
        try {
            String targetPath = task.getParameter("targetPath", String.class);
            if (targetPath == null) {
                return TaskResult.failure("Paramètre 'targetPath' manquant pour l'opération copy");
            }

            VirtualFile projectRoot = project.getBaseDir();
            if (projectRoot == null) {
                return TaskResult.failure("Impossible de déterminer le répertoire racine du projet");
            }

            VirtualFile sourceFile = projectRoot.findFileByRelativePath(filePath);
            if (sourceFile == null) {
                return TaskResult.failure("Fichier source non trouvé: " + filePath);
            }

            WriteCommandAction.runWriteCommandAction(project, () -> {
                try {
                    Path target = Paths.get(targetPath);
                    VirtualFile targetDir = VfsUtil.createDirectoryIfMissing(projectRoot, target.getParent().toString());
                    sourceFile.copy(this, targetDir, target.getFileName().toString());
                    log.info("File copied from {} to {}", filePath, targetPath);
                } catch (IOException e) {
                    log.error("Error copying file from {} to {}", filePath, targetPath, e);
                    throw new RuntimeException(e);
                }
            });

            return TaskResult.success("Fichier copié avec succès de " + filePath + " vers " + targetPath);

        } catch (Exception e) {
            log.error("Error copying file from {} to {}", filePath, task.getParameter("targetPath", String.class), e);
            return TaskResult.failure("Erreur lors de la copie du fichier: " + e.getMessage());
        }
    }

    @Override
    public boolean canExecute(Task task) {
        return task.getType() == Task.TaskType.FILE_OPERATION;
    }

    @Override
    public String getExecutorName() {
        return "FileOperationExecutor";
    }

    // Implémentation SnapshotCapable

    @Override
    public ActionSnapshot captureBeforeSnapshot(Task task) {
        String operation = task.getParameter("operation", String.class);
        String filePath = task.getParameter("filePath", String.class);

        if (filePath == null) {
            return null;
        }

        try {
            VirtualFile projectRoot = project.getBaseDir();
            if (projectRoot == null) {
                return null;
            }

            VirtualFile file = projectRoot.findFileByRelativePath(filePath);
            SnapshotData beforeState;

            if (file != null && file.exists()) {
                // Fichier existe, capturer son contenu
                String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
                beforeState = SnapshotData.forFile(filePath, content);
            } else {
                // Fichier n'existe pas
                beforeState = SnapshotData.forDeletedFile(filePath);
            }

            ActionSnapshot.ActionType actionType = switch (operation.toLowerCase()) {
                case "create" -> ActionSnapshot.ActionType.FILE_CREATE;
                case "delete" -> ActionSnapshot.ActionType.FILE_DELETE;
                case "modify" -> ActionSnapshot.ActionType.FILE_MODIFY;
                case "move" -> ActionSnapshot.ActionType.FILE_MOVE;
                default -> ActionSnapshot.ActionType.FILE_MODIFY;
            };

            return ActionSnapshot.builder()
                    .actionId(task.getId() + "_" + System.currentTimeMillis())
                    .taskId(task.getId())
                    .actionType(actionType)
                    .beforeState(beforeState)
                    .timestamp(java.time.LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Erreur lors de la capture du snapshot avant: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public ActionSnapshot captureAfterSnapshot(Task task, ActionSnapshot beforeSnapshot) {
        if (beforeSnapshot == null) {
            return null;
        }

        String filePath = task.getParameter("filePath", String.class);
        String targetPath = task.getParameter("targetPath", String.class);

        try {
            VirtualFile projectRoot = project.getBaseDir();
            if (projectRoot == null) {
                return null;
            }

            SnapshotData afterState;
            String effectivePath = targetPath != null ? targetPath : filePath;

            VirtualFile file = projectRoot.findFileByRelativePath(effectivePath);
            if (file != null && file.exists()) {
                String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
                afterState = SnapshotData.forFile(effectivePath, content);
            } else {
                afterState = SnapshotData.forDeletedFile(effectivePath);
            }

            return ActionSnapshot.builder()
                    .actionId(beforeSnapshot.getActionId())
                    .taskId(beforeSnapshot.getTaskId())
                    .actionType(beforeSnapshot.getActionType())
                    .beforeState(beforeSnapshot.getBeforeState())
                    .afterState(afterState)
                    .timestamp(beforeSnapshot.getTimestamp())
                    .build();

        } catch (Exception e) {
            log.error("Erreur lors de la capture du snapshot après: {}", e.getMessage());
            return beforeSnapshot;
        }
    }

    @Override
    public boolean supportsRollback(Task task) {
        return task.getType() == Task.TaskType.FILE_OPERATION;
    }
}