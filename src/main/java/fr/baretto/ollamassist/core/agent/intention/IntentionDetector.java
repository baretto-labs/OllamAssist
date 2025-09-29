package fr.baretto.ollamassist.core.agent.intention;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Détecteur d'intention utilisateur - Analyse les messages pour déterminer si c'est une question ou une action
 */
public class IntentionDetector {

    private static final List<String> ACTION_KEYWORDS = Arrays.asList(
            "create", "add", "generate", "refactor", "optimize", "fix", "delete", "remove",
            "rename", "extract", "move", "commit", "build", "run", "test", "deploy",
            "implement", "update", "modify", "change", "improve"
    );

    private static final List<String> QUESTION_KEYWORDS = Arrays.asList(
            "what", "how", "why", "when", "where", "which", "who", "explain", "describe",
            "difference", "meaning", "purpose", "help", "understand", "learn"
    );

    private static final Pattern QUESTION_PATTERN = Pattern.compile(".*\\?\\s*$");

    /**
     * Analyse un message utilisateur et détermine son intention
     */
    public UserIntention detectIntention(String message) {
        if (message == null || message.trim().isEmpty()) {
            return UserIntention.unknown();
        }

        String normalizedMessage = message.toLowerCase().trim();

        // Vérifier si c'est clairement une question
        if (isQuestion(normalizedMessage)) {
            return UserIntention.question(extractKeyElements(message));
        }

        // Vérifier si c'est clairement une action
        if (isAction(normalizedMessage)) {
            UserIntention.ActionType actionType = determineActionType(normalizedMessage);
            double confidence = calculateConfidence(normalizedMessage, true);
            return UserIntention.action(actionType, extractKeyElements(message), confidence);
        }

        // En cas d'ambiguïté, préférer question pour éviter les actions non désirées
        return UserIntention.question(extractKeyElements(message));
    }

    private boolean isQuestion(String message) {
        // Vérifier les patterns de questions
        if (QUESTION_PATTERN.matcher(message).matches()) {
            return true;
        }

        // Vérifier les mots-clés de questions
        for (String keyword : QUESTION_KEYWORDS) {
            if (message.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private boolean isAction(String message) {
        // Vérifier les mots-clés d'actions
        for (String keyword : ACTION_KEYWORDS) {
            if (message.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private UserIntention.ActionType determineActionType(String message) {
        // Vérifier en priorité les actions les plus spécifiques
        if (message.contains("commit") || message.contains("git")) {
            return UserIntention.ActionType.GIT_OPERATION;
        }

        if (message.contains("delete") || message.contains("remove") || message.contains("move")) {
            return UserIntention.ActionType.FILE_OPERATION;
        }

        if (message.contains("create") || message.contains("add") || message.contains("generate")) {
            if (message.contains("file") || message.contains("class") || message.contains("test")) {
                return UserIntention.ActionType.FILE_OPERATION;
            }
            return UserIntention.ActionType.CODE_MODIFICATION;
        }

        if (message.contains("build") || message.contains("run")) {
            return UserIntention.ActionType.BUILD_OPERATION;
        }

        if (message.contains("refactor") || message.contains("optimize") || message.contains("fix")) {
            return UserIntention.ActionType.CODE_MODIFICATION;
        }

        return UserIntention.ActionType.CODE_MODIFICATION;
    }

    private List<String> extractKeyElements(String message) {
        // Extraction simple des éléments clés (amélioration future avec NLP)
        return Arrays.asList(message.split("\\s+"));
    }

    private double calculateConfidence(String message, boolean isAction) {
        if (isAction) {
            // Calculer la confiance pour les actions
            long actionKeywords = ACTION_KEYWORDS.stream()
                    .mapToLong(keyword -> message.contains(keyword) ? 1 : 0)
                    .sum();

            // Réduire la confiance pour les messages ambigus ou hésitants
            boolean isAmbiguous = message.contains("maybe") || message.contains("perhaps") ||
                    message.contains("should") || message.contains("could");

            // Boost de confiance pour les actions très claires (verbe + objet spécifique)
            boolean hasSpecificTarget = message.contains("file") || message.contains("method") ||
                    message.contains("class") || message.contains("Test.java");

            if (isAmbiguous) return 0.5; // Messages ambigus ont une confiance faible
            if (actionKeywords >= 2) return 0.9;
            if (actionKeywords == 1 && hasSpecificTarget) return 0.85;
            if (actionKeywords == 1) return 0.7;
            return 0.5;
        } else {
            // Calculer la confiance pour les questions
            if (QUESTION_PATTERN.matcher(message).matches()) return 0.9;

            long questionKeywords = QUESTION_KEYWORDS.stream()
                    .mapToLong(keyword -> message.contains(keyword) ? 1 : 0)
                    .sum();

            if (questionKeywords >= 1) return 0.8;
            return 0.3;
        }
    }
}