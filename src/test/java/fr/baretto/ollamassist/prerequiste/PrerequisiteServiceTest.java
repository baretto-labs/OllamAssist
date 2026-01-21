package fr.baretto.ollamassist.prerequiste;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PrerequisiteService URL normalization behavior.
 * Tests verify that UrlHelper is properly integrated for URL validation.
 */
class PrerequisiteServiceTest {

    @Test
    void testUrlHelper_normalizeUrl_localhost() {
        String result = UrlHelper.normalizeUrl("localhost:11434");
        assertEquals("http://localhost:11434", result);
    }

    @Test
    void testUrlHelper_normalizeUrl_withHttp() {
        String result = UrlHelper.normalizeUrl("http://localhost:11434");
        assertEquals("http://localhost:11434", result);
    }

    @Test
    void testUrlHelper_normalizeUrl_withTrailingSlash() {
        String result = UrlHelper.normalizeUrl("http://localhost:11434/");
        assertEquals("http://localhost:11434", result);
    }

    @Test
    void testUrlHelper_normalizeUrl_mixedCase() {
        String result = UrlHelper.normalizeUrl("localhost:11434/");
        assertEquals("http://localhost:11434", result);
    }

    @Test
    void testUrlHelper_buildApiUrl_normalizes() {
        String result = UrlHelper.buildApiUrl("localhost:11434", "/api/version");
        assertEquals("http://localhost:11434/api/version", result);
    }

    @Test
    void testUrlHelper_buildApiUrl_removesDoubleSlashes() {
        String result = UrlHelper.buildApiUrl("http://localhost:11434/", "/api/tags");
        assertEquals("http://localhost:11434/api/tags", result);
    }

    @Test
    void testUrlHelper_buildApiUrl_addsSlashToPath() {
        String result = UrlHelper.buildApiUrl("http://localhost:11434", "api/version");
        assertEquals("http://localhost:11434/api/version", result);
    }

    @Test
    void testUrlHelper_nullUrl_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> UrlHelper.normalizeUrl(null));
    }

    @Test
    void testUrlHelper_emptyUrl_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> UrlHelper.normalizeUrl(""));
    }

    @Test
    void testUrlHelper_multipleUrlFormats() {
        String[] testUrls = {
                "localhost:11434",
                "http://localhost:11434",
                "http://localhost:11434/",
                "localhost:11434/",
                "192.168.1.1:11434",
                "example.com:8080"
        };

        for (String url : testUrls) {
            assertDoesNotThrow(() -> UrlHelper.normalizeUrl(url),
                    "Failed to normalize: " + url);
            String result = UrlHelper.normalizeUrl(url);
            assertTrue(result.startsWith("http"), "URL should start with http: " + result);
            assertFalse(result.endsWith("/"), "URL should not end with /: " + result);
        }
    }

}
