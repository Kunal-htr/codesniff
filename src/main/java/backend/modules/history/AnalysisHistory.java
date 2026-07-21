package backend.modules.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "analysis_history")
public class AnalysisHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "batch_id", nullable = false)
    private String batchId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "file_names", nullable = false, columnDefinition = "jsonb")
    private List<String> fileNames;

    @Column(name = "total_pairs", nullable = false)
    private Integer totalPairs;

    @Column(name = "highest_similarity", nullable = false)
    private Double highestSimilarity;

    @Column(name = "average_similarity", nullable = false)
    private Double averageSimilarity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "full_result_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode fullResultJson;

    @Column(name = "is_pinned", nullable = false)
    private boolean isPinned = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public AnalysisHistory() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public List<String> getFileNames() {
        return fileNames;
    }

    public void setFileNames(List<String> fileNames) {
        this.fileNames = fileNames;
    }

    public Integer getTotalPairs() {
        return totalPairs;
    }

    public void setTotalPairs(Integer totalPairs) {
        this.totalPairs = totalPairs;
    }

    public Double getHighestSimilarity() {
        return highestSimilarity;
    }

    public void setHighestSimilarity(Double highestSimilarity) {
        this.highestSimilarity = highestSimilarity;
    }

    public Double getAverageSimilarity() {
        return averageSimilarity;
    }

    public void setAverageSimilarity(Double averageSimilarity) {
        this.averageSimilarity = averageSimilarity;
    }

    public JsonNode getFullResultJson() {
        return fullResultJson;
    }

    public void setFullResultJson(JsonNode fullResultJson) {
        this.fullResultJson = fullResultJson;
    }

    public boolean isPinned() {
        return isPinned;
    }

    public void setPinned(boolean pinned) {
        isPinned = pinned;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
