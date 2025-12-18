# MCP HTTP/SSE Authentication Implementation

## Overview
Added support for custom HTTP headers (Bearer token authentication) to enable authentication with MCP servers over HTTP/SSE transport, such as Context7 and other authenticated services.

## Changes Made

### 1. McpServerConfig.java
**File:** `/Users/mehdi/Workspaces/Labs/OllamAssist/src/main/java/fr/baretto/ollamassist/setting/McpServerConfig.java`

**Added field:**
```java
/**
 * Authentication token for HTTP/SSE transport (e.g., Bearer token for API authentication).
 * This will be sent as "Authorization: Bearer <token>" header.
 */
private String authToken = "";
```

**Benefits:**
- Lombok `@Data` annotation automatically generates getters/setters
- Persisted automatically as part of the configuration state
- Default empty string ensures backward compatibility

### 2. McpClientManager.java
**File:** `/Users/mehdi/Workspaces/Labs/OllamAssist/src/main/java/fr/baretto/ollamassist/mcp/McpClientManager.java`

**Modified method:** `createHttpTransport(McpServerConfig config)`

**Changes:**
```java
private McpTransport createHttpTransport(McpServerConfig config) {
    if (config.getUrl() == null || config.getUrl().isEmpty()) {
        log.error("No URL specified for HTTP/SSE transport: {}", config.getName());
        return null;
    }

    log.debug("Creating HTTP/SSE transport with URL: {}", config.getUrl());

    var builder = StreamableHttpMcpTransport.builder()
            .url(config.getUrl())
            .logRequests(config.isLogRequests())
            .logResponses(config.isLogResponses());

    // Add authentication if configured
    if (config.getAuthToken() != null && !config.getAuthToken().isEmpty()) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + config.getAuthToken());
        builder.customHeaders(headers);
        log.debug("Added Bearer token authentication for server: {}", config.getName());
    }

    return builder.build();
}
```

**Key features:**
- Only adds headers when authToken is configured (non-null and non-empty)
- Uses standard "Authorization: Bearer <token>" format
- Logs when authentication is added (without exposing token value)
- Maintains backward compatibility with existing configurations

### 3. AddEditMcpServerDialog.java
**File:** `/Users/mehdi/Workspaces/Labs/OllamAssist/src/main/java/fr/baretto/ollamassist/setting/dialogs/AddEditMcpServerDialog.java`

**Added UI field:**
```java
private final JBTextField authTokenField = new JBTextField(30);
```

**Added to HTTP panel in `createDynamicConfigPanel()`:**
- Label: "API Key / Auth Token (optional):"
- Input field: `authTokenField`
- Help text: "Will be sent as 'Authorization: Bearer <token>' header"

**Updated `loadConfig()` method:**
```java
authTokenField.setText(config.getAuthToken());
```

**Updated `getConfig()` method:**
```java
config.setAuthToken(authTokenField.getText().trim());
```

**UI Layout:**
The auth token field appears in the HTTP/SSE transport configuration panel, positioned after the URL field:
1. URL field
2. URL help text
3. **Auth Token field** (new)
4. **Auth Token help text** (new)

## Usage

### For Users:
1. Open plugin settings (OllamAssist → Settings)
2. Navigate to MCP Servers tab
3. Add or edit an HTTP/SSE server
4. Enter the server URL
5. Optionally enter your API key or authentication token in the "API Key / Auth Token" field
6. Save the configuration

### For Services Like Context7:
Context7 requires authentication via Bearer token. Users can now:
1. Obtain their API key from Context7
2. Configure the MCP server with transport type "HTTP/SSE"
3. Enter the Context7 MCP endpoint URL
4. Enter their API key in the auth token field
5. The plugin will automatically send the token as an HTTP header

## Technical Details

### HTTP Header Format
When configured, the token is sent as:
```
Authorization: Bearer <token>
```

### Backward Compatibility
- Empty or null token values result in no authentication headers being added
- Existing configurations without authToken will continue to work
- The field is optional and clearly marked as such in the UI

### Security Considerations
- Token values are stored in IntelliJ's configuration files
- Token values are NOT logged (only the fact that authentication was added)
- Token values are trimmed to avoid whitespace issues
- Uses standard JBTextField (consider using JPasswordField for enhanced security in future)

## Testing

### Build Verification
The implementation has been verified to compile successfully:
```bash
./gradlew build --no-daemon -x test
# BUILD SUCCESSFUL
```

### Manual Testing Checklist
- [ ] Add new HTTP/SSE server with auth token
- [ ] Edit existing HTTP/SSE server to add auth token
- [ ] Verify auth token persists after IDE restart
- [ ] Verify connection works with authenticated service (e.g., Context7)
- [ ] Verify empty auth token doesn't break unauthenticated servers
- [ ] Verify auth token field only appears for HTTP/SSE transport type

## Future Enhancements

Potential improvements for future versions:

1. **Password Field**: Use `JPasswordField` instead of `JBTextField` for better security
2. **Multiple Headers**: Support multiple custom headers beyond just Authorization
3. **Header Templates**: Support different authentication schemes (Basic, API Key, etc.)
4. **Credential Storage**: Integrate with IntelliJ's credential store for secure token storage
5. **Token Validation**: Add basic validation for token format
6. **Environment Variables**: Allow referencing environment variables for tokens

## Files Modified

1. `/Users/mehdi/Workspaces/Labs/OllamAssist/src/main/java/fr/baretto/ollamassist/setting/McpServerConfig.java`
2. `/Users/mehdi/Workspaces/Labs/OllamAssist/src/main/java/fr/baretto/ollamassist/mcp/McpClientManager.java`
3. `/Users/mehdi/Workspaces/Labs/OllamAssist/src/main/java/fr/baretto/ollamassist/setting/dialogs/AddEditMcpServerDialog.java`

## References

- MCP (Model Context Protocol) Specification
- LangChain4j StreamableHttpMcpTransport documentation
- Context7 MCP server authentication requirements