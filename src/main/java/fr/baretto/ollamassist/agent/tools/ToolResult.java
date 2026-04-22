package fr.baretto.ollamassist.agent.tools;

public final class ToolResult {

    private final boolean success;
    private final String output;
    private final String errorMessage;

    private ToolResult(boolean success, String output, String errorMessage) {
        this.success = success;
        this.output = output != null ? output : "";
        this.errorMessage = errorMessage != null ? errorMessage : "";
    }

    public static ToolResult success(String output) {
        return new ToolResult(true, output, null);
    }

    public static ToolResult failure(String errorMessage) {
        return new ToolResult(false, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getOutput() {
        return output;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return success
                ? "ToolResult{success, output='" + output + "'}"
                : "ToolResult{failure, error='" + errorMessage + "'}";
    }
}
