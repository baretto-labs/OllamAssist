package fr.baretto.ollamassist.auth;

import fr.baretto.ollamassist.setting.OllamAssistSettings;
import lombok.experimental.UtilityClass;

import java.util.Base64;

/**
 * Helper class for handling basic authentication for Ollama connections.
 */
@UtilityClass
public class AuthenticationHelper {

    private static final String CREDENTIALS_FORMAT = "%s:%s";

    /**
     * Creates a basic authentication header value if username and password are provided.
     * 
     * @return Base64 encoded "username:password" string, or null if credentials are not provided
     */
    public static String createBasicAuthHeader() {
        OllamAssistSettings settings = OllamAssistSettings.getInstance();
        String username = settings.getUsername();
        String password = settings.getPassword();
        
        if (username == null || username.trim().isEmpty() || 
            password == null || password.trim().isEmpty()) {
            return null;
        }
        
        String credentials = String.format(CREDENTIALS_FORMAT, username.trim(), password.trim());
        return Base64.getEncoder().encodeToString(credentials.getBytes());
    }
    
    /**
     * Checks if authentication is configured.
     * 
     * @return true if both username and password are provided, false otherwise
     */
    public static boolean isAuthenticationConfigured() {
        OllamAssistSettings settings = OllamAssistSettings.getInstance();
        String username = settings.getUsername();
        String password = settings.getPassword();
        
        return username != null && !username.trim().isEmpty() && 
               password != null && !password.trim().isEmpty();
    }
}
