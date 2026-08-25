package fr.baretto.ollamassist.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The revision of the code a benchmark run measured.
 *
 * <p>A score is only comparable to another score if both name the commit they came from.
 * Without it a results file records that quality changed, but not what changed with it,
 * and the before/after of a correction cannot be reconstructed afterwards.
 *
 * <p>A working tree with uncommitted changes is reported as {@code <sha>-dirty}: the run
 * measured something that exists on no commit, so it is comparable to nothing.
 */
final class MeasuredRevision {

    private static final String UNKNOWN = "unknown";
    private static final int TIMEOUT_SECONDS = 10;

    private MeasuredRevision() {
    }

    /**
     * Reads the current revision of the git working tree at {@code directory}.
     *
     * @param directory any directory inside the working tree
     * @return the short commit hash, suffixed {@code -dirty} when the tree has uncommitted
     *         changes, or {@code "unknown"} when the revision cannot be established
     */
    static String of(Path directory) {
        String head = run(directory, "git", "rev-parse", "--short", "HEAD");
        if (head.isBlank()) {
            return UNKNOWN;
        }
        String status = run(directory, "git", "status", "--porcelain");
        return status.isBlank() ? head : head + "-dirty";
    }

    private static String run(Path directory, String... command) {
        try {
            Process process = new ProcessBuilder(List.of(command))
                    .directory(directory.toFile())
                    .redirectErrorStream(false)
                    .start();

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "";
            }
            return process.exitValue() == 0 ? output : "";
        } catch (IOException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }
}
