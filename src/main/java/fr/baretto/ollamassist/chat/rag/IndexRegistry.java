package fr.baretto.ollamassist.chat.rag;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

//@TODO replace by a service
@Slf4j
public class IndexRegistry {

    private static final String USER_HOME = System.getProperty("user.home");
    public static final String OLLAMASSIST_DIR = USER_HOME + File.separator + ".ollamassist";
    private static final String PROJECTS_FILE = OLLAMASSIST_DIR + File.separator + "indexed_projects.txt";

    public IndexRegistry() {
        ensureDirectoryExists();
        ensureFileExists();
    }

    public boolean isIndexed(String projectId) {
        return getIndexedProjects().contains(projectId);
    }

    public void markAsIndexed(String projectId) {
        if (!isIndexed(projectId)) {
            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(PROJECTS_FILE), StandardOpenOption.APPEND)) {
                writer.write(projectId);
                writer.newLine();
            } catch (IOException e) {
                log.error("Error adding project to indexed list", e);
            }
        }
    }

    public Set<String> getIndexedProjects() {
        Set<String> projects = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(PROJECTS_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                projects.add(line.trim());
            }
        } catch (IOException e) {
            log.error("Error reading indexed projects file", e);
        }
        return projects;
    }

    public void removeProject(String projectId) {
        Set<String> projects = getIndexedProjects();
        if (projects.remove(projectId)) {
            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(PROJECTS_FILE))) {
                for (String project : projects) {
                    writer.write(project);
                    writer.newLine();
                }
            } catch (IOException e) {
                log.error("Error updating indexed projects file", e);
            }
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
