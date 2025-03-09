package fr.baretto.ollamassist.chat.rag;

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

    private static final String USER_HOME = System.getProperty("user.home");
    public static final String OLLAMASSIST_DIR = USER_HOME + File.separator + ".ollamassist";
    public static final Charset CHARSET = StandardCharsets.UTF_8;
    private static final String PROJECTS_FILE = OLLAMASSIST_DIR + File.separator + "indexed_projects.txt";
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
        Map<String, LocalDate> indexedProjects = getIndexedProjects();
        LocalDate lastIndexedDate = indexedProjects.get(projectId);
        if (lastIndexedDate == null) {
            return false;
        }
        LocalDate sevenDaysAgo = LocalDate.now().minusDays(7);
        return !lastIndexedDate.isBefore(sevenDaysAgo);
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
        Map<String, LocalDate> projects = getIndexedProjects();
        projects.put(projectId, LocalDate.now());
        writeProjectsToFile(projects);
    }

    public Map<String, LocalDate> getIndexedProjects() {
        Map<String, LocalDate> projects = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(PROJECTS_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith(",")) continue;

                String[] parts = line.split(SEPARATOR, 2);
                if (parts.length == 2) {
                    try {
                        LocalDate date = LocalDate.parse(parts[1]);
                        projects.put(parts[0], date);
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
        Map<String, LocalDate> projects = getIndexedProjects();
        if (projects.remove(projectId) != null) {
            writeProjectsToFile(projects);
        }
    }

    private void writeProjectsToFile(Map<String, LocalDate> projects) {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(PROJECTS_FILE))) {
            for (Map.Entry<String, LocalDate> entry : projects.entrySet()) {
                writer.write(entry.getKey() + SEPARATOR + entry.getValue());
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
}