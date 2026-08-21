# MEMORY.md

This file tracks important context about ongoing and past tasks in the OllamAssist project.
It is maintained by Claude Code across conversations to preserve task continuity.

## How to use

- **Read this file** at the start of each session to understand what was done before.
- **Update this file** at the end of each task or when significant decisions are made.
- **Do not store** code patterns, architecture details, or anything already in CLAUDE.md.
- **Do store** task status, decisions made, open questions, and non-obvious context.

## Active Tasks

Cinq entrées : trois chantiers actifs (release, conférence, RAGUnit), le mode agent gelé,
et l'état de la mesure. État au 2026-08-21.

---

### 1. Release 1.14.0 — en cours de livraison

**Contenu livré** (tout est sur `main`) :
- opt-out des notifications de release + correction du balloon qui revenait à chaque démarrage
- interrupteur de la complétion inline (Settings → OllamAssist → Actions) + 3 correctifs :
  handler Entrée détourné à l'échelle de l'IDE et jamais restauré, key listener ajouté à chaque
  requête, et read action manquante sur le chemin de repli (`EnhancedContextProvider`)
- correction C1 du mode agent (numéros de ligne absolus envoyés au planner) — PR #178
- suppression de l'architecture agent function-calling (19 classes mortes)
- juge RAGUnit dans le benchmark (invisible pour l'utilisateur, `test` scope)

**Origine de la priorité complétion** : avis 3 étoiles sur le marketplace (PyCharm 2026.2,
plugin 1.13.1) — « My workflow does not use code completion […] I have found no way to disable,
other than disabling the plugin altogether ». Répondre publiquement à cet avis fait partie de la
livraison.

**Reste à faire** : test manuel du zip, tag `v1.14.0`, upload marketplace, réponse au reviewer.

**Points de vigilance :**
- La vérification automatique JetBrains rejette tout ce qui ouvre une dialog modale au démarrage
  (cf. rejet du 2026-08-17). Ne pas rouvrir ce chemin.
- `main` n'a **aucune protection de branche** : `gh pr merge --auto` merge immédiatement sans
  attendre le job `build`. Attendre le check explicitement, ou protéger la branche.

---

### 2. Conférence DevFest Paris — 27 novembre 2026

**Thèse** : on sait tester le déterministe, les tests font partie des bonnes pratiques — comment
teste-t-on ce qui ne l'est pas ? Une réponse : LLM-as-a-judge. Public de développeurs
généralistes, pas de spécialistes LLM.

**Arc en quatre actes** : le problème (sortie non déterministe) → l'instrument (seuil,
répétitions, stabilité, prompt auditable) → les cas concrets → les limites.

**Cas concrets disponibles** : le benchmark RAG (BM25 +154 %, Neo4j à plat, bug de chunking
+60 % trouvé par la mesure) et l'intégration dans la CI d'OllamAssist.

**Angle qui parle à la salle** : le test flaky. `withRuns(n)` + rejet si `stddev > 0.15`, c'est
la réponse connue au problème « ça ne donne pas deux fois le même résultat ».

**Supports v1** dans le dépôt `baretto-labs/llm-as-a-judge`. Plan détaillé avec le raisonnement :
https://claude.ai/code/artifact/7352d89c-718c-4afc-9b14-18962a5b3cf1

**Jalons non négociables :**
- **mi-octobre** — gel de l'API RAGUnit + décision sur le nom (voir §3)
- **6 novembre** — dernier moment raisonnable pour publier sur Maven Central (la vérification
  de namespace dépend d'un délai externe ; trois semaines de marge)
- démo : runs pré-enregistrés, pas d'Ollama en direct sur le wifi d'un DevFest

---

### 3. RAGUnit — la bibliothèque d'évaluation (`baretto-labs/ragunit`)

Bibliothèque JVM de LLM-as-a-judge intégrée à JUnit 5, écrite par Mehdi. Consommée par
OllamAssist en `test` scope.

**Fait** : PR #1 mergée — timeout de requête configurable (`timeout(Duration)` sur les deux
builders, `HttpJudge.DEFAULT_TIMEOUT` publique). Trouvé parce que 51 appels sur 60 tombaient en
timeout avec `qwen2.5:14b` en local. Tag `v0.3.1` posé.

**Reste, par ordre de valeur :**
1. `satisfies(Criterion, threshold)` dans le DSL d'assertion — un critère maison n'a aujourd'hui
   aucun chemin d'assertion, donc pas de `withRuns`, pas d'écart-type, pas de reporting
   (`RepeatedEvaluation` est package-private). C'est le chemin dont le harnais agent aura besoin.
   **Forme à décider après la friction réelle du harnais**, pas avant.
2. Une métrique « l'état final satisfait-il le but » pour l'agentique (RAGAS en a 4, RAGUnit 1).
   Doit rester générique : l'appelant décrit l'état en texte, la lib ne connaît ni diff ni plan.
3. `seed` sur `OllamaJudge` — température 0 réduit la variance, ne rend pas reproductible.
4. Mineur : le reporter JSON écrit dans `target/` (layout Maven) même dans un projet Gradle.

**Garde-fou** : n'ajouter que ce dont un consommateur a réellement besoin. La parité de comptage
avec RAGAS (13 métriques contre 35) n'est pas un objectif.

**Doute ouvert sur le nom** (à trancher mi-octobre, avant Central) : « Unit » est bon — dans la
famille xUnit il signifie « bibliothèque de test », pas « tests unitaires ». « RAG » a vieilli :
la lib juge déjà des agents, de l'injection de prompt, des fuites de PII, et le tagline du README
dit déjà « The LLM evaluation library for the JVM ». Renommer coûte le domaine `ragunit.org`, le
site de doc et le package `org.ragunit` — coût minimal aujourd'hui, croissant après Central.

**Distribution** : reste sur JitPack pour l'instant (décision assumée : livrer les retours
utilisateurs passe avant la plomberie de publication). Maven Central obligatoire pour le DevFest.
Pin exact obligatoire côté OllamAssist — une montée de version change le comportement du juge et
invalide la baseline.

---

### 4. Mode agent — gelé depuis le 2026-08-21

**Décision d'architecture** : Plan-then-Execute (`PlanAndExecuteAgentService`). L'architecture
function-calling a été supprimée du dépôt (elle était inatteignable). `AGENT_ARCH.md` porte les
invariants SI-1 à SI-8 et le tableau des écarts.

**Gelé veut dire** : on n'investit plus tant que la mesure n'est pas en place, pas qu'on retient
un correctif terminé. C1 est livré dans la 1.14.0 pour cette raison.

**Écarts ouverts** : C2 (prompt de planification non assaini), C3 (`LineEditTool` sans
approbation ni détection de secret ni validation syntaxique), M1 à M10 — détail dans
`AGENT_ARCH.md`, section « Known gaps ».

**C2 et M9 attendent la mesure** : ce sont des réglages de qualité, pas des défauts démontrables.
Les corriger sans baseline rend l'amélioration invérifiable.

**Conservés bien qu'inutilisés**, car nécessaires aux corrections à venir : `PromptSanitizer`
(C2), `AuditLogger` (M4), `EditFileTool` (édition par ancre, candidat au remplacement de
`LineEditTool` pour C3).

---

### 5. Mesure de la qualité — étape 01 terminée

**Fait** : le juge maison du benchmark de chunking peut être remplacé par RAGUnit
(`-Pbenchmark.judge.impl=ragunit`). Les deux juges partagent la même préparation de contexte,
donc la comparaison mesure le juge. Résultat sur les mêmes 30 questions × 2 stratégies :
60/60 questions jugées, 0 timeout, 5,05/10 contre 4,62/10 pour le juge maison. Deux couches de
jugement à 0,4 point d'écart : migrer ne coûte pas de justesse.

**Étape suivante (étape 1 du plan)** : harnais agent en mode rejeu, **sans juge**, avec des
assertions déterministes uniquement — le plan parse, les chemins existent, chaque étape
s'applique, le fichier compile, le symbole atterrit au bon endroit, l'annulation arrête vraiment.
Le hook existe déjà : le constructeur `@TestOnly` de `PlanAndExecuteAgentService` accepte un
`StreamingChatModel`.

**Contrainte de structure à respecter** : le harnais doit pouvoir tourner sur un commit
arbitraire, sinon la baseline d'avant une correction est perdue. Pas de dépendance à une API
interne récente ; procédure de cherry-pick à documenter.

**À instrumenter pour la conf** : la proportion de questions tranchées par une vérification
exacte contre celles tranchées par le juge. Ce chiffre ne se reconstitue pas après coup.

---

## Completed Tasks

### Release 1.14.0 (2026-08-19)
Version prepare depuis main (1.13.1) : `build.gradle.kts`, `<change-notes>` de `plugin.xml`
et entree `v1.14.0-release` dans `HardcodedNotificationProvider`.
Contenu = uniquement l'opt-out des notifications ci-dessous. Decision : la branche
`feat/agent-function-calling` (24 commits, 128 fichiers, 8 commits de retard sur main) reste
hors de cette release, le fonctionnement du mode agent ayant change.

### Opt-out des notifications de release (2026-08-19)
Le balloon "OllamAssist updates" n'acquittait la version qu'au `whenExpired` du balloon :
un utilisateur qui l'ignorait (le balloon reste dans la fenêtre Notifications sans expirer)
le revoyait à chaque démarrage. Correction + opt-out explicite :
- acquittement déplacé à l'affichage du balloon (`BalloonNotificationDisplayer.show`) ;
- action "Don't show again" sur le balloon, et case `DoNotAskOption` dans `NotificationDialog`
  (type qualifié `com.intellij.openapi.ui.DoNotAskOption` — le type imbriqué hérité de
  `DialogWrapper` est deprecated for removal et masque un import simple) ;
- flag `muted` persisté dans `PersistentNotificationStorage.State` (champs passés en `public`,
  cf. issue #170), filtré en un seul point : `NotificationManagerImpl.getUnreadNotifications()` ;
- réversible via Settings -> OllamAssist -> UI -> Notifications.

Décision : le mute couvre toutes les priorités, y compris `BREAKING_CHANGE`. Une info critique
devra passer par le tool window, pas par un popup.

### Rejet Marketplace — dialog modale au démarrage (2026-08-17)
L'upload du plugin échouait sur la vérification automatique JetBrains
(`InstallPluginTest.testTrialWidgetPresence`) : `Timeout(5m)` sur les indicateurs +
"Plugin must not remove the IDE Trial widget".

**Cause :** `DialogNotificationDisplayer` ouvrait une `NotificationDialog` **modale** depuis
le `postStartupActivity` `NotificationApplicationStartup`. L'EDT restait parqué dans
`Dialog.show()` (visible dans le thread dump) — personne pour cliquer OK dans l'IDE de test.

**Correctif :** `BalloonNotificationDisplayer` (remplace `DialogNotificationDisplayer`,
plugin.xml mis à jour) — balloon non bloquante dans le groupe `OllamAssist`, action
"See what's new" qui ouvre la dialog uniquement sur clic utilisateur, garde
`isUnitTestMode() || isHeadlessEnvironment()`, et `whenExpired` → `updateLastNotifiedVersion()`
pour ne pas re-notifier au redémarrage suivant.

Règle ajoutée dans `.claude/rules/TECH_STACK.md` (section IntelliJ Platform).

### Issue #170 — Chat System Prompt reset au redémarrage (2026-08-17, release 1.13.1)
Le prompt personnalisé revenait au défaut à chaque redémarrage de l'IDE.

**Cause :** `PromptSettings.State` déclarait ses champs `private` avec un simple Lombok
`@Getter`. `XmlSerializer` d'IntelliJ ne lie que les champs publics, ou les propriétés
ayant getter **et** setter (`PropertyCollector` configuré avec `COLLECT_PRIVATE_FIELDS = false`,
`isAcceptableProperty` rejette un getter sans setter). Résultat : rien n'était jamais écrit
dans `PromptSettings.xml` ni relu — l'état retombait sur les initialiseurs de champs.
Confirmé en décompilant `util-8.jar` (2024.3), pas par déduction.

**Décisions clés :**
- Champs passés en `public` dans `PromptSettings.State` et `ActionsSettings.State`
  (même bug latent : `autoApproveFileCreation` / `toolsEnabled` ne persistaient pas non plus).
- `@Getter` conservé : `SettingsMigrationService.isDefaultActionsSettings` l'utilise, et la
  propriété read-only est simplement ignorée par le collector — pas de doublon.
- Bug secondaire découvert au passage : `XmlSerializer` supprime silencieusement les
  caractères hors-BMP. `DEFAULT_CHAT_SYSTEM_PROMPT` contenait 🔧 et 👋 → retirés, sinon un
  utilisateur qui édite le prompt autour récupère un texte mutilé.

**Tests :** `SettingsStatePersistenceTest` (6) — round-trip `XmlSerializer` sur les deux
classes d'état. 4 rouges avant le fix.

**Règle persistée :** `.claude/rules/TECH_STACK.md` — section "PersistentStateComponent —
state fields must be public" + corollaire BMP.

### Bearer Token / API Key Authentication (2026-06-15)
Ajout du support d'authentification par API key (Bearer) en plus du Basic Auth existant
(issue utilisateur : Ollama derrière un proxy OpenWebUI exigeant un Bearer token).

**Décisions clés :**
- Nouveau `AuthMode` (NONE / BASIC / BEARER) — mutuellement exclusifs car même header `Authorization`.
- `AuthMode.fromString` fail-closed → NONE sur valeur inconnue/null.
- Rétrocompatibilité : `OllamaSettings.getAuthMode()` infère BASIC si `authMode` vide mais username+password présents (utilisateurs Basic existants non impactés).
- Centralisation : `AuthenticationHelper.buildAuthorizationHeaderValue(...)` (pur, testé) + `createAuthorizationHeaderValue()` / `authHeaders()`. Les 9 sites qui construisaient "Basic %s" en dur ont été refactorés pour passer par le helper (sinon le Bearer n'aurait marché qu'à moitié).
- Persistance dans `OllamaSettings.xml` (champs `authMode`, `apiKey`).
- UI : `OllamaConfigPanel` — combo "Authentication" + champs username/password (BASIC) ou API key (BEARER) affichés selon le mode.

**Tests :** `AuthenticationHelperTest` (9, fail-closed inclus), `AuthModeTest` (4). Logique pure, sans plateforme.

**Note :** effet de bord attendu — pointer l'URL chat vers OpenWebUI + Bearer donne accès aux outils OpenWebUI (SearXNG, etc.).

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