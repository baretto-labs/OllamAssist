package fr.baretto.ollamassist.actions.refactor;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LanguageDetector {

    private static final Map<String, String> EXTENSION_TO_LANGUAGE = Map.ofEntries(
            Map.entry("java", "Java"),
            Map.entry("py", "Python"),
            Map.entry("js", "JavaScript"),
            Map.entry("ts", "TypeScript"),
            Map.entry("kt", "Kotlin"),
            Map.entry("go", "Go"),
            Map.entry("rs", "Rust"),
            Map.entry("cpp", "C++"),
            Map.entry("c", "C"),
            Map.entry("cs", "C#"),
            Map.entry("html", "HTML"),
            Map.entry("css", "CSS"),
            Map.entry("sql", "SQL"),
            Map.entry("sh", "Shell Script"),
            Map.entry("rb", "Ruby")
    );

    public static String getLanguageFromFileName(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return "text";
        }
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        return EXTENSION_TO_LANGUAGE.getOrDefault(extension, "text");
    }
}
