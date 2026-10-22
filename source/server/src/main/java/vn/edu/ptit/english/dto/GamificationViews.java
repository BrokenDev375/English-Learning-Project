package vn.edu.ptit.english.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.*;
import java.util.List;

public final class GamificationViews {
    private GamificationViews() {}
    public record Day(@JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate date,int awardedXp,int capXp,int qualifiedSubmissions,boolean isActive) {}
    public record Streak(int current,int best,@JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate lastActiveDate) {}
    public record BadgeCount(int earnedCount,int totalCount) {}
    public record Backlog(long pendingSubmissions,long deferredSubmissions) {}
    public record Summary(@JsonFormat(shape=JsonFormat.Shape.STRING) Instant serverTime,String zone,String policyVersion,
        @JsonFormat(shape=JsonFormat.Shape.STRING) Instant activatedAt,long revision,long totalXp,long activityLevel,
        long levelFloorXp,long nextLevelAtXp,Day day,Streak streak,BadgeCount badges,Backlog backlog,String processingState) {}
    public record Sync(String requestId,int processedSubmissions,long remainingSubmissions,long deferredSubmissions,long revision,
        @JsonFormat(shape=JsonFormat.Shape.STRING) Instant processedAt) {}
    public record Decision(String rewardType,Long attemptQuestionId,Long objectiveId,int requestedXp,int awardedXp,String reason) {}
    public record LeagueAward(Long seasonId,int countedXp,String reason) {}
    public record Activity(long submissionId,long attemptId,String mode,@JsonFormat(shape=JsonFormat.Shape.STRING) Instant submittedAt,
        @JsonFormat(shape=JsonFormat.Shape.STRING) Instant processedAt,@JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate activityDate,
        String policyVersion,String disposition,int requestedXp,int awardedXp,boolean qualifiedActivity,List<Decision> decisions,LeagueAward league) {
        public Activity { decisions=List.copyOf(decisions); }
    }
    public record RequestedSubmission(long submissionId,String state) {}
    public record ActivitiesPage(List<Activity> items,long totalElements,int page,int size,int totalPages,RequestedSubmission requestedSubmission) {}
    public record Badge(String code,String name,String description,@JsonFormat(shape=JsonFormat.Shape.STRING) Instant earnedAt,
        @JsonFormat(shape=JsonFormat.Shape.STRING) Instant evidenceSourceAt,int progress,int target) {}
    public record Badges(@JsonFormat(shape=JsonFormat.Shape.STRING) Instant serverTime,List<Badge> items) {}
}
