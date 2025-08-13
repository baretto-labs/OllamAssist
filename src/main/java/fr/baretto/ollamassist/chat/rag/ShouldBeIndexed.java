package fr.baretto.ollamassist.chat.rag;

import fr.baretto.ollamassist.setting.OllamAssistSettings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class ShouldBeIndexed implements PathMatcher {

    private static final String SEPARATOR = ";";
    protected Set<String> includedPaths;
    protected Set<String> excludedPaths;

    ShouldBeIndexed() {
        this.includedPaths = getSourcePatterns();
        this.excludedPaths = Set.of();
    }

    @Override
    public boolean matches(Path path) {
        String normalizedPath = path.toString().replace('\\', '/');
        boolean isIncluded = includedPaths.isEmpty();
        for (String inclusion : includedPaths) {
            if (normalizedPath.contains(inclusion)) {
                isIncluded = true;
                break;
            }
        }
        if (!isIncluded) {
            return false;
        }
        try {
            return Files.isRegularFile(path) && path.toFile().length() > 0;
        } catch (SecurityException e) {
            return false;
        }
    }

    private Set<String> getSourcePatterns() {
        return Arrays.stream(OllamAssistSettings.getInstance()
                        .getSources()
                        .split(SEPARATOR))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }
}
