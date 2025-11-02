package fr.baretto.ollamassist.core.agent.ui;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBColor;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Indicateur de progression pour les tâches agent avec UI temps réel
 */
public class TaskProgressIndicator extends JPanel {

    private final JProgressBar progressBar;
    private final JLabel statusLabel;
    private final JLabel detailsLabel;
    private final JButton cancelButton;
    private final Timer updateTimer;

    private boolean cancelled = false;
    private CompletableFuture<TaskResult> currentTask;

    public TaskProgressIndicator() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("Agent Task Progress"));

        // Progress bar
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");

        // Status and details labels
        statusLabel = new JLabel("Waiting for task...");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));

        detailsLabel = new JLabel(" ");
        detailsLabel.setFont(detailsLabel.getFont().deriveFont(Font.PLAIN, 10f));
        detailsLabel.setForeground(JBColor.GRAY);

        // Cancel button
        cancelButton = new JButton("Cancel");
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(this::onCancelClicked);

        // Layout
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(statusLabel, BorderLayout.CENTER);
        topPanel.add(cancelButton, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);
        add(progressBar, BorderLayout.CENTER);
        add(detailsLabel, BorderLayout.SOUTH);

        // Update timer for smooth progress
        updateTimer = new Timer(100, e -> updateProgress());
    }

    /**
     * Démarre une nouvelle tâche avec indicateur de progression
     */
    public CompletableFuture<TaskResult> executeTask(
            String taskName,
            Supplier<TaskResult> taskExecutor,
            Consumer<String> progressCallback) {

        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(true);
        }

        cancelled = false;
        progressBar.setIndeterminate(true);
        progressBar.setString("Starting...");
        statusLabel.setText(taskName);
        detailsLabel.setText("Initializing task execution...");
        cancelButton.setEnabled(true);
        updateTimer.start();

        currentTask = CompletableFuture.supplyAsync(() -> {
            try {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(10);
                    progressBar.setString("Executing...");
                    statusLabel.setText(taskName + " - Running");
                    detailsLabel.setText("Task is being executed...");
                });

                // Simulate progress updates
                for (int i = 10; i <= 90; i += 10) {
                    if (cancelled) {
                        return TaskResult.failure("Task cancelled by user");
                    }

                    final int progress = i;
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(progress);
                        progressBar.setString(progress + "% complete");
                        if (progressCallback != null) {
                            progressCallback.accept("Progress: " + progress + "%");
                        }
                    });

                    Thread.sleep(200); // Simulate work
                }

                // Execute the actual task
                TaskResult result = taskExecutor.get();

                SwingUtilities.invokeLater(() -> {
                    if (result.isSuccess()) {
                        onTaskCompleted(taskName, result);
                    } else {
                        onTaskFailed(taskName, result);
                    }
                });

                return result;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                SwingUtilities.invokeLater(() -> onTaskCancelled(taskName));
                return TaskResult.failure("Task interrupted");
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> onTaskError(taskName, e));
                return TaskResult.failure("Task error: " + e.getMessage());
            }
        });

        return currentTask;
    }

    /**
     * Exécute une tâche IntelliJ en arrière-plan avec progression
     */
    public void executeIntelliJTask(Project project, String title, Runnable taskRunnable) {
        new Task.Backgroundable(project, title, true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setIndeterminate(false);
                    indicator.setFraction(0.0);
                    indicator.setText("Starting " + title + "...");

                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText(title);
                        progressBar.setIndeterminate(false);
                        progressBar.setValue(0);
                        progressBar.setString("IntelliJ Task Running...");
                        cancelButton.setEnabled(true);
                    });

                    // Simulate progress during task execution
                    for (int i = 0; i <= 100; i += 20) {
                        if (indicator.isCanceled()) {
                            SwingUtilities.invokeLater(() -> onTaskCancelled(title));
                            return;
                        }

                        indicator.setFraction(i / 100.0);
                        indicator.setText(title + " - " + i + "% complete");

                        final int progress = i;
                        SwingUtilities.invokeLater(() -> {
                            progressBar.setValue(progress);
                            progressBar.setString(progress + "% complete");
                        });

                        if (i < 100) {
                            Thread.sleep(500);
                        }
                    }

                    // Execute the actual task
                    taskRunnable.run();

                    SwingUtilities.invokeLater(() -> {
                        onTaskCompleted(title, TaskResult.success("IntelliJ task completed"));
                    });

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    SwingUtilities.invokeLater(() -> onTaskCancelled(title));
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> onTaskError(title, e));
                }
            }
        }.queue();
    }

    private void updateProgress() {
        // Smooth animation for indeterminate progress
        if (progressBar.isIndeterminate()) {
            // IntelliJ will handle the indeterminate animation
        }
    }

    private void onTaskCompleted(String taskName, TaskResult result) {
        updateTimer.stop();
        progressBar.setValue(100);
        progressBar.setString("Completed");
        statusLabel.setText(taskName + " - Success");
        detailsLabel.setText(result.getMessage());
        cancelButton.setEnabled(false);

        // Auto-hide after 3 seconds
        Timer hideTimer = new Timer(3000, e -> resetProgress());
        hideTimer.setRepeats(false);
        hideTimer.start();
    }

    private void onTaskFailed(String taskName, TaskResult result) {
        updateTimer.stop();
        progressBar.setValue(0);
        progressBar.setString("Failed");
        statusLabel.setText(taskName + " - Failed");
        detailsLabel.setText(result.getErrorMessage());
        cancelButton.setEnabled(false);

        // Show error dialog
        Messages.showErrorDialog(
                result.getErrorMessage(),
                "Task Failed: " + taskName
        );
    }

    private void onTaskCancelled(String taskName) {
        updateTimer.stop();
        progressBar.setValue(0);
        progressBar.setString("Cancelled");
        statusLabel.setText(taskName + " - Cancelled");
        detailsLabel.setText("Task was cancelled by user");
        cancelButton.setEnabled(false);

        // Auto-hide after 2 seconds
        Timer hideTimer = new Timer(2000, e -> resetProgress());
        hideTimer.setRepeats(false);
        hideTimer.start();
    }

    private void onTaskError(String taskName, Exception error) {
        updateTimer.stop();
        progressBar.setValue(0);
        progressBar.setString("Error");
        statusLabel.setText(taskName + " - Error");
        detailsLabel.setText("Error: " + error.getMessage());
        cancelButton.setEnabled(false);

        // Show error dialog
        Messages.showErrorDialog(
                "An unexpected error occurred:\n" + error.getMessage(),
                "Task Error: " + taskName
        );
    }

    private void onCancelClicked(ActionEvent e) {
        cancelled = true;
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(true);
        }
        cancelButton.setEnabled(false);
        progressBar.setString("Cancelling...");
        statusLabel.setText("Cancelling task...");
    }

    private void resetProgress() {
        progressBar.setValue(0);
        progressBar.setString("Ready");
        statusLabel.setText("Waiting for task...");
        detailsLabel.setText(" ");
        cancelButton.setEnabled(false);
        cancelled = false;
    }

    /**
     * Affiche un message de statut temporaire
     * FIX: Stop indeterminate animation to prevent "thinking" icon spinning forever
     */
    public void showTemporaryStatus(String message, int durationMs) {
        String originalStatus = statusLabel.getText();
        String originalDetails = detailsLabel.getText();

        // FIX: Stop update timer to prevent continuous animation
        updateTimer.stop();

        // FIX: Stop indeterminate progress bar animation
        progressBar.setIndeterminate(false);
        progressBar.setValue(100);
        progressBar.setString("Completed");

        statusLabel.setText(message);
        detailsLabel.setText("Temporary status message");

        Timer resetTimer = new Timer(durationMs, e -> {
            statusLabel.setText(originalStatus);
            detailsLabel.setText(originalDetails);
        });
        resetTimer.setRepeats(false);
        resetTimer.start();
    }

    /**
     * Met à jour le progrès manuellement
     */
    public void updateProgress(int percentage, String message) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setIndeterminate(false);
            progressBar.setValue(percentage);
            progressBar.setString(percentage + "% - " + message);
            detailsLabel.setText(message);
        });
    }

    /**
     * Stop all progress animations and timers
     * FIX: Public method to ensure animation stops completely
     */
    public void stopProgress() {
        SwingUtilities.invokeLater(() -> {
            // Stop update timer
            if (updateTimer != null && updateTimer.isRunning()) {
                updateTimer.stop();
            }

            // Stop indeterminate animation
            progressBar.setIndeterminate(false);
            progressBar.setValue(0);
            progressBar.setString("Ready");

            // Reset labels
            statusLabel.setText("Waiting for task...");
            detailsLabel.setText(" ");
            cancelButton.setEnabled(false);
        });
    }

    /**
     * Vérifie si une tâche est en cours
     */
    public boolean isTaskRunning() {
        return currentTask != null && !currentTask.isDone() && !cancelled;
    }

    /**
     * Nettoyage des ressources
     */
    public void dispose() {
        if (updateTimer != null) {
            updateTimer.stop();
        }
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(true);
        }
    }
}