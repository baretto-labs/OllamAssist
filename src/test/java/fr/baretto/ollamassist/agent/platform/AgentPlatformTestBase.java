package fr.baretto.ollamassist.agent.platform;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import fr.baretto.ollamassist.events.FileApprovalNotifier;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Base class for all agent platform tests.
 *
 * <p>Provides:
 * <ul>
 *   <li>A live IntelliJ {@link com.intellij.openapi.project.Project} with real VirtualFile system</li>
 *   <li>Auto-approval of file mutations (configurable via {@link #setAutoApprove(boolean)})</li>
 *   <li>Helper to skip tests that require a live Ollama instance</li>
 * </ul>
 *
 * <p>Subclasses use JUnit 3/4 naming conventions (methods starting with {@code test}).
 */
public abstract class AgentPlatformTestBase extends BasePlatformTestCase {

    private boolean autoApprove = true;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Single subscriber drives all approval decisions for this test.
        // Toggle autoApprove in a test method before triggering the tool call.
        getProject().getMessageBus()
                .connect(getTestRootDisposable())
                .subscribe(FileApprovalNotifier.TOPIC,
                        req -> req.getResponseFuture().complete(
                                autoApprove ? FileApprovalNotifier.ApprovalDecision.allow()
                                            : FileApprovalNotifier.ApprovalDecision.deny(null)));
    }

    /** Call in a test method to simulate user rejecting file changes. */
    protected void setAutoApprove(boolean approve) {
        this.autoApprove = approve;
    }

    /**
     * Skips the current test if Ollama is not reachable.
     * Call at the start of any test annotated with {@link RequiresOllama}.
     */
    protected void assumeOllamaRunning() {
        String url = System.getProperty("platformTest.ollamaUrl", "http://localhost:11434");
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(1000);
            conn.setRequestMethod("HEAD");
            conn.connect();
            int code = conn.getResponseCode();
            if (code < 200 || code >= 500) {
                throw new IOException("Unexpected status: " + code);
            }
        } catch (IOException e) {
            System.out.println("[platformTest] Ollama not reachable at " + url + " — skipping test.");
            // JUnit 3/4 style: throwing AssumptionViolatedException skips the test
            throw new org.junit.AssumptionViolatedException("Ollama not reachable: " + e.getMessage());
        }
    }

    /** Convenience: read the current content of a project-relative file from the VFS. */
    protected String readProjectFile(String relativePath) throws Exception {
        var file = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .refreshAndFindFileByPath(getProject().getBasePath() + "/" + relativePath);
        assertNotNull("File not found in VFS: " + relativePath, file);
        file.refresh(false, false);
        return new String(file.contentsToByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }
}
