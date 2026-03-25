package fr.baretto.ollamassist.prerequiste;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlHelperTest {

    @Test
    void testNormalizeUrl_withHttpScheme() {
        String result = UrlHelper.normalizeUrl("http://localhost:11434");
        assertEquals("http://localhost:11434", result);
    }

    @Test
    void testNormalizeUrl_withHttpsScheme() {
        String result = UrlHelper.normalizeUrl("https://localhost:11434");
        assertEquals("https://localhost:11434", result);
    }

    @Test
    void testNormalizeUrl_withoutScheme() {
        String result = UrlHelper.normalizeUrl("localhost:11434");
        assertEquals("http://localhost:11434", result);
    }

    @Test
    void testNormalizeUrl_withoutSchemeAndPort() {
        String result = UrlHelper.normalizeUrl("localhost");
        assertEquals("http://localhost", result);
    }

    @Test
    void testNormalizeUrl_withTrailingSlash() {
        String result = UrlHelper.normalizeUrl("http://localhost:11434/");
        assertEquals("http://localhost:11434", result);
    }

    @Test
    void testNormalizeUrl_withMultipleTrailingSlashes() {
        String result = UrlHelper.normalizeUrl("http://localhost:11434///");
        assertEquals("http://localhost:11434", result);
    }

    @Test
    void testNormalizeUrl_withoutSchemeAndTrailingSlash() {
        String result = UrlHelper.normalizeUrl("localhost:11434/");
        assertEquals("http://localhost:11434", result);
    }

    @Test
    void testNormalizeUrl_withWhitespace() {
        String result = UrlHelper.normalizeUrl("  http://localhost:11434  ");
        assertEquals("http://localhost:11434", result);
    }

    @Test
    void testNormalizeUrl_complexURL() {
        String result = UrlHelper.normalizeUrl("192.168.1.1:8080");
        assertEquals("http://192.168.1.1:8080", result);
    }

    @Test
    void testNormalizeUrl_null_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> UrlHelper.normalizeUrl(null));
    }

    @Test
    void testNormalizeUrl_emptyString_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> UrlHelper.normalizeUrl(""));
    }

    @Test
    void testNormalizeUrl_whitespaceOnly_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> UrlHelper.normalizeUrl("   "));
    }

    @Test
    void testValidateUrl_validUrl() {
        assertDoesNotThrow(() -> UrlHelper.validateUrl("http://localhost:11434"));
    }

    @Test
    void testValidateUrl_invalidUrl_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> UrlHelper.validateUrl("ht!tp://invalid:url"));
    }

    @Test
    void testBuildApiUrl_withValidInputs() {
        String result = UrlHelper.buildApiUrl("http://localhost:11434", "/api/tags");
        assertEquals("http://localhost:11434/api/tags", result);
    }

    @Test
    void testBuildApiUrl_withTrailingSlashInBase() {
        String result = UrlHelper.buildApiUrl("http://localhost:11434/", "/api/tags");
        assertEquals("http://localhost:11434/api/tags", result);
    }

    @Test
    void testBuildApiUrl_withoutSchemeInBase() {
        String result = UrlHelper.buildApiUrl("localhost:11434", "/api/tags");
        assertEquals("http://localhost:11434/api/tags", result);
    }

    @Test
    void testBuildApiUrl_withoutLeadingSlashInPath() {
        String result = UrlHelper.buildApiUrl("http://localhost:11434", "api/tags");
        assertEquals("http://localhost:11434/api/tags", result);
    }

    @Test
    void testBuildApiUrl_baseUrlNull_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> UrlHelper.buildApiUrl(null, "/api/tags"));
    }

    @Test
    void testBuildApiUrl_baseUrlEmpty_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> UrlHelper.buildApiUrl("", "/api/tags"));
    }

    @Test
    void testBuildApiUrl_pathNull_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> UrlHelper.buildApiUrl("http://localhost:11434", null));
    }

    @Test
    void testBuildApiUrl_pathEmpty_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> UrlHelper.buildApiUrl("http://localhost:11434", ""));
    }

    @Test
    void testBuildApiUrl_allCombinations() {
        String result = UrlHelper.buildApiUrl("localhost:11434/", "api/version");
        assertEquals("http://localhost:11434/api/version", result);
    }

    @Test
    void testBuildApiUrl_customPort() {
        String result = UrlHelper.buildApiUrl("http://192.168.1.100:9999", "/api/tags");
        assertEquals("http://192.168.1.100:9999/api/tags", result);
    }

    @Test
    void testNormalizeUrl_preservesHttpsPort() {
        String result = UrlHelper.normalizeUrl("https://example.com:8443");
        assertEquals("https://example.com:8443", result);
    }

    @Test
    void testNormalizeUrl_variousValidInputs() {
        String[] inputs = {
                "localhost:11434",
                "localhost:8080",
                "192.168.1.1:11434",
                "example.com:11434",
                "http://localhost:11434",
                "https://localhost:11434"
        };

        for (String input : inputs) {
            assertDoesNotThrow(() -> UrlHelper.normalizeUrl(input));
            String result = UrlHelper.normalizeUrl(input);
            assertTrue(result.startsWith("http"), "URL should start with http: " + result);
            assertFalse(result.endsWith("/"), "URL should not end with /: " + result);
        }
    }

}
