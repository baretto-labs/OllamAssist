package fr.baretto.ollamassist.core.agent.security;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Input validation utility for agent operations
 * Prevents injection attacks, path traversal, and malicious commands
 */
@Slf4j
public class InputValidator {

    // Maximum length for Git commit messages (following Git best practices)
    private static final int MAX_COMMIT_MESSAGE_LENGTH = 500;

    // Dangerous characters that could lead to command injection
    private static final Pattern DANGEROUS_CHARS = Pattern.compile("[;|&$`\\n\\r]");

    // Git flag detection (prevents --no-verify, --amend, etc.)
    private static final Pattern GIT_FLAGS = Pattern.compile("^\\s*--?[a-zA-Z]");

    // Allowed file extensions for file operations (whitelist approach)
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".java", ".kt", ".kts",           // Java/Kotlin
            ".xml", ".yaml", ".yml", ".json", // Configuration
            ".md", ".txt", ".properties",     // Documentation/Config
            ".gradle", ".gradle.kts",         // Build
            ".gitignore", ".editorconfig"     // VCS/Editor
    );

    // Sensitive files that should never be modified by agent (blacklist)
    private static final Set<String> SENSITIVE_FILES = Set.of(
            ".env", ".env.local", ".env.production",
            "credentials.json", "secrets.json",
            "id_rsa", "id_dsa", "id_ecdsa", "id_ed25519",
            ".pem", ".key", ".p12", ".jks", ".keystore"
    );

    // Allowed build operations (whitelist)
    private static final Set<String> ALLOWED_BUILD_OPERATIONS = Set.of(
            "build", "compile",
            "test",
            "clean",
            "package", "jar",
            "diagnostics"
    );

    /**
     * Sanitizes a Git commit message to prevent command injection
     *
     * @param message the commit message to sanitize
     * @return sanitized message
     * @throws ValidationException if message is invalid or dangerous
     */
    public static String sanitizeGitMessage(String message) throws ValidationException {
        if (message == null || message.trim().isEmpty()) {
            throw new ValidationException("Commit message cannot be null or empty");
        }

        String trimmed = message.trim();

        // Check length
        if (trimmed.length() > MAX_COMMIT_MESSAGE_LENGTH) {
            throw new ValidationException(
                    "Commit message too long (" + trimmed.length() + " characters). " +
                            "Maximum allowed: " + MAX_COMMIT_MESSAGE_LENGTH
            );
        }

        // Detect Git flags at the beginning (e.g., "--no-verify", "--amend")
        if (GIT_FLAGS.matcher(trimmed).find()) {
            throw new ValidationException(
                    "Commit message cannot start with Git flags (--flag). " +
                            "This could be used to bypass Git hooks or modify commit behavior."
            );
        }

        // Check for dangerous characters that could lead to command injection
        if (DANGEROUS_CHARS.matcher(trimmed).find()) {
            log.warn("Dangerous characters detected in commit message: {}", trimmed);
            throw new ValidationException(
                    "Commit message contains dangerous characters (; | & $ ` newline). " +
                            "These could be used for command injection attacks."
            );
        }

        // Escape double quotes to prevent breaking out of Git command
        String sanitized = trimmed.replace("\"", "\\\"");

        log.debug("Git commit message sanitized successfully. Length: {}", sanitized.length());
        return sanitized;
    }

    /**
     * Validates a file path to prevent path traversal attacks
     *
     * @param filePath        the file path to validate (relative or absolute)
     * @param projectRootPath the project root path for boundary checking
     * @return normalized safe path
     * @throws ValidationException if path is invalid or dangerous
     */
    public static String validateFilePath(String filePath, String projectRootPath) throws ValidationException {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new ValidationException("File path cannot be null or empty");
        }

        if (projectRootPath == null || projectRootPath.trim().isEmpty()) {
            throw new ValidationException("Project root path cannot be null or empty");
        }

        try {
            // Normalize paths to detect traversal attempts
            Path projectRoot = Paths.get(projectRootPath).normalize().toAbsolutePath();
            Path requestedPath = Paths.get(filePath).normalize();

            // Convert relative path to absolute for boundary checking
            Path absolutePath;
            if (requestedPath.isAbsolute()) {
                absolutePath = requestedPath;
            } else {
                absolutePath = projectRoot.resolve(requestedPath).normalize();
            }

            // CRITICAL: Check path traversal - ensure file is within project
            if (!absolutePath.startsWith(projectRoot)) {
                log.warn("Path traversal attempt detected: {} escapes project root {}", filePath, projectRootPath);
                throw new ValidationException(
                        "Path traversal detected. File path must be within project: " + filePath
                );
            }

            // Check file extension against whitelist
            String fileName = absolutePath.getFileName().toString().toLowerCase();
            boolean hasAllowedExtension = ALLOWED_EXTENSIONS.stream()
                    .anyMatch(fileName::endsWith);

            if (!hasAllowedExtension) {
                // Special case: files without extension (like Dockerfile, Makefile)
                if (fileName.contains(".")) {
                    throw new ValidationException(
                            "File extension not allowed: " + fileName + ". " +
                                    "Allowed extensions: " + ALLOWED_EXTENSIONS
                    );
                }
                // Allow files without extension (Dockerfile, README, etc.)
                log.debug("Allowing file without extension: {}", fileName);
            }

            // Check for sensitive files (blacklist)
            for (String sensitive : SENSITIVE_FILES) {
                if (fileName.endsWith(sensitive) || fileName.equals(sensitive)) {
                    log.warn("Attempt to modify sensitive file: {}", fileName);
                    throw new ValidationException(
                            "Cannot modify sensitive file: " + fileName + ". " +
                                    "This file contains credentials or sensitive data."
                    );
                }
            }

            // Return the absolute path as string
            String validatedPath = absolutePath.toString();
            log.debug("File path validated successfully: {}", validatedPath);
            return validatedPath;

        } catch (ValidationException e) {
            throw e; // Re-throw validation exceptions
        } catch (Exception e) {
            log.error("Error validating file path: {}", filePath, e);
            throw new ValidationException("Invalid file path: " + e.getMessage(), e);
        }
    }

    /**
     * Validates a build operation against whitelist
     *
     * @param operation the build operation to validate (e.g., "build", "test", "clean")
     * @throws ValidationException if operation is not allowed
     */
    public static void validateBuildOperation(String operation) throws ValidationException {
        if (operation == null || operation.trim().isEmpty()) {
            throw new ValidationException("Build operation cannot be null or empty");
        }

        String normalized = operation.trim().toLowerCase();

        // Detect attempts to inject additional arguments/flags
        if (normalized.contains(" ") || normalized.contains("\t")) {
            log.warn("Build operation contains whitespace (possible flag injection): {}", operation);
            throw new ValidationException(
                    "Build operation cannot contain spaces or additional arguments: " + operation
            );
        }

        // Check against whitelist
        if (!ALLOWED_BUILD_OPERATIONS.contains(normalized)) {
            throw new ValidationException(
                    "Build operation not allowed: " + operation + ". " +
                            "Allowed operations: " + ALLOWED_BUILD_OPERATIONS
            );
        }

        log.debug("Build operation validated successfully: {}", normalized);
    }

    /**
     * Custom exception for input validation failures
     */
    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }

        public ValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
