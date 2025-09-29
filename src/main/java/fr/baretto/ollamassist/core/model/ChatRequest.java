package fr.baretto.ollamassist.core.model;

import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

/**
 * Simple request object for chat conversations with AI model.
 */
@Data
@Builder
public class ChatRequest {

    @NotNull
    private final String message;
}