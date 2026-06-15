# MEMORY.md

This file tracks important context about ongoing and past tasks in the OllamAssist project.
It is maintained by Claude Code across conversations to preserve task continuity.

## How to use

- **Read this file** at the start of each session to understand what was done before.
- **Update this file** at the end of each task or when significant decisions are made.
- **Do not store** code patterns, architecture details, or anything already in CLAUDE.md.
- **Do store** task status, decisions made, open questions, and non-obvious context.

## Active Tasks

### Agent Mode Quality — Benchmark & Foundation Fixes (2026-06-11)

**Statut** : en attente de RAGUnit v0.2 (mission externe en cours).

**Contexte** : audit du mode agent (`PlanAndExecuteAgentService`, 951 lignes, câblé dans `OllamaContent`). Trois bugs de correctness identifiés en plus des manques de qualité :
- Numérotation de lignes incohérente pour fichiers >150 lignes (`extractFragment` renvoie des fenêtres numérotées 1..N alors que `LineEditTool` applique sur les lignes absolues) — le prompt système ment au LLM.
- Dérive multi-étapes : steps planifiés contre l'état initial, appliqués séquentiellement → les line refs deviennent stales dès qu'un step modifie le fichier.
- Validation post-édition trop faible : `isSyntaxError` ne reconnaît que 3 motifs de chaîne, `PsiSyntaxValidator` existe mais n'est pas branché en boucle de feedback.
Plus : discovery purement keyword-based (regex camelCase/snake_case), la `KnowledgeIndex` n'est jamais utilisée pour le planner ; et plan sans reasoning ni summary final.

**Décision stratégique** : investir d'abord dans RAGUnit v0.2 (Bloc 1+2, ~3.5j) pour disposer d'une mesure crédible AVANT d'attaquer les corrections agent. Pas d'alternative JVM mature (langchain4j-evaluator n'existe pas, vérifié). Fallback documenté : si RAGUnit v0.2 ne livre pas sous 3.5j, basculer sur RAGAS (Python) en subprocess.

**Mission RAGUnit déléguée** : prompt remis le 2026-06-11. Périmètre = T1.1 prompt visible, T1.2 variance N-runs, T1.3 JudgeResult structuré, T2.1 JudgeQuery générique, T2.2 helpers déterministes. Suivi côté RAGUnit dans `V0.2_PLAN.md` à la racine du repo ragunit.

**À reprendre quand RAGUnit v0.2 est livrée** (ordre d'exécution : 6 → 1 → 3 → 4 → 2 → 5) :
1. **Tâche 6 (~1j)** : monter le banc benchmark dans `src/benchmark/java/` : `AgentTestHarness`, `TestProjectFixture` (via `BasePlatformTestCase`), `GoldenScenarioLoader`. Ajouter `benchmarkImplementation("com.github.baretto-labs:ragunit:0.2.0")` + jitpack repo. Étage 1 = assertions déterministes (PSI parse, file content, regex sur diff). Étage 2 = RAGUnit sur résumé/réponse. Modèle juge ≠ modèle agent (anti-self-bias) : `OllamaJudge("qwen2.5:14b")`.
2. **Tâche 1** : préserver les numéros absolus dans `extractFragment` (l.464–499) et `buildPlanningMessage` (l.553–580) de `PlanAndExecuteAgentService.java`. Test : fichier 400 lignes, mot-clé en ligne 300, plan `insertAfterLine 305` doit atterrir en ligne 305 du fichier réel.
3. **Tâche 3** : remplacer `isSyntaxError` (string match, l.636–640) par validation PSI réelle après chaque `writeFile`/`editFile`/`appendFile`. Boucle de fix jusqu'à 2 retries avec les vraies erreurs PSI.
4. **Tâche 4** : régler la dérive multi-étapes. Option a (simple) : interdire dans le prompt système plusieurs `editFile` sur le même path et imposer l'ordre décroissant des lignes. Option b (robuste) : recalculer les offsets entre steps. Option a suffit en première passe.
5. **Tâche 2** : brancher `SearchKnowledgeBaseTool` en fallback de discovery quand `extractKeywords` retourne <2 termes OU que PSI/grep cumule <2 fichiers. Marquer la provenance (`[semantic match]`) dans le contexte injecté.
6. **Tâche 5** : étendre le format de plan avec `reasoning` (consommé, non persisté) et `summary` (persisté en conversation à la place du `Done.\n`). Unifie Format A/B.

Après chaque tâche, relancer le banc et consigner le delta de scores dans ce fichier sous une section "Scoreboard" à créer.

**Garde-fous discutés** :
- Les bugs de correctness (tâches 1, 3, 4) sont mesurables par assertions déterministes ; RAGUnit n'est pas l'instrument principal pour eux. Le juge LLM est instrument secondaire pour la qualité du résumé (tâche 5) et l'absence d'hallucination dans la réponse.
- Si à 6 mois RAGUnit n'a aucun utilisateur externe, c'est de facto une lib "OllamAssist eval" — soit assumer, soit replier sur Python. Ne pas s'enfermer par sunk cost.

## Completed Tasks

### Conversation Management Feature (2026-03-24)
Implémentation complète de la gestion des conversations par projet.

**Fichiers créés :**
- `fr.baretto.ollamassist.conversation.Conversation` — domaine, mutable, génération titre automatique (1er message, 60 chars max)
- `fr.baretto.ollamassist.conversation.ConversationMessage` — record immuable, roles USER/ASSISTANT
- `fr.baretto.ollamassist.conversation.ConversationRepository` — persistance JSON (Jackson) dans `{project}/.ollamassist/conversations/`
- `fr.baretto.ollamassist.conversation.ConversationService` — `@Service(PROJECT)`, CRUD, charge au démarrage
- `fr.baretto.ollamassist.events.ConversationSwitchedNotifier` — nouveau topic projet (BroadcastDirection.NONE)
- `fr.baretto.ollamassist.chat.ui.ConversationManagerPanel` — remplace `ConversationSelectorPanel`, JComboBox + boutons + et poubelle

**Fichiers modifiés :**
- `OllamaService` — abonnement à `ConversationSwitchedNotifier`, méthode `restoreMemory()`
- `MessagesPanel` — méthode `loadConversation()`, `clearAll()` recrée la `PresentationPanel`
- `OllamaContent` — persist user/assistant messages, abonnement `ConversationSwitchedNotifier`, charge l'historique au démarrage
- `plugin.xml` — enregistrement `ConversationService` comme projectService

**Décisions clés :**
- Persistance JSON (pas Lucene) — plus simple, lisible, Jackson déjà présent
- 1 fichier JSON par conversation, nommé `{uuid}.json`
- La mémoire LangChain4j (25 msg sliding window) reste ; on la restaure au switch depuis les messages persistés
- La suppression demande confirmation via `Messages.showYesNoDialog`
- `ConversationNotifier` (ancien "clear") conservé pour compatibilité

## Open Questions / Blockers

<!-- none -->