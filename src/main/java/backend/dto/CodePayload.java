package backend.dto;

import java.util.List;

/**
 * Inbound request payload for code similarity analysis.
 * <p>
 * Contains a list of code submissions and optional analysis options.
 */
public record CodePayload(List<Submission> submissions, OptionsDTO options) {

    /** A single code submission with a name and its source content. */
    public record Submission(String name, String content) {}

    /** Optional tuning parameters for the analysis engine. */
    public record OptionsDTO(Boolean omitComments, Integer k, Integer window) {}
}
