package fr.baretto.ollamassist.core.mcp.server.builtin;

import fr.baretto.ollamassist.core.mcp.protocol.MCPResponse;
import fr.baretto.ollamassist.core.mcp.server.MCPServerConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serveur MCP intégré pour la recherche web
 */
@Slf4j
public class WebSearchMCPServer implements BuiltinMCPServer {

    private static final String SERVER_ID = "websearch";
    private static final String SERVER_NAME = "Web Search";
    private static final String SERVER_VERSION = "1.0.0";

    private final HttpClient httpClient;

    public WebSearchMCPServer() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String getId() {
        return SERVER_ID;
    }

    @Override
    public String getName() {
        return SERVER_NAME;
    }

    @Override
    public String getVersion() {
        return SERVER_VERSION;
    }

    @Override
    public MCPServerConfig getConfig() {
        return MCPServerConfig.builder()
                .id(SERVER_ID)
                .name(SERVER_NAME)
                .type(MCPServerConfig.MCPServerType.BUILTIN)
                .capabilities(List.of(
                        "web/search",
                        "web/get_page",
                        "web/extract_text"
                ))
                .enabled(true)
                .build();
    }

    @Override
    public MCPResponse executeCapability(String capability, Map<String, Object> params) {
        log.debug("Executing WebSearch capability: {} with params: {}", capability, params);

        try {
            return switch (capability) {
                case "web/search" -> webSearch(params);
                case "web/get_page" -> getPage(params);
                case "web/extract_text" -> extractText(params);
                default -> MCPResponse.error("Capability not supported: " + capability);
            };
        } catch (Exception e) {
            log.error("Error executing web search capability: {}", capability, e);
            return MCPResponse.error("Execution error: " + e.getMessage());
        }
    }

    private MCPResponse webSearch(Map<String, Object> params) {
        String query = (String) params.get("query");
        Integer maxResults = (Integer) params.get("max_results");

        if (query == null || query.trim().isEmpty()) {
            return MCPResponse.error("Parameter 'query' is required");
        }

        if (maxResults == null) {
            maxResults = 10;
        }

        try {
            // Utilisation de DuckDuckGo pour la recherche (pas d'API key requise)
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = "https://duckduckgo.com/html/?q=" + encodedQuery;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .header("User-Agent", "Mozilla/5.0 (OllamAssist MCP Server)")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return MCPResponse.error("Search request failed with status: " + response.statusCode());
            }

            List<Map<String, Object>> results = parseSearchResults(response.body(), maxResults);

            return MCPResponse.success(Map.of(
                    "query", query,
                    "results", results,
                    "count", results.size()
            ));

        } catch (IOException | InterruptedException e) {
            return MCPResponse.error("Error performing search: " + e.getMessage());
        }
    }

    private MCPResponse getPage(Map<String, Object> params) {
        String url = (String) params.get("url");
        if (url == null) {
            return MCPResponse.error("Parameter 'url' is required");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (OllamAssist MCP Server)")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return MCPResponse.error("Page request failed with status: " + response.statusCode());
            }

            return MCPResponse.success(Map.of(
                    "url", url,
                    "content", response.body(),
                    "status", response.statusCode(),
                    "headers", response.headers().map()
            ));

        } catch (IOException | InterruptedException e) {
            return MCPResponse.error("Error fetching page: " + e.getMessage());
        }
    }

    private MCPResponse extractText(Map<String, Object> params) {
        String html = (String) params.get("html");
        if (html == null) {
            return MCPResponse.error("Parameter 'html' is required");
        }

        try {
            // Extraction de texte simple (enlever les balises HTML)
            String text = html
                    .replaceAll("(?i)<script[^>]*>.*?</script>", " ")
                    .replaceAll("(?i)<style[^>]*>.*?</style>", " ")
                    .replaceAll("(?i)<[^>]+>", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

            return MCPResponse.success(Map.of(
                    "text", text,
                    "length", text.length()
            ));

        } catch (Exception e) {
            return MCPResponse.error("Error extracting text: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> parseSearchResults(String html, int maxResults) {
        // Pattern simple pour extraire les résultats de DuckDuckGo
        // Note: Ceci est une implémentation basique, un parser HTML réel serait préférable
        Pattern resultPattern = Pattern.compile(
                "<a[^>]+class=\"result__a\"[^>]+href=\"([^\"]+)\"[^>]*>([^<]+)</a>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Pattern snippetPattern = Pattern.compile(
                "<a[^>]+class=\"result__snippet\"[^>]*>([^<]+)</a>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        List<Map<String, Object>> results = new java.util.ArrayList<>();
        Matcher matcher = resultPattern.matcher(html);
        int count = 0;

        while (matcher.find() && count < maxResults) {
            String url = matcher.group(1);
            String title = matcher.group(2).trim();

            // Essayer de trouver le snippet correspondant
            String snippet = "";
            Matcher snippetMatcher = snippetPattern.matcher(html);
            if (snippetMatcher.find()) {
                snippet = snippetMatcher.group(1).trim();
            }

            results.add(Map.of(
                    "title", title,
                    "url", url,
                    "snippet", snippet
            ));
            count++;
        }

        return results;
    }

    @Override
    public void stop() {
        // Le HttpClient sera fermé automatiquement
    }
}