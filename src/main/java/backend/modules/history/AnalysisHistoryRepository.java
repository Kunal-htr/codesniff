package backend.modules.history;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AnalysisHistoryRepository extends JpaRepository<AnalysisHistory, UUID> {

    List<AnalysisHistory> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<AnalysisHistory> findByUserIdOrderByIsPinnedDescCreatedAtDesc(UUID userId);

    org.springframework.data.domain.Page<AnalysisHistory> findByUserId(UUID userId, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT h FROM AnalysisHistory h WHERE h.userId = :userId AND (LOWER(h.batchId) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(cast(h.fileNames as string)) LIKE LOWER(CONCAT('%', :search, '%')))")
    org.springframework.data.domain.Page<AnalysisHistory> searchHistory(@org.springframework.data.repository.query.Param("userId") UUID userId, @org.springframework.data.repository.query.Param("search") String search, org.springframework.data.domain.Pageable pageable);
}
