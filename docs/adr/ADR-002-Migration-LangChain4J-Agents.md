# ADR-002: Migration vers LangChain4J Agentic pour le Mode Agent

## Status
**ACCEPTÉ** - 16 septembre 2025

## Context

Le mode agent d'OllamAssist utilise actuellement une architecture custom avec :
- TaskPlanner qui envoie des prompts texte à Ollama
- Parsing JSON manuel des réponses
- TaskExecutors qui simulent des outils
- Pas de vraie communication tools ↔ LLM

### Problèmes identifiés
1. **Approche "bluff"** : Ollama ne connaît pas vraiment nos outils
2. **Parsing fragile** : JSON parsing manuel sujets aux erreurs
3. **Code de plomberie** : Beaucoup de code custom à maintenir
4. **Pas de standards** : Réinvention de concepts déjà standardisés

### Opportunité LangChain4J Agentic
LangChain4J offre des modules agents matures :
- `langchain4j-agentic` : API déclarative pour agents
- `langchain4j-agentic-a2a` : Agent-to-Agent communication
- Support natif des tools avec `@Tool` annotations
- AgenticScope pour partage d'état entre agents
- Function calling natif avec les LLMs

## Decision

**Migration progressive vers LangChain4J Agentic en 2 phases :**

### Phase 1 (Immédiate) : Hybridation Tools
- Garder l'architecture ExecutionEngine actuelle
- Transformer les TaskExecutors en vrais Tools LangChain4J (`@Tool`)
- Remplacer le TaskPlanner par un Agent LangChain4J
- Utiliser le function calling natif au lieu du parsing JSON

### Phase 2 (Future) : Migration complète
- Architecture 100% LangChain4J agentic
- Multi-agents spécialisés (FileAgent, CodeAgent, GitAgent)
- AgenticScope pour coordination
- Workflows agents complexes

## Consequences

### Positives
✅ **Vraie intégration tools** : Function calling natif
✅ **Robustesse** : Moins de code custom fragile
✅ **Standards industriels** : Architecture agentic reconnue
✅ **Évolutivité** : Support multi-agents natif
✅ **Maintenabilité** : Moins de code de plomberie

### Négatives
❌ **Refactoring** : Modification de l'architecture existante
❌ **Dépendance** : Plus dépendant de LangChain4J
❌ **Apprentissage** : Nouvelles APIs à maîtriser

### Risques
🚨 **Module experimental** : `langchain4j-agentic` encore en beta
🚨 **Compatibilité** : Possible breaking changes futurs
🚨 **Performance** : Impact sur les performances à évaluer

## Implementation

### Dépendances à ajouter
```kotlin
implementation("dev.langchain4j:langchain4j-agentic:$langchain4jVersion")
implementation("dev.langchain4j:langchain4j-agentic-a2a:$langchain4jVersion")
```

### Architecture cible Phase 1
```
TaskPlanner (custom)
    ↓
IntelliJDevelopmentAgent (@Agent LangChain4J)
    ├── createFile() @Tool
    ├── analyzeCode() @Tool
    ├── executeGitCommand() @Tool
    └── buildProject() @Tool
    ↓
ExecutionEngine (garde l'existant)
```

### Métrique de succès
- ✅ Function calling fonctionne avec Ollama
- ✅ Outils déclarés correctement au LLM
- ✅ Création de fichiers effective
- ✅ Logs montrent les vrais tool calls

## Related

- Remplace l'architecture custom du mode agent
- Prépare l'évolution vers multi-agents
- Améliore l'intégration avec l'écosystème AI/ML Java