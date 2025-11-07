# LangChain4J Patterns - Documentation de Recherche (Phase 1.1)

**Date:** 2025-01-06
**Statut:** ✅ Complété

Cette documentation compile les recherches effectuées sur les patterns LangChain4J nécessaires pour implémenter le système d'agents.

---

## 1. AI Services avec @Tool Annotation

### Concepts Clés

L'annotation `@Tool` permet de transformer n'importe quelle méthode Java en outil callable par le LLM. LangChain4j gère automatiquement :
- La conversion en `ToolSpecification`
- L'inclusion dans les requêtes LLM
- L'exécution quand le LLM décide d'appeler l'outil
- Le retour du résultat au LLM

### Syntaxe de Base

```java
@Tool("Returns the weather forecast for a given city")
public String getWeather(@P("The city name") String city) {
    return weatherService.fetch(city);
}
```

**Champs de @Tool:**
- `name`: Identifiant custom (défaut: nom de la méthode)
- `value`: Description human-readable de l'outil

**Annotation @P (paramètres):**
- `value`: Description du paramètre (mandatory)
- `required`: Boolean flag (défaut: `true`)

### Types de Paramètres Supportés

✅ **Primitives:** `int`, `double`, `boolean`
✅ **Objects:** `String`, `Integer`, `Double`, `Boolean`
✅ **POJOs custom** avec nested objects
✅ **Enums**
✅ **Collections:** `List<T>`, `Set<T>`, `Map<K,V>`
✅ **Sans paramètres**

Pour les POJOs complexes, utiliser `@Description` sur les champs :

```java
@Description("User information")
record User(
    String name,
    @Description("Contact email address")
    String email
) {}

@Tool
void processUser(User user) { }
```

### Types de Retour

| Type de retour | Comportement |
|---------------|--------------|
| `void` | Retourne "Success" au LLM |
| `String` | Envoyé tel quel au LLM |
| Autres types | Converti en JSON avant transmission |

```java
@Tool
void logEvent(String event) { }  // → "Success"

@Tool
String fetchData(String query) { }  // → Raw string

@Tool
Map<String, Object> getAnalytics() { }  // → JSON
```

### Best Practices

> "If a human can understand the purpose of a tool and how to use it, chances are that the LLM can too."

1. **Noms clairs:** `getWeatherForecast` pas `gw`
2. **Descriptions complètes:** Expliquer ce que fait l'outil et quand l'utiliser
3. **Documentation des paramètres:** Décrire le but et le format de chaque paramètre
4. **Spécificité des types:** Utiliser enums au lieu de strings pour valeurs contraintes

### Découverte Automatique des Tools

```java
interface Assistant {
    String chat(String message);
}

class Tools {
    @Tool("Searches the web")
    List<String> search(@P("Query terms") String query) {
        return searchEngine.find(query);
    }
}

Assistant assistant = AiServices.builder(Assistant.class)
    .chatModel(chatModel)
    .tools(new Tools())
    .build();
```

LangChain4j automatiquement:
- ✅ Découvre toutes les méthodes `@Tool`
- ✅ Génère les `ToolSpecification`
- ✅ Les inclut dans les requêtes LLM
- ✅ Exécute les méthodes matchées
- ✅ Retourne les résultats au LLM

### Features Avancées

**1. InvocationParameters (contexte extra):**
```java
@Tool
String getWeather(String city, InvocationParameters params) {
    String userId = params.get("userId");
    return weatherService.getForUser(city, userId);
}

String result = assistant.chat("Weather in London",
    InvocationParameters.from(Map.of("userId", "12345")));
```

**2. @ToolMemoryId (contexte utilisateur/session):**
```java
interface Assistant {
    String chat(@UserMessage String msg, @MemoryId String memoryId);
}

@Tool
void addEvent(CalendarEvent event, @ToolMemoryId String memoryId) {
    // Use memoryId to identify user's calendar
}
```

**3. Immediate Return (skip LLM reprocessing):**
```java
@Tool(returnBehavior = ReturnBehavior.IMMEDIATE)
double add(int a, int b) {
    return a + b;
}
```

### Limitations

- Aucune restriction sur static/non-static
- Tous niveaux de visibilité (public, private, etc.)
- Pas de restrictions spéciales sur l'implémentation

---

## 2. Structured Outputs (JSON Schema)

### Aperçu

LangChain4j supporte 3 approches (classées par fiabilité):

1. **JSON Schema** (le plus fiable) ⭐
2. **Prompting + JSON Mode**
3. **Prompting** (le moins fiable)

### Providers Supportés

JSON Schema est supporté par:
- ✅ Azure OpenAI
- ✅ Google AI Gemini
- ✅ Mistral
- ✅ **Ollama** ⭐ (important pour nous!)
- ✅ OpenAI

### Intégration Ollama

Pour activer JSON Schema avec Ollama:

```java
OllamaChatModel model = OllamaChatModel.builder()
    .baseUrl("http://localhost:11434")
    .modelName("llama3.2")
    .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)  // ← Important!
    .build();
```

Pour streaming:
```java
.responseSchema(jsonSchema)  // Spécifier le schema
```

**Note:** Ollama 0.5.7+ offre structured output natif où le JSON schema est automatiquement inclus dans la requête LLM quand un AI service retourne un object.

### Types de Schema

LangChain4j fournit:

- `JsonObjectSchema` – Objects avec propriétés
- `JsonStringSchema`, `JsonIntegerSchema`, `JsonNumberSchema` – Types scalaires
- `JsonArraySchema` – Collections
- `JsonEnumSchema` – Valeurs d'énumération
- `JsonReferenceSchema` – Structures récursives (**Azure OpenAI, OpenAI uniquement**, pas Ollama ❌)
- `JsonAnyOfSchema` – Polymorphisme (**support limité**, pas Ollama ❌)

### Champs Required

> "Required properties must be explicitly specified; otherwise, they are considered optional."

Utiliser `.required()` pour désigner les champs obligatoires.

**Important:** Par défaut, les champs sont **optionnels** pour éviter les hallucinations. Pour forcer required:

```java
@JsonProperty(required = true)
String mandatoryField;
```

### AI Services Approach (Recommandé)

L'API AI Services génère **automatiquement** le ResponseFormat basé sur le type de retour:

```java
interface Assistant {
    Person extractPersonFrom(String text);  // Auto-génère schema pour Person
}

record Person(
    String name,
    @Description("Contact email address")
    String email
) {}
```

### Limitations Clés

❌ **Pas supporté par Ollama:**
- `JsonReferenceSchema` (structures récursives)
- `JsonAnyOfSchema` (polymorphisme)

❌ **Pas supporté en général:**
- Types `Map`, `Date` avec JSON Schema
- Interfaces ou classes abstraites (polymorphisme)

✅ **Supporté:**
- POJOs simples et nested
- Collections (`List<T>`, `Set<T>`)
- Enums
- Types primitifs et wrappers

### Best Practices

1. Ajouter descriptions aux champs et types pour guider le LLM
2. Utiliser `@JsonProperty(required = true)` pour champs obligatoires
3. Préférer l'approche AI Services (auto-génération) quand possible
4. Tester avec différents modèles Ollama (certains sont meilleurs que d'autres)

---

## 3. Observability avec ChatModelListener

### Interface et Méthodes

`ChatModelListener` fournit 3 callbacks pour monitorer les interactions LLM:

1. **onRequest()** - Appelé avant l'appel à l'API LLM
2. **onResponse()** - Appelé après réception d'une réponse réussie
3. **onError()** - Appelé quand une erreur survient

### 1. onRequest Method

Reçoit `ChatModelRequestContext` contenant:
- Messages de chat et nom du modèle
- Paramètres de requête (temperature, topP, topK, penalties, max tokens, stop sequences)
- Tool specifications et response format
- Paramètres provider-specific (ex: OpenAI seed, logitBias, reasoningEffort)
- Identifiant du model provider
- **Attributes map** pour données custom

### 2. onResponse Method

Accessible via `ChatModelResponseContext`, capture:
- Contenu du message AI
- Metadata (ID, nom modèle, finish reason)
- **Token usage details** (input/output/total counts) ⭐
- Metadata provider-specific
- Requête chat originale
- **Attributes map**

### 3. onError Method

Via `ChatModelErrorContext`, fournit:
- Détails de l'exception
- Requête chat originale
- Information model provider
- **Attributes map**

### Attributes Map pour Corrélation

> "The attributes map allows passing information between the onRequest, onResponse, and onError methods of the same ChatModelListener, as well as between multiple ChatModelListeners."

**Use case:** Calculer la durée d'exécution

```java
@Override
public void onRequest(ChatModelRequestContext context) {
    context.attributes().put("request-start", Instant.now());
}

@Override
public void onResponse(ChatModelResponseContext context) {
    Instant start = (Instant) context.attributes().get("request-start");
    Duration duration = Duration.between(start, Instant.now());
    // Log duration
}
```

### Comportement d'Exécution

- ✅ Listeners s'exécutent **synchronously** dans l'ordre d'itération
- ✅ Chaque méthode appelée **seulement une fois** par requête (pas de retry callbacks)
- ✅ Exceptions loggées au niveau **WARN**; les listeners suivants continuent
- ⚠️ Pour `StreamingChatModel`, response/error callbacks s'exécutent sur **threads différents** que request callbacks → propagation manuelle des attributes recommandée

### Intégration Spring Boot

Dans Spring Boot, tous les beans `ChatModelListener` dans l'application context sont **automatiquement injectés** dans tous les `ChatModel` et `StreamingChatModel` créés par les starters LangChain4j.

```java
@Component
public class MyCustomListener implements ChatModelListener {
    @Override
    public void onRequest(ChatModelRequestContext context) { }

    @Override
    public void onResponse(ChatModelResponseContext context) { }

    @Override
    public void onError(ChatModelErrorContext context) { }
}
```

### Best Practices

1. **Attributes map:** Utiliser pour propager le contexte thread-local en streaming
2. **Exception handling:** Implémenter dans les callbacks pour prévenir les failures
3. **Provider-specific params:** Utiliser les classes de paramètres provider-specific pour observabilité détaillée
4. **Metrics:** Capturer token usage, duration, error rates

---

## 4. Multi-Agent Patterns (Recherche en cours)

### État de la Recherche

⚠️ **Note:** La recherche web pour "LangChain4J multi-agent orchestrator pattern AgenticScope" a échoué (service unavailable).

### Informations Disponibles

D'après la documentation LangChain4j existante:

**Sequential Workflows:**
- Agents s'exécutent un après l'autre
- État partagé via attributes map
- Chaque agent a un `outputKey` dédié pour ses résultats

**AgenticScope:**
- Gère la communication inter-agent
- Pattern de coordination entre agents
- Documentation détaillée à rechercher ultérieurement

### Actions Requises

- [ ] Rechercher documentation AgenticScope dans GitHub langchain4j
- [ ] Consulter exemples multi-agents dans le repo
- [ ] Vérifier si Ollama supporte les features multi-agents
- [ ] Documenter les patterns de coordination découverts

---

## Décisions d'Architecture Validées

### ✅ Decision 1: @Tool pour Agents Spécialisés

**Rationale confirmé:**
- Découverte automatique par le LLM ✅
- Conversion automatique en ToolSpecification ✅
- Execution automatique par LangChain4j ✅
- Support des types complexes (POJOs, Collections) ✅
- InvocationParameters pour contexte extra ✅

**Parfait pour notre use case:** Chaque agent spécialisé expose ses @Tool methods.

### ✅ Decision 2: Structured Outputs pour Plan Generation

**Rationale confirmé:**
- JSON Schema supporté par Ollama ✅
- Auto-génération depuis types de retour Java ✅
- Type-safe et validé ✅
- Élimine les erreurs de parsing ✅
- Champs required/optional configurables ✅

**Parfait pour notre use case:** PlanGenerator retourne un `Plan` POJO avec structured output.

**Limitations identifiées:**
- ❌ Pas de JsonReferenceSchema (récursion) avec Ollama
- ❌ Pas de JsonAnyOfSchema (polymorphisme) avec Ollama
- ✅ Mais on n'en a pas besoin pour notre Plan simple!

### ✅ Decision 3: ChatModelListener pour Observability

**Rationale confirmé:**
- Callbacks avant/après/erreur ✅
- Token usage tracking ✅
- Attributes map pour corrélation ✅
- Intégration Spring Boot automatique ✅
- Exécution synchrone et ordonnée ✅

**Parfait pour notre use case:** `AgentObservabilityChatModelListener` capture tokens, durée, erreurs.

---

## Patterns d'Implémentation Recommandés

### Pattern 1: Agent Spécialisé avec Tools

```java
public class RagSearchAgent implements Agent {

    @Tool("Search for code in the project using semantic search")
    public ToolResult searchCode(
        @P("search query describing what to find") String query,
        @P(value = "maximum number of results", required = false) Integer maxResults
    ) {
        // 1. Create embedding
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // 2. Search in vector store
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(
            queryEmbedding,
            maxResults != null ? maxResults : 5,
            0.7
        );

        // 3. Convert to SourceReferences
        List<SourceReference> sources = matches.stream()
            .map(this::toSourceReference)
            .collect(Collectors.toList());

        // 4. Return result
        return ToolResult.success(formatResults(sources), sources);
    }
}
```

### Pattern 2: Orchestrator avec Structured Output

```java
interface PlanGeneratorService {
    Plan generatePlan(@UserMessage String userRequest);
}

record Plan(
    @Description("Overall goal of the plan")
    String goal,

    @Description("List of steps to execute")
    List<PlanStep> steps,

    @Description("Estimated duration in seconds")
    @JsonProperty(required = false)
    Integer estimatedDuration
) {}

record PlanStep(
    @Description("Step number starting from 1")
    int stepNumber,

    @Description("Agent type to use")
    AgentType agentType,

    @Description("Action to perform")
    String action,

    @Description("Why this step is needed")
    String justification
) {}

// Usage
PlanGeneratorService generator = AiServices.builder(PlanGeneratorService.class)
    .chatModel(chatModel)  // Must support JSON Schema (Ollama 0.5.7+)
    .build();

Plan plan = generator.generatePlan("Find all TODO comments in the codebase");
```

### Pattern 3: Observability avec ChatModelListener

```java
public class AgentObservabilityChatModelListener implements ChatModelListener {
    private final ObservabilityCollector collector;
    private final String currentStepId;

    @Override
    public void onRequest(ChatModelRequestContext context) {
        context.attributes().put("request-start", Instant.now());
        context.attributes().put("step-id", currentStepId);

        StepTrace step = collector.findStep(currentStepId);
        if (step != null) {
            step.addLog("LLM Request: " + context.chatRequest().messages().size() + " messages");
        }
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        Instant start = (Instant) context.attributes().get("request-start");
        Duration duration = Duration.between(start, Instant.now());

        TokenUsage tokenUsage = context.chatResponse().metadata().tokenUsage();
        int inputTokens = tokenUsage.inputTokenCount();
        int outputTokens = tokenUsage.outputTokenCount();

        // Record metrics
        collector.getTrace().getMetrics()
            .recordLLMCall(inputTokens, outputTokens, duration);

        // Log response
        StepTrace step = collector.findStep(currentStepId);
        if (step != null) {
            step.addLog(String.format("LLM Response: %d tokens in %d ms",
                outputTokens, duration.toMillis()));
        }
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        Throwable error = context.error();
        StepTrace step = collector.findStep(currentStepId);
        if (step != null) {
            step.addLog("LLM Error: " + error.getMessage());
        }
        collector.getTrace().getMetrics().incrementFailures();
    }
}
```

---

## Configuration Ollama Requise

Pour activer toutes les features nécessaires:

```java
OllamaChatModel chatModel = OllamaChatModel.builder()
    .baseUrl("http://localhost:11434")
    .modelName("llama3.2")  // ou autre modèle compatible
    .temperature(0.3)  // Plus bas pour planning (déterministe)
    .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)  // Pour structured outputs
    .build();
```

**Versions requises:**
- Ollama >= 0.5.7 (structured outputs)
- Modèle compatible avec JSON Schema (llama3.2, mistral, etc.)

---

## Prochaines Étapes (Phase 1)

- [x] 1.1: Étudier LangChain4J (agents, tools, structured outputs) ✅
- [ ] 1.2: Analyser capacités d'observabilité (en cours)
- [ ] 1.3: Documenter patterns d'implémentation (en cours)
- [ ] 1.4: Rechercher AgenticScope et multi-agent patterns
- [ ] 1.5: Créer diagrammes d'architecture
- [ ] 1.6: Finaliser mise à jour de CLAUDE.md avec learnings

---

## Références

- **Tools Documentation:** https://docs.langchain4j.dev/tutorials/tools/
- **Structured Outputs:** https://docs.langchain4j.dev/tutorials/structured-outputs/
- **Observability:** https://docs.langchain4j.dev/tutorials/observability/
- **AI Services:** https://docs.langchain4j.dev/tutorials/ai-services/
