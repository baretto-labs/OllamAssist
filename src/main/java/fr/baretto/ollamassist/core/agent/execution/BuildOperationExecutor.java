package fr.baretto.ollamassist.core.agent.execution;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import fr.baretto.ollamassist.core.agent.security.InputValidator;
import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Exécuteur pour les opérations de build/compilation
 */
@Slf4j
public class BuildOperationExecutor implements ExecutionEngine.TaskExecutor {

    private final Project project;

    public BuildOperationExecutor(Project project) {
        this.project = project;
    }

    @Override
    public TaskResult execute(Task task) {
        log.debug("Executing build operation task: {}", task.getId());

        try {
            String operation = task.getParameter("operation", String.class);
            if (operation == null) {
                return TaskResult.failure("Paramètre 'operation' manquant");
            }

            // SECURITY: Validate build operation against whitelist to prevent command injection
            try {
                InputValidator.validateBuildOperation(operation);
            } catch (InputValidator.ValidationException e) {
                log.warn("Build operation validation failed: {}", e.getMessage());
                return TaskResult.failure("Opération de build invalide: " + e.getMessage());
            }

            VirtualFile projectRoot = project.getBaseDir();
            if (projectRoot == null) {
                return TaskResult.failure("Impossible de déterminer le répertoire racine du projet");
            }

            return switch (operation.toLowerCase()) {
                case "compile", "build" -> runBuild(task, projectRoot);
                case "test" -> runTests(task, projectRoot);
                case "clean" -> runClean(task, projectRoot);
                case "package", "jar" -> runPackage(task, projectRoot);
                case "diagnostics" -> runDiagnostics(task, projectRoot);
                default -> TaskResult.failure("Opération de build non supportée: " + operation);
            };

        } catch (Exception e) {
            log.error("Error in build operation execution", e);
            return TaskResult.failure("Erreur lors de l'opération de build", e);
        }
    }

    private TaskResult runBuild(Task task, VirtualFile projectRoot) {
        return executeCommand(task, projectRoot, getBuildCommand(projectRoot), "build");
    }

    private TaskResult runTests(Task task, VirtualFile projectRoot) {
        return executeCommand(task, projectRoot, getTestCommand(projectRoot), "test");
    }

    private TaskResult runClean(Task task, VirtualFile projectRoot) {
        return executeCommand(task, projectRoot, getCleanCommand(projectRoot), "clean");
    }

    private TaskResult runPackage(Task task, VirtualFile projectRoot) {
        return executeCommand(task, projectRoot, getPackageCommand(projectRoot), "package");
    }

    private TaskResult runDiagnostics(Task task, VirtualFile projectRoot) {
        return executeCommand(task, projectRoot, getDiagnosticsCommand(projectRoot), "diagnostics");
    }

    private String[] getBuildCommand(VirtualFile projectRoot) {
        // Détecter le type de projet et retourner la commande appropriée
        if (new File(projectRoot.getPath(), "gradlew").exists()) {
            return new String[]{"./gradlew", "build"};
        } else if (new File(projectRoot.getPath(), "gradlew.bat").exists()) {
            return new String[]{"gradlew.bat", "build"};
        } else if (new File(projectRoot.getPath(), "pom.xml").exists()) {
            return new String[]{"mvn", "compile"};
        } else if (new File(projectRoot.getPath(), "package.json").exists()) {
            return new String[]{"npm", "run", "build"};
        } else {
            return new String[]{"make"};
        }
    }

    private String[] getTestCommand(VirtualFile projectRoot) {
        if (new File(projectRoot.getPath(), "gradlew").exists()) {
            return new String[]{"./gradlew", "test"};
        } else if (new File(projectRoot.getPath(), "gradlew.bat").exists()) {
            return new String[]{"gradlew.bat", "test"};
        } else if (new File(projectRoot.getPath(), "pom.xml").exists()) {
            return new String[]{"mvn", "test"};
        } else if (new File(projectRoot.getPath(), "package.json").exists()) {
            return new String[]{"npm", "test"};
        } else {
            return new String[]{"make", "test"};
        }
    }

    private String[] getCleanCommand(VirtualFile projectRoot) {
        if (new File(projectRoot.getPath(), "gradlew").exists()) {
            return new String[]{"./gradlew", "clean"};
        } else if (new File(projectRoot.getPath(), "gradlew.bat").exists()) {
            return new String[]{"gradlew.bat", "clean"};
        } else if (new File(projectRoot.getPath(), "pom.xml").exists()) {
            return new String[]{"mvn", "clean"};
        } else if (new File(projectRoot.getPath(), "package.json").exists()) {
            return new String[]{"npm", "run", "clean"};
        } else {
            return new String[]{"make", "clean"};
        }
    }

    private String[] getPackageCommand(VirtualFile projectRoot) {
        if (new File(projectRoot.getPath(), "gradlew").exists()) {
            return new String[]{"./gradlew", "jar"};
        } else if (new File(projectRoot.getPath(), "gradlew.bat").exists()) {
            return new String[]{"gradlew.bat", "jar"};
        } else if (new File(projectRoot.getPath(), "pom.xml").exists()) {
            return new String[]{"mvn", "package"};
        } else if (new File(projectRoot.getPath(), "package.json").exists()) {
            return new String[]{"npm", "pack"};
        } else {
            return new String[]{"make", "package"};
        }
    }

    private String[] getDiagnosticsCommand(VirtualFile projectRoot) {
        // Commandes pour obtenir les diagnostics de compilation
        if (new File(projectRoot.getPath(), "gradlew").exists()) {
            return new String[]{"./gradlew", "compileJava", "--console=plain"};
        } else if (new File(projectRoot.getPath(), "gradlew.bat").exists()) {
            return new String[]{"gradlew.bat", "compileJava", "--console=plain"};
        } else if (new File(projectRoot.getPath(), "pom.xml").exists()) {
            return new String[]{"mvn", "compile", "-q"};
        } else if (new File(projectRoot.getPath(), "package.json").exists()) {
            return new String[]{"npm", "run", "build"};
        } else {
            return new String[]{"make", "compile"};
        }
    }

    private TaskResult executeCommand(Task task, VirtualFile projectRoot, String[] command, String operationName) {
        try {
            GeneralCommandLine commandLine = new GeneralCommandLine(command);
            commandLine.setWorkDirectory(new File(projectRoot.getPath()));

            Integer timeoutSeconds = task.getParameter("timeout", Integer.class);
            int timeout = timeoutSeconds != null ? timeoutSeconds : 300; // 5 minutes par défaut

            OSProcessHandler processHandler = new OSProcessHandler(commandLine);
            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();
            CountDownLatch latch = new CountDownLatch(1);

            processHandler.addProcessListener(new ProcessListener() {
                @Override
                public void startNotified(ProcessEvent event) {
                }

                @Override
                public void processTerminated(ProcessEvent event) {
                    latch.countDown();
                }

                @Override
                public void onTextAvailable(ProcessEvent event, Key outputType) {
                    if (outputType.toString().contains("STDOUT")) {
                        output.append(event.getText());
                    } else if (outputType.toString().contains("STDERR")) {
                        errorOutput.append(event.getText());
                    }
                }
            });

            processHandler.startNotify();

            if (latch.await(timeout, TimeUnit.SECONDS)) {
                int exitCode = processHandler.getExitCode();
                if (exitCode == 0) {
                    return TaskResult.success("Opération " + operationName + " terminée avec succès.\nSortie:\n" + output);
                } else {
                    return TaskResult.failure("Opération " + operationName + " échouée (code: " + exitCode + ").\nErreur:\n" + errorOutput);
                }
            } else {
                processHandler.destroyProcess();
                return TaskResult.failure("Opération " + operationName + " interrompue (timeout de " + timeout + " secondes)");
            }

        } catch (ExecutionException e) {
            log.error("Error executing {} command", operationName, e);
            return TaskResult.failure("Erreur d'exécution pour " + operationName + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TaskResult.failure("Opération " + operationName + " interrompue");
        }
    }

    @Override
    public boolean canExecute(Task task) {
        return task.getType() == Task.TaskType.BUILD_OPERATION;
    }

    @Override
    public String getExecutorName() {
        return "BuildOperationExecutor";
    }
}