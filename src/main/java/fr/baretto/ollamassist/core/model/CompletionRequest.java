package fr.baretto.ollamassist.core.model;

import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

/**
 * Simple request object for code completion with AI model.
 */
@Data
@Builder
public class CompletionRequest {

    @NotNull
    private final String context;

    @NotNull
    private final String fileExtension;
}