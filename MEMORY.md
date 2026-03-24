# MEMORY.md

This file tracks important context about ongoing and past tasks in the OllamAssist project.
It is maintained by Claude Code across conversations to preserve task continuity.

## How to use

- **Read this file** at the start of each session to understand what was done before.
- **Update this file** at the end of each task or when significant decisions are made.
- **Do not store** code patterns, architecture details, or anything already in CLAUDE.md.
- **Do store** task status, decisions made, open questions, and non-obvious context.

## Active Tasks

<!-- none currently -->

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