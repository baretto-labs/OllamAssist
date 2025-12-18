import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Minimal MCP (Model Context Protocol) Test Server
 *
 * A simple STDIO-based MCP server for testing tool approval systems.
 * Uses only JDK classes - no external dependencies.
 *
 * Implements MCP JSON-RPC protocol with two test tools:
 * - echo: Safe tool that returns input arguments
 * - write_file: Tool with side-effects for testing approval/denial
 */
public class TestMcpServer {

    private static final String VERSION = "1.0.0";
    private static final String SERVER_NAME = "test-mcp-server";
    private static final Path TEMP_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "mcp-test-server");

    private final BufferedReader reader;
    private final PrintWriter writer;
    private boolean initialized = false;

    public TestMcpServer(InputStream in, OutputStream out) {
        this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true);
    }

    public void start() throws IOException {
        // Ensure temp directory exists
        Files.createDirectories(TEMP_DIR);

        String line;
        while ((line = reader.readLine()) != null) {
            try {
                handleRequest(line);
            } catch (Exception e) {
                sendError(null, -32603, "Internal error: " + e.getMessage());
            }
        }
    }

    private void handleRequest(String jsonRequest) {
        try {
            Map<String, Object> request = parseJson(jsonRequest);
            Object id = request.get("id");
            String method = (String) request.get("method");
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", new HashMap<>());

            switch (method) {
                case "initialize":
                    handleInitialize(id, params);
                    break;
                case "tools/list":
                    handleToolsList(id);
                    break;
                case "tools/call":
                    handleToolsCall(id, params);
                    break;
                case "ping":
                    sendResult(id, Collections.emptyMap());
                    break;
                default:
                    sendError(id, -32601, "Method not found: " + method);
            }
        } catch (Exception e) {
            sendError(null, -32700, "Parse error: " + e.getMessage());
        }
    }

    private void handleInitialize(Object id, Map<String, Object> params) {
        initialized = true;

        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", VERSION);

        Map<String, Object> capabilities = new HashMap<>();
        Map<String, Object> tools = new HashMap<>();
        tools.put("listChanged", false);
        capabilities.put("tools", tools);

        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", "2024-11-05");
        result.put("serverInfo", serverInfo);
        result.put("capabilities", capabilities);

        sendResult(id, result);
    }

    private void handleToolsList(Object id) {
        if (!initialized) {
            sendError(id, -32002, "Server not initialized");
            return;
        }

        List<Map<String, Object>> tools = new ArrayList<>();

        // Echo tool - safe, no side effects
        Map<String, Object> echoTool = new HashMap<>();
        echoTool.put("name", "echo");
        echoTool.put("description", "Returns the input message as-is. Safe tool for testing approval flow.");

        Map<String, Object> echoInputSchema = new HashMap<>();
        echoInputSchema.put("type", "object");

        Map<String, Object> echoProperties = new HashMap<>();
        Map<String, Object> messageProperty = new HashMap<>();
        messageProperty.put("type", "string");
        messageProperty.put("description", "Message to echo back");
        echoProperties.put("message", messageProperty);

        echoInputSchema.put("properties", echoProperties);
        echoInputSchema.put("required", Arrays.asList("message"));

        echoTool.put("inputSchema", echoInputSchema);
        tools.add(echoTool);

        // Write file tool - has side effects
        Map<String, Object> writeFileTool = new HashMap<>();
        writeFileTool.put("name", "write_file");
        writeFileTool.put("description", "Writes content to a file in the temp directory. Has side-effects for testing approval/denial.");

        Map<String, Object> writeInputSchema = new HashMap<>();
        writeInputSchema.put("type", "object");

        Map<String, Object> writeProperties = new HashMap<>();

        Map<String, Object> filenameProperty = new HashMap<>();
        filenameProperty.put("type", "string");
        filenameProperty.put("description", "Name of the file to write");
        writeProperties.put("filename", filenameProperty);

        Map<String, Object> contentProperty = new HashMap<>();
        contentProperty.put("type", "string");
        contentProperty.put("description", "Content to write to the file");
        writeProperties.put("content", contentProperty);

        writeInputSchema.put("properties", writeProperties);
        writeInputSchema.put("required", Arrays.asList("filename", "content"));

        writeFileTool.put("inputSchema", writeInputSchema);
        tools.add(writeFileTool);

        Map<String, Object> result = new HashMap<>();
        result.put("tools", tools);

        sendResult(id, result);
    }

    private void handleToolsCall(Object id, Map<String, Object> params) {
        if (!initialized) {
            sendError(id, -32002, "Server not initialized");
            return;
        }

        String toolName = (String) params.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", new HashMap<>());

        try {
            Map<String, Object> result = new HashMap<>();

            switch (toolName) {
                case "echo":
                    String message = (String) arguments.get("message");
                    if (message == null) {
                        sendError(id, -32602, "Missing required parameter: message");
                        return;
                    }

                    List<Map<String, Object>> echoContent = new ArrayList<>();
                    Map<String, Object> echoText = new HashMap<>();
                    echoText.put("type", "text");
                    echoText.put("text", "Echo: " + message);
                    echoContent.add(echoText);

                    result.put("content", echoContent);
                    result.put("isError", false);
                    break;

                case "write_file":
                    String filename = (String) arguments.get("filename");
                    String content = (String) arguments.get("content");

                    if (filename == null || content == null) {
                        sendError(id, -32602, "Missing required parameters: filename and content");
                        return;
                    }

                    // Sanitize filename to prevent directory traversal
                    String sanitizedFilename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
                    Path filePath = TEMP_DIR.resolve(sanitizedFilename);

                    Files.writeString(filePath, content, StandardCharsets.UTF_8);

                    List<Map<String, Object>> writeContent = new ArrayList<>();
                    Map<String, Object> writeText = new HashMap<>();
                    writeText.put("type", "text");
                    writeText.put("text", "File written successfully: " + filePath.toString());
                    writeContent.add(writeText);

                    result.put("content", writeContent);
                    result.put("isError", false);
                    break;

                default:
                    sendError(id, -32601, "Unknown tool: " + toolName);
                    return;
            }

            sendResult(id, result);

        } catch (Exception e) {
            List<Map<String, Object>> errorContent = new ArrayList<>();
            Map<String, Object> errorText = new HashMap<>();
            errorText.put("type", "text");
            errorText.put("text", "Error executing tool: " + e.getMessage());
            errorContent.add(errorText);

            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("content", errorContent);
            errorResult.put("isError", true);

            sendResult(id, errorResult);
        }
    }

    private void sendResult(Object id, Map<String, Object> result) {
        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);

        String json = toJson(response);
        writer.println(json);
        writer.flush();
    }

    private void sendError(Object id, int code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);

        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", error);

        String json = toJson(response);
        writer.println(json);
        writer.flush();
    }

    // Simple JSON parser - parses basic JSON structures
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new IllegalArgumentException("Invalid JSON object");
        }

        Map<String, Object> result = new HashMap<>();
        String content = json.substring(1, json.length() - 1).trim();

        if (content.isEmpty()) {
            return result;
        }

        List<String> pairs = splitJsonPairs(content);

        for (String pair : pairs) {
            int colonIndex = pair.indexOf(':');
            if (colonIndex == -1) continue;

            String key = pair.substring(0, colonIndex).trim();
            String value = pair.substring(colonIndex + 1).trim();

            // Remove quotes from key
            key = key.replaceAll("^\"|\"$", "");

            result.put(key, parseValue(value));
        }

        return result;
    }

    private List<String> splitJsonPairs(String content) {
        List<String> pairs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int braceDepth = 0;
        int bracketDepth = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (escape) {
                current.append(c);
                escape = false;
                continue;
            }

            if (c == '\\') {
                escape = true;
                current.append(c);
                continue;
            }

            if (c == '"') {
                inString = !inString;
                current.append(c);
                continue;
            }

            if (inString) {
                current.append(c);
                continue;
            }

            if (c == '{') braceDepth++;
            else if (c == '}') braceDepth--;
            else if (c == '[') bracketDepth++;
            else if (c == ']') bracketDepth--;

            if (c == ',' && braceDepth == 0 && bracketDepth == 0) {
                pairs.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        if (!current.isEmpty()) {
            pairs.add(current.toString().trim());
        }

        return pairs;
    }

    private Object parseValue(String value) {
        value = value.trim();

        if (value.equals("null")) return null;
        if (value.equals("true")) return true;
        if (value.equals("false")) return false;

        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
        }

        if (value.startsWith("{") && value.endsWith("}")) {
            return parseJson(value);
        }

        if (value.startsWith("[") && value.endsWith("]")) {
            return parseArray(value);
        }

        // Try to parse as number
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            } else {
                return Long.parseLong(value);
            }
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private List<Object> parseArray(String json) {
        json = json.trim();
        if (!json.startsWith("[") || !json.endsWith("]")) {
            throw new IllegalArgumentException("Invalid JSON array");
        }

        List<Object> result = new ArrayList<>();
        String content = json.substring(1, json.length() - 1).trim();

        if (content.isEmpty()) {
            return result;
        }

        List<String> elements = splitJsonArray(content);
        for (String element : elements) {
            result.add(parseValue(element));
        }

        return result;
    }

    private List<String> splitJsonArray(String content) {
        List<String> elements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int braceDepth = 0;
        int bracketDepth = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (escape) {
                current.append(c);
                escape = false;
                continue;
            }

            if (c == '\\') {
                escape = true;
                current.append(c);
                continue;
            }

            if (c == '"') {
                inString = !inString;
                current.append(c);
                continue;
            }

            if (inString) {
                current.append(c);
                continue;
            }

            if (c == '{') braceDepth++;
            else if (c == '}') braceDepth--;
            else if (c == '[') bracketDepth++;
            else if (c == ']') bracketDepth--;

            if (c == ',' && braceDepth == 0 && bracketDepth == 0) {
                elements.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        if (!current.isEmpty()) {
            elements.add(current.toString().trim());
        }

        return elements;
    }

    // Simple JSON serializer
    private String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof Boolean) return obj.toString();
        if (obj instanceof Number) return obj.toString();
        if (obj instanceof String) return "\"" + escapeJson((String) obj) + "\"";

        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) obj;
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
                sb.append(toJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }

        if (obj instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) obj;
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }

        return "\"" + escapeJson(obj.toString()) + "\"";
    }

    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    public static void main(String[] args) {
        try {
            TestMcpServer server = new TestMcpServer(System.in, System.out);
            server.start();
        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
