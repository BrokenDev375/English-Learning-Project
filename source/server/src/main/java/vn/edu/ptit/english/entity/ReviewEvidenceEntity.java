package vn.edu.ptit.english.entity;

import jakarta.persistence.*;

@Entity @Table(name = "review_difficulty_evidence")
public class ReviewEvidenceEntity {
    @EmbeddedId private ReviewEvidenceKey id;
    @Column(name = "fresh_set_count", nullable = false) private int freshSetCount;
    @Column(name = "fresh_answered_count", nullable = false) private int freshAnsweredCount;
    @Column(name = "fresh_correct_count", nullable = false) private int freshCorrectCount;
    @Column(name = "good_set_streak", nullable = false) private int goodSetStreak;
    protected ReviewEvidenceEntity() {}
    public ReviewEvidenceEntity(long userId, long objectiveId, String difficulty) {
        id = new ReviewEvidenceKey(userId, objectiveId, difficulty);
    }
    public void record(int answered, int correct, int minimumQuestions, int goodPercent) {
        record(answered, correct, minimumQuestions, goodPercent, false);
    }
    public void record(int answered, int correct, int minimumQuestions, int goodPercent, boolean independentMistake) {
        freshAnsweredCount += answered; freshCorrectCount += correct;
        if (independentMistake || (answered < minimumQuestions && correct < answered)) goodSetStreak = 0;
        if (answered >= minimumQuestions) {
            freshSetCount++;
            goodSetStreak = !independentMistake && correct * 100L >= answered * (long) goodPercent ? goodSetStreak + 1 : 0;
        }
    }
    public ReviewEvidenceKey getId() { return id; }
    public int getFreshSetCount() { return freshSetCount; }
    public int getFreshAnsweredCount() { return freshAnsweredCount; }
    public int getFreshCorrectCount() { return freshCorrectCount; }
    public int getGoodSetStreak() { return goodSetStreak; }
}
