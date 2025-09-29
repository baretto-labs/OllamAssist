package fr.baretto.ollamassist.core.agent.intention;

import java.util.List;

/**
 * Représente l'intention détectée d'un message utilisateur
 */
public class UserIntention {

    private final Type type;
    private final ActionType actionType;
    private final List<String> keyElements;
    private final double confidence;
    private UserIntention(Type type, ActionType actionType, List<String> keyElements, double confidence) {
        this.type = type;
        this.actionType = actionType;
        this.keyElements = keyElements;
        this.confidence = confidence;
    }

    public static UserIntention question(List<String> keyElements) {
        return new UserIntention(Type.QUESTION, null, keyElements, 0.8);
    }

    public static UserIntention action(ActionType actionType, List<String> keyElements, double confidence) {
        return new UserIntention(Type.ACTION, actionType, keyElements, confidence);
    }

    public static UserIntention unknown() {
        return new UserIntention(Type.UNKNOWN, null, List.of(), 0.0);
    }

    // Getters
    public Type getType() {
        return type;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public List<String> getKeyElements() {
        return keyElements;
    }

    public double getConfidence() {
        return confidence;
    }

    @Override
    public String toString() {
        return "UserIntention{" +
                "type=" + type +
                ", actionType=" + actionType +
                ", confidence=" + confidence +
                '}';
    }

    public enum Type {
        QUESTION,   // L'utilisateur pose une question
        ACTION,     // L'utilisateur demande une action
        UNKNOWN     // Intention non déterminée
    }

    public enum ActionType {
        CODE_MODIFICATION,  // Modification de code
        FILE_OPERATION,     // Opération sur fichiers
        GIT_OPERATION,      // Opération Git
        BUILD_OPERATION     // Opération de build/test
    }
}