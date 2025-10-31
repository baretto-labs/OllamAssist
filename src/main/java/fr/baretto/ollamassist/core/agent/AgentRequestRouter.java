package fr.baretto.ollamassist.core.agent;

import lombok.extern.slf4j.Slf4j;

/**
 * Classifier for agent requests - determines if a user request is an action or a question
 */
@Slf4j
public class AgentRequestRouter {

    /**
     * Détecte si la requête utilisateur est une action de développement ou une question
     *
     * @param userRequest la requête de l'utilisateur
     * @return true si c'est une action, false si c'est une question/chat
     */
    public boolean isActionRequest(String userRequest) {
        if (userRequest == null || userRequest.isBlank()) {
            return false;
        }

        String lower = userRequest.toLowerCase();

        log.debug("Analyzing request: '{}'", userRequest);

        // Exclure les phrases de clarification ou de reformulation
        if (isReformulationRequest(lower)) {
            log.debug("Request classified as reformulation: '{}'", userRequest);
            return false;
        }

        // Détecter les actions de développement
        boolean isAction = isCreationAction(lower) || isOtherDevelopmentAction(lower);

        log.debug("Request classified as action: {}", isAction);
        return isAction;
    }

    /**
     * Vérifie si c'est une demande de reformulation/clarification
     */
    private boolean isReformulationRequest(String lowerRequest) {
        return lowerRequest.startsWith("reformule")
                || lowerRequest.startsWith("clarifi")
                || lowerRequest.startsWith("explique")
                || lowerRequest.contains("qu'est-ce que")
                || lowerRequest.contains("comment ça")
                || lowerRequest.contains("je veux dire")
                || lowerRequest.contains("en fait");
    }

    /**
     * Vérifie si c'est une action de création (classe, fichier)
     */
    private boolean isCreationAction(String lowerRequest) {
        boolean hasCreateVerb = lowerRequest.contains("crée")
                || lowerRequest.contains("créer")
                || lowerRequest.contains("créé")
                || lowerRequest.contains("create");

        boolean hasTarget = lowerRequest.contains("classe")
                || lowerRequest.contains("class")
                || lowerRequest.contains("fichier")
                || lowerRequest.contains("file");

        return hasCreateVerb && hasTarget;
    }

    /**
     * Vérifie si c'est une autre action de développement (git, build, etc.)
     */
    private boolean isOtherDevelopmentAction(String lowerRequest) {
        return lowerRequest.contains("commit")
                || lowerRequest.contains("push")
                || lowerRequest.contains("build")
                || lowerRequest.contains("compile")
                || lowerRequest.contains("test")
                || lowerRequest.contains("refactor");
    }
}
