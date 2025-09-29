
pour le# ADR-001: Migration vers le Mode Agent avec Intégration MCP

## Statut
**EN COURS** - Implémentation active (Mis à jour: 2025-01-13)

## Contexte et Problématique

OllamAssist fonctionne actuellement comme un assistant de chat traditionnel avec RAG. Nous souhaitons étendre ses capacités pour qu'il devienne un véritable agent autonome capable d'exécuter des tâches complexes dans l'IDE et de se connecter à des serveurs MCP (Model Context Protocol) externes.

### Limitations Actuelles
- Interactions limitées à un système de chat question/réponse
- Pas d'exécution automatique de tâches dans l'IDE
- Capacités d'analyse et d'action restreintes au contexte local
- Absence de connectivité aux services externes MCP

## Intention et Objectifs

### Vision du Mode Agent
Transformer OllamAssist en un agent intelligent capable de :

1. **Analyse Autonome** : Analyser automatiquement le code, détecter les problèmes et proposer des solutions
2. **Exécution d'Actions** : Modifier le code, créer des fichiers, exécuter des commandes
3. **Planification de Tâches** : Décomposer des demandes complexes en sous-tâches exécutables
4. **Connectivité MCP** : Se connecter à des serveurs MCP pour étendre ses capacités
5. **Workflow Automatisé** : Exécuter des séquences d'actions pour accomplir des objectifs complexes

### Objectifs Spécifiques
- **Productivité** : Réduire le temps de développement par l'automatisation
- **Qualité** : Améliorer la qualité du code par l'analyse continue
- **Extensibilité** : Permettre l'ajout de nouvelles capacités via MCP
- **Autonomie** : Minimiser les interventions manuelles pour les tâches répétitives

## Comparaison Agent vs Chat

| Aspect | Mode Chat Actuel | Mode Agent Proposé |
|--------|------------------|-------------------|
| **Interaction** | Question/Réponse réactive | Proactif et autonome |
| **Capacités** | Conseil et génération de code | Exécution d'actions directes |
| **Workflow** | Linéaire, guidé par l'utilisateur | Planification et exécution de tâches complexes |
| **Contexte** | RAG local uniquement | RAG local + MCP servers |
| **Actions** | Copier/coller manuel | Modifications automatiques dans l'IDE |
| **Persistence** | Conversation temporaire | État et historique des tâches |
| **Extensibilité** | Plugins IntelliJ uniquement | MCP servers + plugins |
| **Feedback** | Textuel dans le chat | Visual + notifications + chat |

## Architecture Proposée

### Composants Principaux

```
OllamAssist Agent Architecture
├── Core/
│   ├── AgentCoordinator           # Chef d'orchestre principal
│   ├── TaskPlanner               # Planification et décomposition de tâches
│   ├── ExecutionEngine           # Moteur d'exécution des actions
│   └── StateManager             # Gestion d'état et persistance
├── Analysis/
│   ├── CodeAnalyzer              # Analyse statique du code
│   ├── ProjectAnalyzer           # Analyse de structure de projet
│   └── DependencyAnalyzer        # Analyse des dépendances
├── Actions/
│   ├── CodeActions               # Modifications de code
│   ├── FileActions               # Opérations sur fichiers
│   ├── BuildActions              # Compilation et tests
│   └── GitActions                # Opérations Git
├── MCP/
│   ├── MCPConnectionManager      # Gestion des connexions MCP
│   ├── MCPServerRegistry         # Registre des serveurs disponibles
│   ├── MCPProtocolHandler        # Gestion du protocole MCP
│   └── MCPCapabilityProvider     # Exposition des capacités MCP
├── UI/
│   ├── AgentPanel                # Interface agent principale
│   ├── TaskProgressViewer        # Visualisation des tâches
│   ├── MCPConfigPanel            # Configuration MCP
│   └── AgentSettingsPanel        # Paramètres agent
└── Communication/
    ├── ConversationManager       # Gestion des conversations
    ├── NotificationService       # Notifications utilisateur
    └── ResultPresenter           # Présentation des résultats
```

### Intégration MCP

#### Configuration MCP
```yaml
mcp:
  servers:
    - name: "filesystem"
      type: "builtin"
      capabilities: ["file_operations", "directory_traversal"]
    - name: "web-search"
      endpoint: "http://localhost:8080/mcp"
      auth:
        type: "api_key"
        key: "${WEB_SEARCH_API_KEY}"
      capabilities: ["web_search", "url_fetching"]
    - name: "code-analysis"
      endpoint: "http://localhost:8081/mcp"
      capabilities: ["static_analysis", "dependency_check"]
  connection:
    timeout: 30000
    retry_attempts: 3
    heartbeat_interval: 60000
```

#### Types de Serveurs MCP Supportés
1. **Serveurs Intégrés** : Système de fichiers, Git, compilation
2. **Serveurs Externes** : Recherche web, analyse de code, documentation
3. **Serveurs Personnalisés** : APIs spécifiques au projet ou à l'entreprise

## Prérequis Techniques

### Infrastructure
- **Java 21+** : Support des virtual threads pour la concurrence
- **LangChain4J 0.34+** : Dernières fonctionnalités d'agent
- **Jackson 2.15+** : Sérialisation JSON pour MCP
- **OkHttp 4.12+** : Client HTTP pour connexions MCP
- **SQLite 3.44+** : Persistance locale de l'état

### Dépendances Nouvelles
```gradle
dependencies {
    implementation 'dev.langchain4j:langchain4j-agent:0.34.0'
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'org.xerial:sqlite-jdbc:3.44.1.0'
    implementation 'io.github.cdimascio:dotenv-java:3.0.0'
    implementation 'org.springframework:spring-websocket:6.1.0'
}
```

### Configuration IntelliJ
- **Version Minimale** : IntelliJ IDEA 2024.3+
- **Permissions** : Accès en écriture aux fichiers projet
- **APIs Requises** : PSI, VFS, Project Model, Build System

### Serveurs MCP
- **Protocole** : MCP 1.0+ compatible
- **Transport** : HTTP/WebSocket
- **Authentification** : API Keys, OAuth2, Basic Auth
- **Formats** : JSON-RPC 2.0

## Dangers et Risques

### Risques Techniques

#### 1. Sécurité
- **Exécution de Code Arbitraire** : L'agent peut modifier/exécuter du code
- **Connexions MCP Non Sécurisées** : Risque d'exposition de données
- **Injection de Commandes** : Validation des entrées utilisateur critique
- **Accès Fichiers Sensibles** : Contrôle d'accès aux fichiers système

#### 2. Performance
- **Blocage UI** : Opérations longues doivent être asynchrones
- **Consommation Mémoire** : Gestion de l'état et cache des connexions MCP
- **Latence Réseau** : Dépendance aux serveurs MCP externes
- **Concurrence** : Gestion des tâches simultanées

#### 3. Fiabilité
- **Corruptions de Code** : Modifications automatiques incorrectes
- **États Incohérents** : Gestion de l'état distribué complexe
- **Pannes de Serveurs MCP** : Dégradation gracieuse requise
- **Rollback Complexe** : Annulation de tâches partiellement exécutées

### Risques Fonctionnels

#### 1. Expérience Utilisateur
- **Perte de Contrôle** : Actions automatiques non désirées
- **Complexité d'Interface** : Surcharge cognitive pour l'utilisateur
- **Feedback Insuffisant** : Manque de visibilité sur les actions
- **Interruption de Workflow** : Perturbation du développement

#### 2. Compatibilité
- **Versions IntelliJ** : Maintenance multi-versions complexe
- **Plugins Tiers** : Conflits avec autres plugins
- **Formats de Projet** : Support limité de certains types de projets
- **Serveurs MCP** : Compatibilité et versioning

## Points de Surveillance

### Métriques Techniques
```yaml
monitoring:
  performance:
    - agent_task_execution_time
    - mcp_connection_latency
    - memory_usage_agent_state
    - ui_responsiveness_index
  reliability:
    - task_success_rate
    - mcp_connection_failures
    - code_modification_errors
    - rollback_success_rate
  security:
    - unauthorized_file_access_attempts
    - mcp_authentication_failures
    - code_injection_detections
```

### Indicateurs Utilisateur
- **Satisfaction** : Enquêtes et feedback
- **Adoption** : Utilisation des fonctionnalités agent
- **Productivité** : Temps de développement et qualité du code
- **Erreurs** : Rapports de bugs et problèmes utilisateur

### Surveillance Opérationnelle
- **Logs Structurés** : Actions agent et communications MCP
- **Alertes** : Échecs critiques et problèmes de performance
- **Dashboards** : Métriques en temps réel
- **Audit Trail** : Traçabilité des modifications automatiques

## Plan de Migration

### Phase 1 : Infrastructure de Base (Sprint 1-2)
```
Objectifs:
- Création de l'architecture agent de base
- Implémentation du AgentCoordinator
- UI basique pour le mode agent
- Tests d'intégration fondamentaux

Livrables:
- Classes core de l'agent
- Interface utilisateur agent basique
- Tests unitaires et d'intégration
- Documentation technique de base
```

### Phase 2 : Capacités MCP (Sprint 3-4)
```
Objectifs:
- Intégration du protocole MCP
- Configuration des serveurs MCP
- Interface de gestion des connexions
- Serveurs MCP intégrés (filesystem, git)

Livrables:
- MCPConnectionManager fonctionnel
- Configuration MCP via UI
- Serveurs MCP de base opérationnels
- Documentation MCP
```

### Phase 3 : Actions Automatisées (Sprint 5-6)
```
Objectifs:
- Implémentation des actions de code
- Système de planification de tâches
- Gestion d'état et persistance
- Interface de progression des tâches

Livrables:
- Actions de modification de code
- TaskPlanner opérationnel
- StateManager avec persistance
- UI de suivi de progression
```

### Phase 4 : Intégration et Optimisation (Sprint 7-8)
```
Objectifs:
- Intégration complète agent/chat
- Optimisations de performance
- Tests de charge et sécurité
- Documentation utilisateur complète

Livrables:
- Mode hybride agent/chat
- Performance optimisée
- Tests de sécurité passés
- Guide utilisateur complet
```

## Stratégies de Rollback

### Rollback Technique
1. **Feature Flags** : Désactivation du mode agent sans redéploiement
2. **Mode Dégradé** : Retour automatique au mode chat en cas d'erreur
3. **Sauvegarde d'État** : Backup avant modifications automatiques
4. **Versioning** : Gestion des versions de configuration MCP

### Rollback Fonctionnel
1. **Undo Stack** : Annulation des actions agent par l'utilisateur
2. **Confirmation** : Validation utilisateur pour actions critiques
3. **Mode Manuel** : Bascule vers contrôle manuel à tout moment
4. **Restauration Projet** : Outils de restauration de l'état projet

## Configuration MCP Détaillée

### Structure de Configuration
```json
{
  "mcp": {
    "enabled": true,
    "default_timeout": 30000,
    "max_concurrent_connections": 10,
    "servers": [
      {
        "id": "filesystem-server",
        "name": "File System Operations",
        "type": "builtin",
        "enabled": true,
        "capabilities": [
          "file_read",
          "file_write",
          "directory_list",
          "file_search"
        ],
        "config": {
          "allowed_paths": ["${PROJECT_ROOT}"],
          "max_file_size": "10MB"
        }
      },
      {
        "id": "web-search-server",
        "name": "Web Search & Documentation",
        "type": "external",
        "enabled": false,
        "endpoint": "ws://localhost:8080/mcp",
        "auth": {
          "type": "api_key",
          "key_env": "WEB_SEARCH_API_KEY"
        },
        "capabilities": [
          "web_search",
          "url_fetch",
          "documentation_lookup"
        ],
        "config": {
          "max_results": 10,
          "timeout": 15000
        }
      }
    ],
    "security": {
      "validate_certificates": true,
      "allowed_domains": ["localhost", "*.company.com"],
      "rate_limiting": {
        "max_requests_per_minute": 100
      }
    }
  }
}
```

### Interface de Configuration UI
```
MCPConfigPanel:
├── Server List View
│   ├── Add/Remove servers
│   ├── Enable/Disable toggle
│   └── Test connection button
├── Server Details Form
│   ├── Basic info (name, endpoint)
│   ├── Authentication settings
│   ├── Capabilities selection
│   └── Advanced configuration
└── Security Settings
    ├── Certificate validation
    ├── Domain whitelist
    └── Rate limiting
```

## Critères de Succès

### Critères Techniques
- ✅ Exécution de tâches agent sans blocage UI
- ✅ Connexions MCP stables avec <2s latence
- ✅ Taux d'erreur <5% pour modifications automatiques
- ✅ Temps de réponse <500ms pour actions simples
- ✅ Compatibilité avec IntelliJ 2024.3+

### Critères Fonctionnels
- ✅ Interface agent intuitive et non-intrusive
- ✅ Configuration MCP simple via UI
- ✅ Rollback fiable des actions automatiques
- ✅ Intégration transparente avec workflow existant
- ✅ Documentation complète pour utilisateurs et développeurs

### Critères de Qualité
- ✅ Tests couvrant >80% du code agent
- ✅ Validation sécurité par audit externe
- ✅ Performance acceptable sous charge
- ✅ Feedback utilisateur positif (>80% satisfaction)
- ✅ Adoption progressive sans regression chat

## Conclusion

Cette migration vers le mode agent avec intégration MCP représente une évolution majeure d'OllamAssist. Elle nécessite une approche prudente et progressive, avec une attention particulière à la sécurité, la performance et l'expérience utilisateur.

Le succès de cette migration dépendra de :
1. **L'adoption progressive** avec possibilité de rollback
2. **La robustesse** de l'architecture MCP
3. **La qualité** de l'interface utilisateur agent
4. **La sécurité** des opérations automatisées
5. **La performance** des connexions MCP

## Implementation Progress

### ✅ **COMPLETED** (2025-01-13)

#### 1. **Unified Agent Mode Refactoring**
- **Issue**: Initial chat/agent toggle approach was outdated and non-modern
- **Solution**: Migration to unified agent mode with action validation
- **Result**: Unified interface where agent proposes actions that user can validate/reject

#### 2. **Core Agent Architecture**
```java
Core Agent Architecture:
├── fr.baretto.ollamassist.core.agent/
│   ├── AgentCoordinator           ✅ Implemented - Main orchestrator
│   ├── task/
│   │   ├── Task                   ✅ Implemented - Task model with statuses
│   │   ├── TaskResult            ✅ Implemented - Execution results
│   │   └── TaskPlanner           ✅ Implemented - Task decomposition
│   ├── execution/
│   │   ├── ExecutionEngine       ✅ Implemented - Execution engine
│   │   ├── CodeExecutor          ✅ Implemented - Code executor
│   │   ├── FileExecutor          ✅ Implemented - File operations
│   │   └── GitExecutor           ✅ Implemented - Git operations
│   └── state/
│       └── StateManager          ✅ Implemented - State management
```

#### 3. **Complete MCP Infrastructure**
```java
MCP Infrastructure:
├── fr.baretto.ollamassist.core.mcp/
│   ├── server/
│   │   ├── MCPServerRegistry     ✅ Implemented - Server registry with builtins
│   │   ├── MCPServerConfig       ✅ Implemented - Server configuration
│   │   └── MCPServerEditDialog   ✅ Implemented - Server editing UI
│   ├── connection/
│   │   ├── MCPConnectionManager  ✅ Implemented - Connection management
│   │   ├── BuiltinMCPConnection  ✅ Implemented - Builtin connections
│   │   ├── HttpMCPConnection     ✅ Implemented - HTTP connections
│   │   └── WebSocketMCPConnection ✅ Implemented - WebSocket connections
│   ├── capability/
│   │   └── MCPCapabilityProvider ✅ Implemented - MCP capabilities interface
│   └── protocol/
│       ├── MCPMessage           ✅ Implemented - Protocol messages
│       └── MCPResponse          ✅ Implemented - Protocol responses
```

#### 4. **Modern UI Components**
```java
Modern UI Components:
├── fr.baretto.ollamassist.core.agent.ui/
│   ├── ActionProposalCard       ✅ Implemented - Action validation cards
│   ├── TaskProgressPanel        ✅ Implemented - Task progress tracking
│   └── AgentPanel              ✅ Implemented - Main agent interface
└── fr.baretto.ollamassist.setting.mcp/
    ├── MCPConfigurationPanel    ✅ Implemented - MCP configuration
    └── MCPServerEditDialog      ✅ Implemented - Server editing dialog
```

#### 5. **IntelliJ Integration**
- **Services**: All IntelliJ services configured in `plugin.xml` ✅
- **Extension Points**: Configuration panels and MCP services ✅
- **Compilation**: Build successful, all components compile ✅
- **Architecture**: Follows IntelliJ Platform patterns ✅

#### 6. **Chat Interface Cleanup**
- **Toggle Removal**: Complete removal of obsolete chat/agent toggle ✅
- **Unified Interface**: Return to existing chat interface as base ✅
- **Code Clean**: Removal of all separate agent references ✅

### 🚧 **NEXT STEPS** (TODO)

#### 1. **Agent Integration in Chat Interface** (High Priority)
```java
TODO: Unified Agent Experience
├── Modify MessagesPanel to display ActionProposalCards
├── Integrate AgentCoordinator in NewUserMessageNotifier
├── Implement intention detection (question vs action)
└── Add action validation system in chat flow
```

#### 2. **Functional Implementations** (High Priority)
```java
TODO: Functional Implementations
├── Complete CodeExecutor implementations with real actions
├── Implement FileExecutor with IntelliJ PSI operations
├── Complete GitExecutor with real Git4Idea operations
└── Replace demo builtin MCP servers with real implementations
```

#### 3. **Planning System** (Medium Priority)
```java
TODO: Task Planning System
├── Improve TaskPlanner with real task decomposition
├── Implement user intention analysis (light NLP)
├── Add task dependency management
└── Implement rollback and undo actions
```

#### 4. **Advanced UX/UI** (Medium Priority)
```java
TODO: Advanced UX
├── Animations and transitions for ActionProposalCards
├── Toast notifications for successful/failed actions
├── Real-time progress indicators
└── Keyboard shortcuts for quick validation
```

#### 5. **Testing & Quality** (Low Priority)
```java
TODO: Testing & Quality
├── Unit tests for all agent components
├── MCP integration tests
├── UI tests with robot framework
└── Performance and memory tests
```

### 🎯 **Immediate Recommendations**

1. **Start with agent/chat integration**: Modify `NewUserMessageNotifier` to include agent logic
2. **Implement a simple action**: Start with CodeExecutor with basic refactoring
3. **Test UX**: Create prototype with ActionProposalCard in MessagesPanel
4. **Documentation**: Create usage examples for developers

### 🔄 **Key Architecture Decision**

**Unified Agent Mode vs Toggle**: The decision to move to unified mode was correct. UX is more modern and natural, similar to ChatGPT/Claude with capabilities. It eliminates cognitive friction and makes the agent more accessible.

---

**Author**: Claude Code
**Date**: 2025-01-13
**Version**: 1.1
**Status**: IN_PROGRESS