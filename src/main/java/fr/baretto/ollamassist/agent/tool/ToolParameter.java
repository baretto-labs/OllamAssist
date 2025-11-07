package fr.baretto.ollamassist.agent.tool;

/**
 * Parameter definition for an agent tool.
 */
public record ToolParameter(
    String name,
    String type,
    String description,
    Boolean required
) {
    public ToolParameter {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Type cannot be null or empty");
        }
        if (required == null) {
            required = true;
        }
    }

    /**
     * Returns true if this parameter is required.
     */
    public boolean isRequired() {
        return required != null && required;
    }
}
