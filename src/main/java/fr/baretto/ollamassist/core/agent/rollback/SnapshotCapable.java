package fr.baretto.ollamassist.core.agent.rollback;

import fr.baretto.ollamassist.core.agent.task.Task;

/**
 * Interface pour les executors capables de capturer des snapshots
 */
public interface SnapshotCapable {

    /**
     * Capture un snapshot avant l'exécution d'une tâche
     */
    ActionSnapshot captureBeforeSnapshot(Task task);

    /**
     * Capture un snapshot après l'exécution d'une tâche
     */
    ActionSnapshot captureAfterSnapshot(Task task, ActionSnapshot beforeSnapshot);

    /**
     * Indique si cet executor supporte le rollback pour le type de tâche donné
     */
    boolean supportsRollback(Task task);
}