package backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/**
 * Inbound request payload for code similarity analysis.
 * <p>
 * Contains a list of code submissions and optional analysis options.
 * All validation constraints are enforced at the controller layer via
 * {@code @Valid} before any business logic executes.
 */
public record CodePayload(

        @NotEmpty(message = "At least one submission is required")
        @Size(max = ValidationConstants.MAX_SUBMISSIONS,
              message = "Cannot exceed " + ValidationConstants.MAX_SUBMISSIONS + " submissions per batch")
        List<@Valid Submission> submissions,

        @Valid
        OptionsDTO options
) {

    /**
     * A single code submission with a name and its source content.
     * <p>
     * Both fields are required — empty content is rejected to prevent
     * degenerate AST/fingerprint results.
     */
    public record Submission(
            @NotBlank(message = "Submission name must not be blank")
            String name,

            @NotBlank(message = "Submission content must not be blank")
            String content
    ) {}

    /**
     * Optional tuning parameters for the analysis engine.
     * <p>
     * Bounds mirror {@code SimilarityEngine.Options} constraints:
     * k must be 3–64, window must be 1–128.
     */
    public record OptionsDTO(
            Boolean omitComments,

            @Min(value = 3, message = "k must be at least 3")
            @Max(value = 64, message = "k must be at most 64")
            Integer k,

            @Min(value = 1, message = "window must be at least 1")
            @Max(value = 128, message = "window must be at most 128")
            Integer window
    ) {}
}
