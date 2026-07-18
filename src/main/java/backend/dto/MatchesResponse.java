package backend.dto;

import java.util.List;

/**
 * Top-level response for the GET /api/report/{id}/matches endpoint.
 *
 * @param fileA        DTO for file A details
 * @param fileB        DTO for file B details
 * @param matchedLines shared list of matched line regions between file A and file B
 */
public record MatchesResponse(FileInfoDTO fileA, FileInfoDTO fileB, List<MatchedRegionDTO> matchedLines) {}
