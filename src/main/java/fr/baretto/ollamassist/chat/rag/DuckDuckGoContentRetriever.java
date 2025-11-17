package fr.baretto.ollamassist.chat.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import fr.baretto.ollamassist.completion.LightModelAssistant;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DuckDuckGoContentRetriever {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2000);
    private static final String HTML_SEARCH_URL = "https://html.duckduckgo.com/html/";
    private static final String API_SEARCH_URL = "https://api.duckduckgo.com/";
    private static final String PROTOCOL_HTTP = "http://";
    private static final String PROTOCOL_HTTPS = "https://";
    private static final String FIRST_URL = "FirstURL";
    private static final String PROTOCOL_PREFIX = "//";
    private static final String HTTPS_PREFIX = "https:";
    private static final String QUERY_FORMAT = "q=%s&b=&kl=us-en";
    private static final String API_QUERY_FORMAT = "?q=%s&format=json&no_html=1&skip_disambig=1";
    private static final String SEPARATOR = " - ";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final int maxResults;

    public DuckDuckGoContentRetriever(int maxResults) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(DEFAULT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
        this.maxResults = maxResults;
    }

    @SneakyThrows
    public List<Content> retrieve(Query query) {
        String webQuery = LightModelAssistant.get().createWebSearchQuery(query.text());
        return execute(webQuery);
    }

    private @NotNull List<Content> execute(String webQuery) throws IOException, InterruptedException {
        List<SearchResult> results;
        try {
            results = htmlSearch(webQuery);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            results = apiSearch(webQuery);
        }
        return results.stream()
                .map(r -> {
                    StringBuilder sb = new StringBuilder();
                    if (r.title != null && !r.title.isBlank()) {
                        sb.append(r.title.trim());
                    }
                    if (r.snippet != null && !r.snippet.isBlank()) {
                        if (!sb.isEmpty()) sb.append(SEPARATOR);
                        sb.append(r.snippet.trim());
                    }
                    return Content.from(sb.toString());
                })
                .toList();
    }

    private List<SearchResult> htmlSearch(String query) throws IOException, InterruptedException {
        String formData = String.format(QUERY_FORMAT, URLEncoder.encode(query, StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(HTML_SEARCH_URL))
                .timeout(DEFAULT_TIMEOUT)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        org.jsoup.nodes.Document doc = Jsoup.parse(response.body());
        List<SearchResult> results = parseHtmlResults(doc);

        if (results.isEmpty()) {
            throw new IOException("No results found");
        }

        return results;
    }

    private List<SearchResult> apiSearch(String query) throws IOException, InterruptedException {
        String url = API_SEARCH_URL + String.format(API_QUERY_FORMAT, URLEncoder.encode(query, StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .header("User-Agent", "LangChain4j-DuckDuckGo/1.0")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return mapResponse(response.body());
    }

    List<SearchResult> parseHtmlResults(org.jsoup.nodes.Document doc) {

        String[] selectors = {"div.web-result", "div.result", ".links_main"};
        Elements resultElements = new Elements();

        for (String selector : selectors) {
            resultElements = doc.select(selector);
            if (!resultElements.isEmpty()) break;
        }

        return new ArrayList<>(resultElements.stream()
                .map(element -> {
                    Element titleElement = element.selectFirst("h2 a, .result__title a, h3 a");
                    if (titleElement == null) return null;

                    String title = titleElement.text().trim();
                    String url = titleElement.attr("href");
                    if (title.isEmpty() || !isValidUrl(url)) return null;

                    Element snippetElement = element.selectFirst(".result__snippet, .snippet");
                    String snippet = snippetElement != null ? snippetElement.text().trim() : "";

                    return new SearchResult(title, cleanUrl(url), snippet);
                })
                .filter(Objects::nonNull)
                .limit(maxResults)
                .toList());
    }

    List<SearchResult> mapResponse(String json) {
        try {
            JsonNode rootNode = objectMapper.readTree(json);
            List<SearchResult> results = new ArrayList<>();

            addAbstract(rootNode, results);
            addAnswer(rootNode, results);
            addResultsNode(rootNode.get("Results"), results);
            addRelatedTopics(rootNode.get("RelatedTopics"), results);

            return results.stream().limit(maxResults).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private void addAbstract(JsonNode rootNode, List<SearchResult> results) {
        String abstractText = cleanText(getJsonText(rootNode, "Abstract"));
        String abstractUrl = getJsonText(rootNode, "AbstractURL");
        if (!abstractText.isEmpty() && !abstractUrl.isEmpty()) {
            results.add(new SearchResult("Abstract", abstractUrl, abstractText));
        }
    }

    private void addAnswer(JsonNode rootNode, List<SearchResult> results) {
        String answer = cleanText(getJsonText(rootNode, "Answer"));
        if (!answer.isEmpty()) {
            results.add(new SearchResult("Answer", "https://duckduckgo.com", answer));
        }
    }

    private void addResultsNode(JsonNode resultsNode, List<SearchResult> results) {
        if (resultsNode == null || !resultsNode.isArray()) return;
        resultsNode.forEach(node -> {
            String title = cleanText(getJsonText(node, "Text"));
            String url = getJsonText(node, FIRST_URL);
            if (!title.isEmpty() && !url.isEmpty()) {
                results.add(new SearchResult(title, url, ""));
            }
        });
    }

    private void addRelatedTopics(JsonNode topicsNode, List<SearchResult> results) {
        if (topicsNode == null || !topicsNode.isArray()) return;

        topicsNode.forEach(node -> {
            if (node.has("Text")) {
                addResultsNode(node, results);
            } else if (node.has("Topics")) {
                addResultsNode(node.get("Topics"), results);
            }
        });
    }


    static String cleanText(String html) {
        if (html == null || html.isBlank()) return "";
        return Jsoup.parse(html).text().trim();
    }

    private static String getJsonText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asText("").trim() : "";
    }

    static boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        if (url.contains("duckduckgo.com") && !url.contains("/l/")) return false;
        return url.startsWith(PROTOCOL_HTTPS) || url.startsWith(PROTOCOL_HTTP) || url.startsWith(PROTOCOL_PREFIX);
    }

    static String cleanUrl(String url) {
        if (url.startsWith(PROTOCOL_PREFIX)) {
            url = HTTPS_PREFIX + url;
        }
        if (url.startsWith(PROTOCOL_HTTP)) {
            url = url.replace(PROTOCOL_HTTP, PROTOCOL_HTTPS);
        }
        return url;
    }

    private record SearchResult(String title, String url, String snippet) {
    }
}