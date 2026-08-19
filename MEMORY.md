# MEMORY.md

This file tracks important context about ongoing and past tasks in the OllamAssist project.
It is maintained by Claude Code across conversations to preserve task continuity.

## How to use

- **Read this file** at the start of each session to understand what was done before.
- **Update this file** at the end of each task or when significant decisions are made.
- **Do not store** code patterns, architecture details, or anything already in CLAUDE.md.
- **Do store** task status, decisions made, open questions, and non-obvious context.

## Active Tasks

### Agent mode — nettoyage puis corrections (depuis 2026-08-19)
Audit complet du mode agent effectue le 2026-08-19. Decision : **on garde Plan-then-Execute**
(`PlanAndExecuteAgentService`), l'architecture function-calling est supprimee du repo
(elle etait inatteignable : 19 classes / ~2500 LOC + ~2600 LOC de tests).
`AGENT_ARCH.md` a ete cree a la racine : invariants SI-1 a SI-8 + tableau des ecarts ouverts.

Ecarts a corriger, dans cet ordre (details dans `AGENT_ARCH.md` section "Known gaps") :
C1 numeros de ligne fragment-locaux envoyes au planner (edits au mauvais endroit) —
C2 prompt de planification non assaini — C3 `LineEditTool` sans approbation ni SecretDetector —
puis M1 a M10.

Conserves volontairement bien qu'inutilises pour l'instant, car necessaires aux corrections :
`PromptSanitizer` (C2), `AuditLogger` (M4), `EditFileTool` (candidat au remplacement de
`LineEditTool` pour C1/C3, edition par ancre de texte au lieu de numeros de ligne).


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