package backend.modules.history.dto;

import backend.modules.report.dto.FileInfoDTO;
import backend.modules.report.dto.MatchedRegionDTO;
import backend.modules.report.dto.ReportResponse;

import java.util.List;

public record HistoricalPairDTO(
        String id,
        FileInfoDTO fileA,
        FileInfoDTO fileB,
        double similarityScore,
        List<MatchedRegionDTO> matchedLines,
        ReportResponse reportData
) {}
