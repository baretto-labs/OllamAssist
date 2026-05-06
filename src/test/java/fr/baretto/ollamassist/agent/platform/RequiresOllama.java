package fr.baretto.ollamassist.agent.platform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a platform test that requires a running Ollama instance.
 * Tests with this annotation are skipped when Ollama is not reachable at
 * {@code platformTest.ollamaUrl} (default: http://localhost:11434).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresOllama {}
