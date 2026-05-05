package fr.baretto.ollamassist.agent.tools.files;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.Collection;
import java.util.Optional;

/**
 * Language-agnostic syntax validator backed by IntelliJ's PSI infrastructure.
 *
 * <p>Works for any language with a registered parser in the running IDE: Java, Kotlin,
 * XML, JSON, YAML, TypeScript, etc. Files with unknown or binary types are silently
 * accepted (fail-open — this is a quality check, not a security gate).
 *
 * <p>Validation runs on an in-memory PSI tree without touching disk, inside a
 * {@link ReadAction} so it is safe to call from background threads.
 */
@FunctionalInterface
public interface PsiSyntaxValidator {

    Logger LOG = Logger.getInstance(PsiSyntaxValidator.class);

    /**
     * Validates the proposed content.
     *
     * @param fileName the target filename, used to detect the language (e.g. "Foo.java")
     * @param content  the full proposed file content
     * @return empty when valid (or language unknown), or a human-readable error description
     */
    Optional<String> validate(String fileName, String content);

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /** Production validator — delegates to the IntelliJ PSI engine for the given project. */
    static PsiSyntaxValidator forProject(Project project) {
        return (fileName, content) -> {
            try {
                return ReadAction.compute(() -> {
                    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
                    if (!(fileType instanceof LanguageFileType lft)) {
                        // Binary or plain-text type with no structured parser — skip.
                        return Optional.empty();
                    }
                    Language language = lft.getLanguage();
                    PsiFile tempPsi = PsiFileFactory.getInstance(project)
                            .createFileFromText(fileName, language, content);
                    Collection<PsiErrorElement> errors =
                            PsiTreeUtil.findChildrenOfType(tempPsi, PsiErrorElement.class);
                    if (errors.isEmpty()) return Optional.empty();
                    String description = errors.iterator().next().getErrorDescription();
                    return Optional.of(description);
                });
            } catch (Exception e) {
                // PSI infrastructure unavailable (e.g. headless test without IDE runtime).
                // Fail-open: allow the write rather than blocking the agent on a tooling error.
                LOG.debug("PSI syntax validation unavailable for '" + fileName + "': " + e.getMessage());
                return Optional.empty();
            }
        };
    }

    /** Always reports the content as valid. For use in tests only. */
    static PsiSyntaxValidator alwaysValid() {
        return (fileName, content) -> Optional.empty();
    }

    /** Always reports a fixed error. For use in tests only. */
    static PsiSyntaxValidator alwaysError(String errorMessage) {
        return (fileName, content) -> Optional.of(errorMessage);
    }
}
