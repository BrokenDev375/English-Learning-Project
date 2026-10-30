package vn.edu.ptit.english.dto;

import java.time.Instant;
import vn.edu.ptit.english.dto.CatalogViews.CatalogPage;

public final class ReviewViews {
    private ReviewViews() {}
    public record Sync(int processedSubmissions, long remainingSubmissions) {}
    public record Choices(int all, int priority, int wrong) {}
    public record ObjectiveReview(long objectiveId, long topicId, String name, String status, String reason,
        boolean needsReview, boolean hasMistakes, int stage, Instant lastPracticedAt, Instant nextReviewAt,
        int submissionCount, int firstAnsweredCount, int firstCorrectCount, int assistedCount, int unansweredCount,
        int lastWrongCount, int lastIndependentAnswered, int lastIndependentCorrect,
        int availableQuestionCount, int unseenQuestionCount, String currentDifficulty,
        String recommendedDifficulty, String recommendationReason,
        int freshSetCount, int freshAnsweredCount, int freshCorrectCount) {}
    public record Dashboard(Instant generatedAt, long pendingSubmissions, int totalObjectives,
        int needsReviewCount, int mistakeObjectives, CatalogPage<ObjectiveReview> objectives, Choices choices) {}
}
