package backend.modules.history;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AnalysisHistoryRepository extends JpaRepository<AnalysisHistory, UUID> {

    List<AnalysisHistory> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<AnalysisHistory> findByUserIdOrderByIsPinnedDescCreatedAtDesc(UUID userId);
}
