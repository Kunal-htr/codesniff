package backend.modules.history.dto;

import java.util.List;

public class UsageStatsDTO {
    private int currentStreak;
    private int totalAnalyses;
    private List<DailyCountDTO> dailyCounts;

    public UsageStatsDTO(int currentStreak, int totalAnalyses, List<DailyCountDTO> dailyCounts) {
        this.currentStreak = currentStreak;
        this.totalAnalyses = totalAnalyses;
        this.dailyCounts = dailyCounts;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(int currentStreak) {
        this.currentStreak = currentStreak;
    }

    public int getTotalAnalyses() {
        return totalAnalyses;
    }

    public void setTotalAnalyses(int totalAnalyses) {
        this.totalAnalyses = totalAnalyses;
    }

    public List<DailyCountDTO> getDailyCounts() {
        return dailyCounts;
    }

    public void setDailyCounts(List<DailyCountDTO> dailyCounts) {
        this.dailyCounts = dailyCounts;
    }
}
