package fr.baretto.ollamassist.setting;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Helper class for automatic synchronization between OllamAssistSettings.State and ConfigurationPanel.
 * Uses reflection to automatically map fields, reducing maintenance overhead when adding new settings.
 * <p>
 * Excluded fields (managed directly by UI components at runtime):
 * - webSearchEnabled: managed by PromptPanel
 * - ragEnabled: managed by PromptPanel
 * - uistate: managed by OllamaContent
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SettingsBindingHelper {

    private static final Set<String> EXCLUDED_FIELDS = Set.of(
            "webSearchEnabled",
            "ragEnabled",
            "uistate"
    );
    private static final String GETTER_PREFIX = "get";
    private static final String SETTER_PREFIX = "set";

    /**
     * Load settings from State to ConfigurationPanel (or any object with matching setters).
     * Automatically maps all public fields in State (except excluded ones) to corresponding setters in the panel object.
     */
    public static void loadSettings(OllamAssistSettings.State state, Object panel) {
        if (state == null || panel == null) {
            return;
        }

        Field[] fields = OllamAssistSettings.State.class.getDeclaredFields();
        for (Field field : fields) {
            String fieldName = field.getName();

            // Skip excluded fields
            if (EXCLUDED_FIELDS.contains(fieldName)) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object value = field.get(state);

                if (value == null) {
                    continue;
                }

                // Try to find corresponding setter in ConfigurationPanel
                String setterName = getSetterName(fieldName);
                Method setter = findSetter(panel.getClass(), setterName, field.getType());

                if (setter != null) {
                    setter.invoke(panel, value);
                } else {
                    log.warn("No setter found for field '{}' (expected method: {})", fieldName, setterName);
                }
            } catch (Exception e) {
                log.error("Failed to load setting field '{}'", fieldName, e);
            }
        }
    }

    /**
     * Save settings from ConfigurationPanel (or any object with matching getters) to State.
     * Automatically maps all getters in the panel object to corresponding public fields in State (except excluded ones).
     */
    public static void saveSettings(Object panel, OllamAssistSettings.State state) {
        if (state == null || panel == null) {
            return;
        }

        Field[] fields = OllamAssistSettings.State.class.getDeclaredFields();
        for (Field field : fields) {
            String fieldName = field.getName();

            // Skip excluded fields
            if (EXCLUDED_FIELDS.contains(fieldName)) {
                continue;
            }

            try {
                field.setAccessible(true);

                // Try to find corresponding getter in ConfigurationPanel
                String getterName = getGetterName(fieldName);
                Method getter = findGetter(panel.getClass(), getterName);

                if (getter != null) {
                    Object value = getter.invoke(panel);

                    // Only save non-null values
                    if (value != null) {
                        field.set(state, value);
                    } else {
                        log.debug("Skipping null value for field '{}'", fieldName);
                    }
                } else {
                    log.warn("No getter found for field '{}' (expected method: {})", fieldName, getterName);
                }
            } catch (Exception e) {
                log.error("Failed to save setting field '{}'", fieldName, e);
            }
        }
    }

    /**
     * Check if ConfigurationPanel (or any object with matching getters) has modifications compared to State.
     * Automatically compares all public fields in State (except excluded ones) with corresponding getters in the panel object.
     */
    public static boolean isModified(OllamAssistSettings.State state, Object panel) {
        if (state == null || panel == null) {
            return false;
        }

        Field[] fields = OllamAssistSettings.State.class.getDeclaredFields();
        for (Field field : fields) {
            String fieldName = field.getName();

            // Skip excluded fields
            if (EXCLUDED_FIELDS.contains(fieldName)) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object stateValue = field.get(state);

                // Try to find corresponding getter in ConfigurationPanel
                String getterName = getGetterName(fieldName);
                Method getter = findGetter(panel.getClass(), getterName);

                if (getter != null) {
                    Object panelValue = getter.invoke(panel);

                    // Skip comparison if panel value is null (e.g., models still loading)
                    if (panelValue == null) {
                        log.debug("Skipping comparison for field '{}' (panel value is null)", fieldName);
                        continue;
                    }

                    // Compare values
                    if (!areEqual(stateValue, panelValue)) {
                        log.debug("Modification detected for field '{}': state='{}', panel='{}'",
                                fieldName, stateValue, panelValue);
                        return true;
                    }
                }
            } catch (Exception e) {
                log.error("Failed to check modification for field '{}'", fieldName, e);
            }
        }

        return false;
    }

    /**
     * Generate setter name from field name.
     * Examples: chatOllamaUrl -> setChatOllamaUrl, timeout -> setTimeout
     */
    private static String getSetterName(String fieldName) {
        if (fieldName.equals("indexationSize")) {
            return "setMaxDocuments"; // Special case: field name != panel method name
        }
        return SETTER_PREFIX + capitalize(fieldName);
    }

    /**
     * Generate getter name from field name.
     * Examples: chatOllamaUrl -> getChatOllamaUrl, timeout -> getTimeout
     */
    private static String getGetterName(String fieldName) {
        if (fieldName.equals("indexationSize")) {
            return "getMaxDocuments"; // Special case: field name != panel method name
        }
        if (fieldName.equals("chatModelName")) {
            return "getChatModel"; // Special case
        }
        if (fieldName.equals("completionModelName")) {
            return "getCompletionModel"; // Special case
        }
        if (fieldName.equals("embeddingModelName")) {
            return "getEmbeddingModel"; // Special case
        }
        return GETTER_PREFIX + capitalize(fieldName);
    }

    /**
     * Find setter method in class by name and parameter type.
     */
    private static Method findSetter(Class<?> clazz, String methodName, Class<?> paramType) {
        try {
            return clazz.getMethod(methodName, paramType);
        } catch (NoSuchMethodException e) {
            // Special handling for model name setters
            if (methodName.contains("Model")) {
                try {
                    return clazz.getMethod(methodName.replace("setModel", "setModelName"), paramType);
                } catch (NoSuchMethodException ex) {
                    return null;
                }
            }
            return null;
        }
    }

    /**
     * Find getter method in class by name.
     */
    private static Method findGetter(Class<?> clazz, String methodName) {
        try {
            return clazz.getMethod(methodName);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Compare two values for equality.
     * Handles null values, uses equalsIgnoreCase for Strings, and trims whitespace.
     */
    private static boolean areEqual(Object value1, Object value2) {
        if (value1 == null && value2 == null) {
            return true;
        }
        if (value1 == null || value2 == null) {
            return false;
        }

        // Special handling for String comparison (case insensitive + trim)
        if (value1 instanceof String str1 && value2 instanceof String str2) {
            str1 = str1.trim();
            str2 = str2.trim();
            return str1.equalsIgnoreCase(str2);
        }

        return value1.equals(value2);
    }

    /**
     * Capitalize first letter of string.
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}