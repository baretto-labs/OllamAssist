package fr.baretto.ollamassist.agent.tools.files;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SourceRootResolver#extractMatchingPackagePath}.
 *
 * The source-root lookup methods ({@code correctWritePath}, {@code findInSourceRoots}) require
 * a live IntelliJ project and are integration-tested via the full agent loop.
 * The package-detection logic is pure Java and fully testable here.
 */
class SourceRootResolverTest {

    // -------------------------------------------------------------------------
    // Happy paths — bare package path detected
    // -------------------------------------------------------------------------

    @Test
    void detectPackagePath_javaFileWithMatchingPackage_returnsPackagePath() {
        String content = "package com.example.service;\n\npublic class MathService {}";
        String result = SourceRootResolver.extractMatchingPackagePath(
                "com/example/service/MathService.java", content);
        assertThat(result).isEqualTo("com/example/service");
    }

    @Test
    void detectPackagePath_kotlinFileWithMatchingPackage_returnsPackagePath() {
        // Kotlin package declaration has no trailing semicolon
        String content = "package com.example.controller\n\nclass PingController";
        String result = SourceRootResolver.extractMatchingPackagePath(
                "com/example/controller/PingController.kt", content);
        assertThat(result).isEqualTo("com/example/controller");
    }

    @Test
    void detectPackagePath_groovyFileWithMatchingPackage_returnsPackagePath() {
        String content = "package com.example.util;\n\nclass StringHelper {}";
        String result = SourceRootResolver.extractMatchingPackagePath(
                "com/example/util/StringHelper.groovy", content);
        assertThat(result).isEqualTo("com/example/util");
    }

    @Test
    void detectPackagePath_scalaFileWithMatchingPackage_returnsPackagePath() {
        String content = "package com.example.domain\n\nobject Order {}";
        String result = SourceRootResolver.extractMatchingPackagePath(
                "com/example/domain/Order.scala", content);
        assertThat(result).isEqualTo("com/example/domain");
    }

    @Test
    void detectPackagePath_defaultPackage_noPackageDeclaration_returnsNull() {
        // No package → file belongs to default package → path should be at root, no correction
        String content = "public class Main { public static void main(String[] args) {} }";
        String result = SourceRootResolver.extractMatchingPackagePath("Main.java", content);
        assertThat(result).isNull();
    }

    // -------------------------------------------------------------------------
    // Path already has source root prefix — no correction needed
    // -------------------------------------------------------------------------

    @Test
    void detectPackagePath_pathAlreadyHasSourcePrefix_returnsNull() {
        // "src/main/java/com/example/service" != "com/example/service" → no match
        String content = "package com.example.service;\n\npublic class MathService {}";
        String result = SourceRootResolver.extractMatchingPackagePath(
                "src/main/java/com/example/service/MathService.java", content);
        assertThat(result).isNull();
    }

    @Test
    void detectPackagePath_pathHasPartialPrefix_returnsNull() {
        // "java/com/example" != "com/example" → no match
        String content = "package com.example;\n\npublic class Foo {}";
        String result = SourceRootResolver.extractMatchingPackagePath(
                "java/com/example/Foo.java", content);
        assertThat(result).isNull();
    }

    // -------------------------------------------------------------------------
    // Non-JVM file types — never corrected
    // -------------------------------------------------------------------------

    @Test
    void detectPackagePath_xmlFile_returnsNull() {
        String content = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"/>";
        assertThat(SourceRootResolver.extractMatchingPackagePath("pom.xml", content)).isNull();
    }

    @Test
    void detectPackagePath_yamlFile_returnsNull() {
        String content = "spring:\n  datasource:\n    url: jdbc:h2:mem:testdb";
        assertThat(SourceRootResolver.extractMatchingPackagePath(
                "src/main/resources/application.yml", content)).isNull();
    }

    @Test
    void detectPackagePath_markdownFile_returnsNull() {
        assertThat(SourceRootResolver.extractMatchingPackagePath("README.md", "# My project"))
                .isNull();
    }

    @Test
    void detectPackagePath_shellScript_returnsNull() {
        assertThat(SourceRootResolver.extractMatchingPackagePath(
                "scripts/deploy.sh", "#!/bin/bash\necho hello")).isNull();
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    void detectPackagePath_nullPath_returnsNull() {
        assertThat(SourceRootResolver.extractMatchingPackagePath(null, "package a.b;")).isNull();
    }

    @Test
    void detectPackagePath_nullContent_returnsNull() {
        assertThat(SourceRootResolver.extractMatchingPackagePath("a/b/Foo.java", null)).isNull();
    }

    @Test
    void detectPackagePath_packageMismatch_returnsNull() {
        // Package is "com.example" but path parent is "org/example" → no match
        String content = "package com.example;\n\npublic class Foo {}";
        assertThat(SourceRootResolver.extractMatchingPackagePath(
                "org/example/Foo.java", content)).isNull();
    }

    @Test
    void detectPackagePath_nestedPackage_detectsCorrectly() {
        String content = "package fr.baretto.ollamassist.agent.tools;\n\npublic class ToolResult {}";
        String result = SourceRootResolver.extractMatchingPackagePath(
                "fr/baretto/ollamassist/agent/tools/ToolResult.java", content);
        assertThat(result).isEqualTo("fr/baretto/ollamassist/agent/tools");
    }

    @Test
    void detectPackagePath_packagePatternMatchesFirstOccurrence() {
        // Multiple potential matches in the content — only the first package statement matters
        String content = "package com.example.service;\n\n// package com.other;";
        String result = SourceRootResolver.extractMatchingPackagePath(
                "com/example/service/Foo.java", content);
        assertThat(result).isEqualTo("com/example/service");
    }
}
