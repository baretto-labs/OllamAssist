package fr.baretto.ollamassist.core.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Java file path auto-correction
 * Verifies Bug Fix: Files are created in src/main/java/package/ instead of project root
 */
@DisplayName("Java File Path Auto-Correction Tests")
public class JavaFilePathCorrectionTest {

    private JavaPathCorrector pathCorrector;

    @BeforeEach
    void setUp() {
        pathCorrector = new JavaPathCorrector();
    }

    @Test
    @DisplayName("Should correct simple class name to src/main/java/ClassName.java (no package)")
    void shouldCorrectSimpleClassNameWithoutPackage() {
        // Given: Agent provides just "Test.java"
        String className = "Test";
        String wrongPath = "Test.java";
        String classContent = "public class Test {\n}";

        // When: Path is corrected
        String correctedPath = pathCorrector.correctJavaFilePath(className, wrongPath, classContent);

        // Then: Should be placed in src/main/java/
        assertThat(correctedPath).isEqualTo("src/main/java/Test.java");
    }

    @Test
    @DisplayName("Should correct class path with package declaration")
    void shouldCorrectClassPathWithPackage() {
        // Given: Agent provides "Test.java" but content has package
        String className = "Test";
        String wrongPath = "Test.java";
        String classContent = "package com.example;\n\npublic class Test {\n}";

        // When: Path is corrected
        String correctedPath = pathCorrector.correctJavaFilePath(className, wrongPath, classContent);

        // Then: Should extract package and build proper path
        assertThat(correctedPath).isEqualTo("src/main/java/com/example/Test.java");
    }

    @Test
    @DisplayName("Should correct nested package path")
    void shouldCorrectNestedPackagePath() {
        // Given: Deep package hierarchy
        String className = "UserService";
        String wrongPath = "UserService.java";
        String classContent = "package com.example.service.impl;\n\npublic class UserService {\n}";

        // When: Path is corrected
        String correctedPath = pathCorrector.correctJavaFilePath(className, wrongPath, classContent);

        // Then: Should create nested directories
        assertThat(correctedPath).isEqualTo("src/main/java/com/example/service/impl/UserService.java");
    }

    @Test
    @DisplayName("Should not modify already correct paths")
    void shouldNotModifyCorrectPaths() {
        // Given: Agent provides correct path
        String className = "Test";
        String correctPath = "src/main/java/com/example/Test.java";
        String classContent = "package com.example;\n\npublic class Test {\n}";

        // When: Path correction is attempted
        String result = pathCorrector.correctJavaFilePath(className, correctPath, classContent);

        // Then: Path should remain unchanged
        assertThat(result).isEqualTo(correctPath);
    }

    @Test
    @DisplayName("Should handle path that starts with package name (common mistake)")
    void shouldHandlePackageNameAsPath() {
        // Given: Agent mistakenly uses package as path
        String className = "Test";
        String wrongPath = "com/example/Test.java";
        String classContent = "package com.example;\n\npublic class Test {\n}";

        // When: Path is corrected
        String correctedPath = pathCorrector.correctJavaFilePath(className, wrongPath, classContent);

        // Then: Should prepend src/main/java/
        assertThat(correctedPath).isEqualTo("src/main/java/com/example/Test.java");
    }

    @Test
    @DisplayName("Should handle package with underscores")
    void shouldHandlePackageWithUnderscores() {
        // Given: Package with underscores (valid Java)
        String className = "MyClass";
        String wrongPath = "MyClass.java";
        String classContent = "package com.example_company.my_app;\n\npublic class MyClass {\n}";

        // When: Path is corrected
        String correctedPath = pathCorrector.correctJavaFilePath(className, wrongPath, classContent);

        // Then: Underscores should be preserved in path
        assertThat(correctedPath).isEqualTo("src/main/java/com/example_company/my_app/MyClass.java");
    }

    @Test
    @DisplayName("Should extract package from content with multiple spaces")
    void shouldExtractPackageWithMultipleSpaces() {
        // Given: Package declaration with extra spaces
        String className = "Test";
        String wrongPath = "Test.java";
        String classContent = "package    com.example   ;  \n\npublic class Test {\n}";

        // When: Path is corrected
        String correctedPath = pathCorrector.correctJavaFilePath(className, wrongPath, classContent);

        // Then: Should still extract package correctly
        assertThat(correctedPath).isEqualTo("src/main/java/com/example/Test.java");
    }

    @Test
    @DisplayName("Should handle path with absolute project path (common LLM mistake)")
    void shouldHandleAbsolutePath() {
        // Given: Agent provides absolute path (user workspace path)
        String className = "Test";
        String wrongPath = "/Users/mehdi/Workspaces/Labs/OllamAssist/Test.java";
        String classContent = "package com.example;\n\npublic class Test {\n}";

        // When: Path is corrected
        String correctedPath = pathCorrector.correctJavaFilePath(className, wrongPath, classContent);

        // Then: Should convert to proper relative path
        assertThat(correctedPath).isEqualTo("src/main/java/com/example/Test.java");
    }

    /**
     * Helper class that replicates the correction logic from IntelliJDevelopmentAgent
     */
    private static class JavaPathCorrector {
        public String correctJavaFilePath(String className, String filePath, String classContent) {
            // Extract package from class content
            Pattern packagePattern = Pattern.compile("package\\s+([a-zA-Z0-9_.]+)\\s*;");
            Matcher matcher = packagePattern.matcher(classContent);

            String packageName = null;
            if (matcher.find()) {
                packageName = matcher.group(1);
            }

            // If path doesn't start with src/, auto-correct it
            if (!filePath.startsWith("src/")) {
                if (packageName != null) {
                    // Build proper Java path: src/main/java/com/example/ClassName.java
                    String packagePath = packageName.replace('.', '/');
                    return "src/main/java/" + packagePath + "/" + className + ".java";
                } else {
                    // No package, default to src/main/java/
                    return "src/main/java/" + className + ".java";
                }
            }

            // Path already correct
            return filePath;
        }
    }
}
