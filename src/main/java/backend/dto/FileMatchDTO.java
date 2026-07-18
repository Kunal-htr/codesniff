package backend.dto;

import java.util.List;

/**
 * Standardized DTO containing raw source code and matched line regions for a single file.
 *
 * @param name         original file name
 * @param rawCode      the raw un-normalized source code content
 * @param matchedLines list of matched regions associated with this file
 */
public record FileMatchDTO(String name, String rawCode, List<MatchedRegionDTO> matchedLines) {}
