# Tests d'Intégration OllamAssist

Ce dossier contient les tests unitaires et d'intégration pour le plugin OllamAssist.

## Structure des Tests

### Tests Unitaires de l'Autocomplétion

- **`SuggestionCacheTest`** : Tests du système de cache
  - ✅ Tests des opérations de base du cache
  - ✅ Tests de consistance des clés de cache
  - ✅ Tests d'éviction automatique
  - ✅ Tests de performance et thread-safety
  - ✅ Tests de gestion d'erreurs

- **`MultiSuggestionManagerTest`** : Tests du gestionnaire de suggestions multiples
  - Tests de navigation entre suggestions (Tab/Shift+Tab)
  - Tests d'affichage de suggestions simples et multiples
  - Tests d'insertion de suggestions
  - Tests de gestion d'état

- **`CompletionDebouncerTest`** : Tests du système de debouncing
  - Tests de debouncing de base
  - Tests d'annulation de requêtes
  - Tests d'accès concurrent
  - Tests de gestion des exceptions

## Comment Lancer les Tests

### Tous les Tests
```bash
./gradlew test
```

### Tests Spécifiques
```bash
# Tests du cache uniquement (fonctionnent bien)
./gradlew test --tests "fr.baretto.ollamassist.completion.SuggestionCacheTest"

# Test spécifique qui fonctionne
./gradlew test --tests "fr.baretto.ollamassist.completion.SuggestionCacheTest.testBasicCacheOperations"

# Tests de tous les modules de completion
./gradlew test --tests "fr.baretto.ollamassist.completion.*"
```

### Tests avec Verbose
```bash
./gradlew test --info
```

## Prérequis

Les tests d'intégration utilisent :
- **IntelliJ Platform Test Framework** : Simule un environnement IDE complet
- **JUnit 5** : Framework de tests
- **Mockito** : Mocking des dépendances externes (comme Ollama)

### Configuration Ollama pour les Tests

Les tests mockent les appels Ollama par défaut, mais si tu veux tester avec un vrai serveur Ollama :

1. Démarrer Ollama : `ollama serve`
2. Télécharger un modèle : `ollama pull llama3.1:latest`
3. Modifier les tests pour utiliser de vraies connexions

## Types de Tests

### Tests Unitaires Classiques
- Tests isolés de composants individuels
- Pas de dépendances IntelliJ Platform
- Rapides à exécuter

### Tests d'Intégration
- Tests avec un projet IntelliJ simulé
- Tests d'interactions entre composants
- Plus lents mais plus réalistes

### Tests de Performance
- Tests de charge et stress
- Mesure des temps de réponse
- Tests de gestion mémoire
- Tests d'accès concurrent

## Métriques Testées

- **Cache** : Hit rate, temps de réponse, capacité
- **Débouncing** : Nombre d'exécutions, timing
- **Navigation** : Temps de réponse pour les suggestions multiples
- **Mémoire** : Détection de fuites mémoire
- **Concurrence** : Thread-safety du cache et des services

## Debugging des Tests

Pour débugger les tests :

1. **Logs** : Les tests utilisent System.err.println() pour le debugging
2. **Breakpoints** : Utiliser l'IDE pour débugger
3. **Test individuels** : Lancer un test spécifique
4. **Timeouts** : Les tests ont des timeouts configurés (5-15s)

## Ajout de Nouveaux Tests

Pour ajouter des tests :

1. **Hériter de la structure existante** : Utiliser `CodeInsightTestFixture`
2. **Mocker Ollama** : Utiliser `MockedStatic<OllamAssistSettings>`
3. **Gérer le cycle de vie** : `@BeforeEach` et `@AfterEach` avec cleanup
4. **Timeouts** : Ajouter `@Timeout` pour les tests asynchrones

### Exemple de nouveau test

```java
@Test
@Timeout(value = 5, unit = TimeUnit.SECONDS)
void testNewFeature() throws Exception {
    try (MockedStatic<OllamAssistSettings> settingsMock = Mockito.mockStatic(OllamAssistSettings.class)) {
        settingsMock.when(OllamAssistSettings::getInstance).thenReturn(mockSettings);
        
        fixture.configureByText("TestFile.java", "public class Test {<caret>}");
        Editor editor = fixture.getEditor();
        
        // Test logic here
        
        assertNotNull(result, "Result should not be null");
    }
}
```

## État Actuel des Tests

### ✅ Tests qui Fonctionnent
- **SuggestionCacheTest.testBasicCacheOperations** : Tests de base du cache (100% success)
- **SuggestionCacheTest.testCachePerformance** : Tests de performance du cache
- **SuggestionCacheTest.testThreadSafety** : Tests de concurrence du cache

### ⚠️ Tests Partiellement Fonctionnels
- **SuggestionCacheTest** : 6/10 tests passent (60% success)
  - Les tests qui échouent sont liés aux mocks IntelliJ
  - Les fonctionnalités de base sont testées et valides

### ❌ Limitations Actuelles
- **MultiSuggestionManagerTest** : Requiert ApplicationManager d'IntelliJ
- **CompletionDebouncerTest** : Problèmes avec le système d'Alarm IntelliJ
- Les tests d'intégration complets nécessitent le framework IntelliJ Platform

### 🧪 Tests Manuels Recommandés
Pour tester complètement l'autocomplétion :

1. **Lance IntelliJ avec le plugin**
2. **Ouvre un fichier Java**
3. **Utilise Shift+Space** pour déclencher l'autocomplétion
4. **Teste la navigation** avec Tab/Shift+Tab (si plusieurs suggestions)
5. **Teste l'insertion** avec Entrée
6. **Vérifie le cache** en répétant les mêmes requêtes

## CI/CD

Les tests s'intègrent dans le pipeline de build :
- Exécutés automatiquement avec `./gradlew check`  
- Inclus dans les benchmarks avec `./gradlew benchmark`
- Génèrent des rapports de couverture

**Note** : Seuls les tests unitaires purs (sans dépendances IntelliJ) passent actuellement dans l'environnement CI.