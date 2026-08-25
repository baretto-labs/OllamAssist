package fr.baretto.ollamassist.platform;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Path;

/**
 * Pins what the chunking benchmark's {@code psi-java} arm actually measures.
 *
 * <p>{@code ChunkingBenchmarkTest} resolves its project with
 * {@code ProjectManager.getDefaultProject()} and hands it to {@code CodeAwareDocumentSplitter},
 * whose PSI path falls back to line-based splitting whenever {@code PsiManager.findFile}
 * does not return a {@link PsiJavaFile} — silently, in a {@code catch (Throwable)}. If the
 * default project cannot parse Java, both benchmark arms are the same strategy under two
 * names, and every psi-java-versus-line-based conclusion drawn from those files is void.
 */
public class DefaultProjectPsiTest extends BasePlatformTestCase {

    public void testDefaultProjectParsesARealJavaFileAsJava() {
        Path onDisk = Path.of("src/main/java/fr/baretto/ollamassist/chat/rag/CodeAwareDocumentSplitter.java")
                .toAbsolutePath();
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(onDisk.toString());
        assertNotNull("benchmark source file not found on disk: " + onDisk, virtualFile);

        Project defaultProject = ProjectManager.getInstance().getDefaultProject();
        PsiFile psiFile = ReadAction.compute(() -> PsiManager.getInstance(defaultProject).findFile(virtualFile));

        System.out.println("[probe] default project: " + defaultProject.getName());
        System.out.println("[probe] PsiFile class: " + (psiFile == null ? "null" : psiFile.getClass().getName()));
        System.out.println("[probe] is PsiJavaFile: " + (psiFile instanceof PsiJavaFile));

        assertTrue("the default project did not parse the file as Java — the benchmark's "
                + "psi-java arm silently falls back to line-based", psiFile instanceof PsiJavaFile);
    }
}
