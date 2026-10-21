package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.List;

@Entity @Table(name = "review_objective_progress")
public class ReviewProgressEntity {
    @EmbeddedId private ReviewObjectiveKey id;
    @Column(name = "submission_count", nullable = false) private int submissionCount;
    @Column(name = "first_answered_count", nullable = false) private int firstAnsweredCount;
    @Column(name = "first_correct_count", nullable = false) private int firstCorrectCount;
    @Column(name = "assisted_count", nullable = false) private int assistedCount;
    @Column(name = "unanswered_count", nullable = false) private int unansweredCount;
    @Column(name = "last_wrong_count", nullable = false) private int lastWrongCount;
    @Column(name = "last_independent_answered", nullable = false) private int lastIndependentAnswered;
    @Column(name = "last_independent_correct", nullable = false) private int lastIndependentCorrect;
    @Column(name = "needs_practice", nullable = false) private boolean needsPractice = true;
    @Column(name = "has_mistakes", nullable = false) private boolean hasMistakes;
    @Column(nullable = false) private int stage;
    @Column(name = "current_difficulty", nullable = false, length = 8) private String currentDifficulty = "EASY";
    @Column(name = "last_practiced_at", nullable = false, columnDefinition = "datetime(6)") private Instant lastPracticedAt;
    @Column(name = "next_review_at", nullable = false, columnDefinition = "datetime(6)") private Instant nextReviewAt;
    @Column(name = "last_success_on") private LocalDate lastSuccessOn;
    @Column(name = "last_submission_id", nullable = false) private long lastSubmissionId;
    protected ReviewProgressEntity() {}
    public ReviewProgressEntity(long userId, long objectiveId) { id = new ReviewObjectiveKey(userId, objectiveId); }

    /** One aggregate per objective/submission, never one schedule jump per question. */
    public void record(long submissionId, Instant at, int answered, int correct, int assisted, int blank,
                       int wrong, int independent, int independentCorrect, String difficulty,
                       List<Integer> intervals, ZoneId zone) {
        submissionCount++;
        firstAnsweredCount += answered; firstCorrectCount += correct;
        assistedCount += assisted; unansweredCount += blank;
        lastWrongCount = wrong; lastIndependentAnswered = independent; lastIndependentCorrect = independentCorrect;
        if (difficulty != null) currentDifficulty = difficulty;
        lastSubmissionId = submissionId; lastPracticedAt = at;
        if (answered == 0) {
            // An empty submission cannot erase an existing weakness or prove retention.
            if (nextReviewAt == null) nextReviewAt = at.plus(Duration.ofDays(intervals.get(0)));
            return;
        }
        if (wrong > 0) {
            hasMistakes = true;
            needsPractice = true; stage = 0; nextReviewAt = at;
        } else if (independent > 0 && independentCorrect == independent) {
            hasMistakes = false;
            needsPractice = false;
            LocalDate today = at.atZone(zone).toLocalDate();
            if (!today.equals(lastSuccessOn)) stage = Math.min(intervals.size(), stage + 1);
            stage = Math.max(1, stage);
            lastSuccessOn = today;
            nextReviewAt = at.plus(Duration.ofDays(intervals.get(stage - 1)));
        } else {
            needsPractice = true;
            nextReviewAt = at.plus(Duration.ofDays(intervals.get(0)));
        }
    }
    public ReviewObjectiveKey getId() { return id; }
    public int getSubmissionCount() { return submissionCount; }
    public int getFirstAnsweredCount() { return firstAnsweredCount; }
    public int getFirstCorrectCount() { return firstCorrectCount; }
    public int getAssistedCount() { return assistedCount; }
    public int getUnansweredCount() { return unansweredCount; }
    public int getLastWrongCount() { return lastWrongCount; }
    public int getLastIndependentAnswered() { return lastIndependentAnswered; }
    public int getLastIndependentCorrect() { return lastIndependentCorrect; }
    public boolean isNeedsPractice() { return needsPractice; }
    public boolean isHasMistakes() { return hasMistakes; }
    public int getStage() { return stage; }
    public String getCurrentDifficulty() { return currentDifficulty; }
    public Instant getLastPracticedAt() { return lastPracticedAt; }
    public Instant getNextReviewAt() { return nextReviewAt; }
    public LocalDate getLastSuccessOn() { return lastSuccessOn; }
    public long getLastSubmissionId() { return lastSubmissionId; }
}
