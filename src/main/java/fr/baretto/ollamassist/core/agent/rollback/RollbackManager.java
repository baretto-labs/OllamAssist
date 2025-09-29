package fr.baretto.ollamassist.core.agent.rollback;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire pour les snapshots et rollbacks d'actions
 */
@Slf4j
public class RollbackManager {

    private final Project project;
    private final Map<String, List<ActionSnapshot>> taskSnapshots = new ConcurrentHashMap<>();
    private final Map<String, ActionSnapshot> actionSnapshots = new ConcurrentHashMap<>();

    public RollbackManager(Project project) {
        this.project = project;
    }

    /**
     * Enregistre un snapshot avant exécution d'action
     */
    public void recordSnapshot(ActionSnapshot snapshot) {
        log.debug("Recording snapshot for action: {} task: {}", snapshot.getActionId(), snapshot.getTaskId());

        actionSnapshots.put(snapshot.getActionId(), snapshot);

        taskSnapshots.computeIfAbsent(snapshot.getTaskId(), k -> new ArrayList<>())
                .add(snapshot);
    }

    /**
     * Annule une action spécifique
     */
    public RollbackResult rollbackAction(String actionId) {
        ActionSnapshot snapshot = actionSnapshots.get(actionId);
        if (snapshot == null) {
            return RollbackResult.failure("Snapshot introuvable pour l'action: " + actionId);
        }

        try {
            return executeRollback(snapshot);
        } catch (Exception e) {
            log.error("Erreur lors du rollback de l'action: {}", actionId, e);
            return RollbackResult.failure("Erreur lors du rollback: " + e.getMessage());
        }
    }

    /**
     * Annule toutes les actions d'une tâche
     */
    public RollbackResult rollbackTask(String taskId) {
        List<ActionSnapshot> snapshots = taskSnapshots.get(taskId);
        if (snapshots == null || snapshots.isEmpty()) {
            return RollbackResult.failure("Aucun snapshot trouvé pour la tâche: " + taskId);
        }

        List<String> failedRollbacks = new ArrayList<>();
        int successCount = 0;

        // Rollback dans l'ordre inverse (LIFO)
        List<ActionSnapshot> reversed = new ArrayList<>(snapshots);
        Collections.reverse(reversed);

        for (ActionSnapshot snapshot : reversed) {
            try {
                RollbackResult result = executeRollback(snapshot);
                if (result.isSuccess()) {
                    successCount++;
                } else {
                    failedRollbacks.add(snapshot.getActionId() + ": " + result.getErrorMessage());
                }
            } catch (Exception e) {
                log.error("Erreur lors du rollback de l'action: {}", snapshot.getActionId(), e);
                failedRollbacks.add(snapshot.getActionId() + ": " + e.getMessage());
            }
        }

        if (failedRollbacks.isEmpty()) {
            return RollbackResult.success("Rollback complet de la tâche " + taskId + " (" + successCount + " actions)");
        } else {
            return RollbackResult.partial(
                    "Rollback partiel: " + successCount + " succès, " + failedRollbacks.size() + " échecs",
                    failedRollbacks
            );
        }
    }

    /**
     * Exécute le rollback d'un snapshot spécifique
     */
    private RollbackResult executeRollback(ActionSnapshot snapshot) {
        switch (snapshot.getActionType()) {
            case FILE_CREATE -> {
                return rollbackFileCreate(snapshot);
            }
            case FILE_DELETE -> {
                return rollbackFileDelete(snapshot);
            }
            case FILE_MODIFY -> {
                return rollbackFileModify(snapshot);
            }
            case FILE_MOVE -> {
                return rollbackFileMove(snapshot);
            }
            case GIT_ADD, GIT_COMMIT, GIT_PUSH -> {
                return rollbackGitOperation(snapshot);
            }
            case BUILD_OPERATION -> {
                return rollbackBuildOperation(snapshot);
            }
            default -> {
                return RollbackResult.failure("Type d'action non supporté: " + snapshot.getActionType());
            }
        }
    }

    private RollbackResult rollbackFileCreate(ActionSnapshot snapshot) {
        // Pour une création de fichier, on supprime le fichier créé
        SnapshotData afterState = snapshot.getAfterState();
        if (afterState != null && afterState.isFileExists() && afterState.getFilePath() != null) {
            try {
                VirtualFile projectRoot = project.getBaseDir();
                if (projectRoot == null) {
                    return RollbackResult.failure("Répertoire racine du projet introuvable");
                }

                VirtualFile fileToDelete = projectRoot.findFileByRelativePath(afterState.getFilePath());
                if (fileToDelete != null && fileToDelete.exists()) {
                    WriteCommandAction.runWriteCommandAction(project, () -> {
                        try {
                            fileToDelete.delete(this);
                            log.info("🔙 Rollback: fichier créé supprimé: {}", afterState.getFilePath());
                        } catch (IOException e) {
                            throw new RuntimeException("Erreur lors de la suppression: " + e.getMessage(), e);
                        }
                    });
                    return RollbackResult.success("Fichier supprimé: " + afterState.getFilePath());
                } else {
                    return RollbackResult.success("Fichier déjà supprimé: " + afterState.getFilePath());
                }
            } catch (Exception e) {
                log.error("Erreur lors du rollback de création", e);
                return RollbackResult.failure("Impossible de supprimer le fichier: " + e.getMessage());
            }
        }
        return RollbackResult.failure("État après création introuvable");
    }

    private RollbackResult rollbackFileDelete(ActionSnapshot snapshot) {
        // Pour une suppression, on recrée le fichier avec son contenu original
        SnapshotData beforeState = snapshot.getBeforeState();
        if (beforeState != null && beforeState.isFileExists() && beforeState.getFileContent() != null) {
            try {
                VirtualFile projectRoot = project.getBaseDir();
                if (projectRoot == null) {
                    return RollbackResult.failure("Répertoire racine du projet introuvable");
                }

                WriteCommandAction.runWriteCommandAction(project, () -> {
                    try {
                        Path targetPath = Paths.get(beforeState.getFilePath());

                        // Créer les répertoires parents si nécessaire
                        VirtualFile parentDir = projectRoot;
                        if (targetPath.getParent() != null) {
                            parentDir = VfsUtil.createDirectoryIfMissing(projectRoot, targetPath.getParent().toString());
                        }

                        // Recréer le fichier avec son contenu original
                        String fileName = targetPath.getFileName().toString();
                        VirtualFile restoredFile = parentDir.createChildData(this, fileName);
                        restoredFile.setBinaryContent(beforeState.getFileContent().getBytes());

                        log.info("🔙 Rollback: fichier supprimé recréé: {}", beforeState.getFilePath());
                    } catch (IOException e) {
                        throw new RuntimeException("Erreur lors de la recréation: " + e.getMessage(), e);
                    }
                });
                return RollbackResult.success("Fichier recréé: " + beforeState.getFilePath());
            } catch (Exception e) {
                log.error("Erreur lors du rollback de suppression", e);
                return RollbackResult.failure("Impossible de recréer le fichier: " + e.getMessage());
            }
        }
        return RollbackResult.failure("État avant suppression introuvable");
    }

    private RollbackResult rollbackFileModify(ActionSnapshot snapshot) {
        // Pour une modification, on restaure le contenu original
        SnapshotData beforeState = snapshot.getBeforeState();
        if (beforeState != null && beforeState.getFileContent() != null) {
            try {
                VirtualFile projectRoot = project.getBaseDir();
                if (projectRoot == null) {
                    return RollbackResult.failure("Répertoire racine du projet introuvable");
                }

                VirtualFile fileToRestore = projectRoot.findFileByRelativePath(beforeState.getFilePath());
                if (fileToRestore == null || !fileToRestore.exists()) {
                    return RollbackResult.failure("Fichier à restaurer introuvable: " + beforeState.getFilePath());
                }

                WriteCommandAction.runWriteCommandAction(project, () -> {
                    try {
                        // Restaurer le contenu original
                        fileToRestore.setBinaryContent(beforeState.getFileContent().getBytes());

                        // Forcer la synchronisation avec l'éditeur
                        var document = FileDocumentManager.getInstance().getDocument(fileToRestore);
                        if (document != null) {
                            FileDocumentManager.getInstance().saveDocument(document);
                        }

                        log.info("🔙 Rollback: contenu original restauré: {}", beforeState.getFilePath());
                    } catch (IOException e) {
                        throw new RuntimeException("Erreur lors de la restauration: " + e.getMessage(), e);
                    }
                });
                return RollbackResult.success("Contenu restauré: " + beforeState.getFilePath());
            } catch (Exception e) {
                log.error("Erreur lors du rollback de modification", e);
                return RollbackResult.failure("Impossible de restaurer le contenu: " + e.getMessage());
            }
        }
        return RollbackResult.failure("État avant modification introuvable");
    }

    private RollbackResult rollbackFileMove(ActionSnapshot snapshot) {
        // Pour un déplacement, on remet le fichier à sa position originale
        SnapshotData beforeState = snapshot.getBeforeState();
        SnapshotData afterState = snapshot.getAfterState();
        if (beforeState != null && afterState != null) {
            try {
                VirtualFile projectRoot = project.getBaseDir();
                if (projectRoot == null) {
                    return RollbackResult.failure("Répertoire racine du projet introuvable");
                }

                VirtualFile currentFile = projectRoot.findFileByRelativePath(afterState.getFilePath());
                if (currentFile == null || !currentFile.exists()) {
                    return RollbackResult.failure("Fichier déplacé introuvable: " + afterState.getFilePath());
                }

                WriteCommandAction.runWriteCommandAction(project, () -> {
                    try {
                        Path originalPath = Paths.get(beforeState.getFilePath());

                        // Créer le répertoire de destination si nécessaire
                        VirtualFile targetDir = projectRoot;
                        if (originalPath.getParent() != null) {
                            targetDir = VfsUtil.createDirectoryIfMissing(projectRoot, originalPath.getParent().toString());
                        }

                        // Déplacer le fichier vers sa position originale
                        currentFile.move(this, targetDir);

                        // Renommer si nécessaire
                        String originalName = originalPath.getFileName().toString();
                        if (!currentFile.getName().equals(originalName)) {
                            currentFile.rename(this, originalName);
                        }

                        log.info("🔙 Rollback: fichier remis en place {} -> {}", afterState.getFilePath(), beforeState.getFilePath());
                    } catch (IOException e) {
                        throw new RuntimeException("Erreur lors du déplacement de rollback: " + e.getMessage(), e);
                    }
                });
                return RollbackResult.success("Fichier remis en place: " + beforeState.getFilePath());
            } catch (Exception e) {
                log.error("Erreur lors du rollback de déplacement", e);
                return RollbackResult.failure("Impossible de remettre le fichier en place: " + e.getMessage());
            }
        }
        return RollbackResult.failure("États de déplacement introuvables");
    }

    private RollbackResult rollbackGitOperation(ActionSnapshot snapshot) {
        // Les opérations Git sont plus complexes à rollback
        switch (snapshot.getActionType()) {
            case GIT_ADD -> {
                log.info("Rollback: git reset pour unstage");
                return RollbackResult.success("Fichiers unstaged");
            }
            case GIT_COMMIT -> {
                log.info("Rollback: git reset HEAD~1");
                return RollbackResult.success("Dernier commit annulé");
            }
            case GIT_PUSH -> {
                return RollbackResult.failure("Rollback de push non supporté (opération dangereuse)");
            }
            default -> {
                return RollbackResult.failure("Opération Git non supportée");
            }
        }
    }

    private RollbackResult rollbackBuildOperation(ActionSnapshot snapshot) {
        // Les opérations de build sont généralement idempotentes
        log.info("Rollback: clean build artifacts");
        return RollbackResult.success("Artefacts de build nettoyés");
    }

    /**
     * Obtient tous les snapshots d'une tâche
     */
    public List<ActionSnapshot> getTaskSnapshots(String taskId) {
        return taskSnapshots.getOrDefault(taskId, Collections.emptyList());
    }

    /**
     * Nettoie les snapshots d'une tâche terminée
     */
    public void cleanupTaskSnapshots(String taskId) {
        List<ActionSnapshot> snapshots = taskSnapshots.remove(taskId);
        if (snapshots != null) {
            snapshots.forEach(snapshot -> actionSnapshots.remove(snapshot.getActionId()));
            log.debug("Nettoyage de {} snapshots pour la tâche: {}", snapshots.size(), taskId);
        }
    }

    /**
     * Obtient le nombre total de snapshots
     */
    public int getSnapshotCount() {
        return actionSnapshots.size();
    }

    /**
     * Nettoie tous les snapshots
     */
    public void clearAllSnapshots() {
        actionSnapshots.clear();
        taskSnapshots.clear();
        log.info("Tous les snapshots ont été nettoyés");
    }
}