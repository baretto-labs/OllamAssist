package fr.baretto.ollamassist.chat.rag;

import fr.baretto.ollamassist.setting.OllamAssistSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.List;

public class ShouldBeIndexed implements PathMatcher {

    private static final String SEPARATOR = ";";
    protected List<String> includedFiles;
    protected List<String> excludedFiles;

    ShouldBeIndexed() {
        this.includedFiles = getSourcePatterns();
        this.excludedFiles = List.of("target", "build", ".github", ".git", ".idea", ".gradle");
    }

    @Override
    public boolean matches(Path path) {
        String normalizedPath = path.toString().replace('\\', '/');

        for (String exclusion : excludedFiles) {
            if (normalizedPath.contains(exclusion)) {
                return false;
            }
        }

        boolean isIncluded = includedFiles.isEmpty();
        for (String inclusion : includedFiles) {
            if (normalizedPath.contains(inclusion)) {
                isIncluded = true;
                break;
            }
        }

        if (!isIncluded) {
            return false;
        }
        try {
            return Files.isRegularFile(path);
        } catch (SecurityException e) {
            return false;
        }
    }

    private List<String> getSourcePatterns() {
        return Arrays.stream(OllamAssistSettings.getInstance()
                        .getSources()
                        .split(SEPARATOR))
                .filter(s -> !s.isBlank())
                .toList();
    }
}
