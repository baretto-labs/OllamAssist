package fr.baretto.ollamassist.chat.rag;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
public class IndexRegistry {

    public static final Charset CHARSET = StandardCharsets.UTF_8;
    private static final String USER_HOME = System.getProperty("user.home");
    private static final String OLLAMASSIST_DIR_FORMAT = "%s%s.ollamassist";
    private static final String PROJECTS_FILE_FORMAT = "%s%sindexed_projects.txt";
    public static final String OLLAMASSIST_DIR = String.format(OLLAMASSIST_DIR_FORMAT, USER_HOME, File.separator);
    private static final String PROJECTS_FILE = String.format(PROJECTS_FILE_FORMAT, OLLAMASSIST_DIR, File.separator);
    private static final String SEPARATOR = ",";
    private final Set<String> currentIndexations = new HashSet<>();


    public IndexRegistry() {
        ensureDirectoryExists();
        ensureFileExists();
    }

    public boolean isIndexed(String projectId) {
        if (currentIndexations.contains(projectId)) {
            return true;
        }
        Map<String, ProjectMetadata> indexedProjects = getIndexedProjects();
        ProjectMetadata metadata = indexedProjects.get(projectId);
        if (metadata == null) {
            return false;
        }
        if (metadata.isCorrupted()) {
            return false;
        }
        LocalDate sevenDaysAgo = LocalDate.now().minusDays(7);
        LocalDate lastIndexedDate = metadata.getLastIndexedDate();
        return lastIndexedDate != null && lastIndexedDate.isAfter(sevenDaysAgo);
    }

    public void markAsCurrentIndexation(String projectId) {
        currentIndexations.add(projectId);
    }

    public void removeFromCurrentIndexation(String projectId) {
        currentIndexations.remove(projectId);
    }

    public boolean indexationIsProcessing(String projectId) {
        return currentIndexations.contains(projectId);
    }

    public void markAsIndexed(String projectId) {
        Map<String, ProjectMetadata> projects = getIndexedProjects();
        projects.put(projectId, new ProjectMetadata(LocalDate.now(), false));
        writeProjectsToFile(projects);
    }

    public boolean isCorrupted(String projectId) {
        ProjectMetadata metadata = getIndexedProjects().get(projectId);
        return metadata != null && metadata.isCorrupted();
    }

    public void markAllAsCorrupted() {
        getIndexedProjects().keySet().forEach(this::markAsCorrupted);
    }

    public void markAsCleared(String projectId) {
        Map<String, ProjectMetadata> projects = getIndexedProjects();
        ProjectMetadata existing = projects.get(projectId);
        if (existing != null) {
            projects.put(projectId, new ProjectMetadata(existing.getLastIndexedDate(), false));
        } else {
            projects.put(projectId, new ProjectMetadata(LocalDate.now(), false));
        }
        writeProjectsToFile(projects);
    }

    public void markAsCorrupted(String projectId) {
        Map<String, ProjectMetadata> projects = getIndexedProjects();
        ProjectMetadata existing = projects.get(projectId);
        if (existing != null) {
            projects.put(projectId, new ProjectMetadata(existing.getLastIndexedDate(), true));
        } else {
            projects.put(projectId, new ProjectMetadata(LocalDate.now(), true));
        }
        writeProjectsToFile(projects);
    }

    public Map<String, ProjectMetadata> getIndexedProjects() {
        Map<String, ProjectMetadata> projects = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(PROJECTS_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith(",")) continue;

                String[] parts = line.split(SEPARATOR, 3);
                if (parts.length >= 2) {
                    String projectId = parts[0].trim();
                    try {
                        LocalDate date = LocalDate.parse(parts[1].trim());
                        boolean isCorrupted = parts.length >= 3 && Boolean.parseBoolean(parts[2].trim());
                        projects.put(projectId, new ProjectMetadata(date, isCorrupted));
                    } catch (DateTimeParseException e) {
                        log.warn("Invalid date format for project {}: {}", parts[0], parts[1]);
                    }
                } else {
                    log.info("Project {} needs reindexing (missing date)", parts[0]);
                }
            }
        } catch (IOException e) {
            log.error("Error reading indexed projects file", e);
        }
        return projects;
    }

    public void removeProject(String projectId) {
        Map<String, ProjectMetadata> projects = getIndexedProjects();
        if (projects.remove(projectId) != null) {
            writeProjectsToFile(projects);
        }
    }

    private void writeProjectsToFile(Map<String, ProjectMetadata> projects) {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(PROJECTS_FILE))) {
            for (Map.Entry<String, ProjectMetadata> entry : projects.entrySet()) {
                ProjectMetadata metadata = entry.getValue();
                writer.write(String.format("%s%s%s%s%s", entry.getKey(), SEPARATOR, metadata.getLastIndexedDate(), SEPARATOR, metadata.isCorrupted()));
                writer.newLine();
            }
        } catch (IOException e) {
            log.error("Error updating indexed projects file", e);
        }
    }

    private void ensureDirectoryExists() {
        try {
            Files.createDirectories(Paths.get(OLLAMASSIST_DIR));
        } catch (IOException e) {
            log.error("Error creating .ollamassist directory", e);
        }
    }

    private void ensureFileExists() {
        try {
            Path path = Paths.get(PROJECTS_FILE);
            if (Files.notExists(path)) {
                Files.createFile(path);
            }
        } catch (IOException e) {
            log.error("Error creating indexed projects file", e);
        }
    }

    public static class ProjectMetadata {
        @Getter
        private final LocalDate lastIndexedDate;
        private final boolean isCorrupted;

        ProjectMetadata(LocalDate lastIndexedDate, boolean isCorrupted) {
            this.lastIndexedDate = lastIndexedDate;
            this.isCorrupted = isCorrupted;
        }

        public boolean isCorrupted() {
            return isCorrupted;
        }
    }
}