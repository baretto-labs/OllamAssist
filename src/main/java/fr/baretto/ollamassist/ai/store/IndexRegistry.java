package fr.baretto.ollamassist.ai.store;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

//@TODO replace by a service
public class IndexRegistry {

    private static final String USER_HOME = System.getProperty("user.home");
    public static final String OLLAMASSIST_DIR = USER_HOME + File.separator + ".ollamassist";
    private static final String PROJECTS_FILE = OLLAMASSIST_DIR + File.separator + "projects.txt";

    public IndexRegistry() {
        ensureDirectoryExists();
        ensureFileExists();
    }

    public boolean isIndexed(String projectId) {
        return getIndexedProjects().contains(projectId);
    }

    public void addProject(String projectId) {
        if (!isIndexed(projectId)) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(PROJECTS_FILE, true))) {
                writer.write(projectId);
                writer.newLine();
            } catch (IOException e) {
                throw new RuntimeException("Error adding project to indexed list", e);
            }
        }
    }

    public Set<String> getIndexedProjects() {
        Set<String> projects = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(PROJECTS_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                projects.add(line.trim());
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading indexed projects file", e);
        }
        return projects;
    }

    public void removeProject(String projectId) {
        Set<String> projects = getIndexedProjects();
        if (projects.remove(projectId)) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(PROJECTS_FILE))) {
                for (String project : projects) {
                    writer.write(project);
                    writer.newLine();
                }
            } catch (IOException e) {
                throw new RuntimeException("Error updating indexed projects file", e);
            }
        }
    }

    private void ensureDirectoryExists() {
        File dir = new File(OLLAMASSIST_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("Error creating .ollamassist directory");
        }
    }

    private void ensureFileExists() {
        File file = new File(PROJECTS_FILE);
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (IOException e) {
            throw new RuntimeException("Error creating indexed projects file", e);
        }
    }
}
