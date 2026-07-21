package backend.modules.history.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record HistoryListItemDTO(
        UUID id,
        String batchId,
        List<String> fileNames,
        int totalPairs,
        double highestSimilarity,
        double averageSimilarity,
        boolean isPinned,
        OffsetDateTime createdAt
) {}
