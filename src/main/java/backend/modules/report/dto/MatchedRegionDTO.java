package backend.modules.report.dto;

/**
 * Standardized DTO representing a matched line range between file A and file B.
 *
 * @param startLineA starting line of the matched block in file A
 * @param endLineA   ending line of the matched block in file A
 * @param startLineB starting line of the matched block in file B
 * @param endLineB   ending line of the matched block in file B
 */
public record MatchedRegionDTO(int startLineA, int endLineA, int startLineB, int endLineB) {}
