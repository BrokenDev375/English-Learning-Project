package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ReviewEvidenceKey implements Serializable {
    @Column(name = "user_id") private Long userId;
    @Column(name = "objective_id") private Long objectiveId;
    @Column(nullable = false, length = 8) private String difficulty;
    protected ReviewEvidenceKey() {}
    public ReviewEvidenceKey(long userId, long objectiveId, String difficulty) {
        this.userId = userId; this.objectiveId = objectiveId; this.difficulty = difficulty;
    }
    public Long getUserId() { return userId; }
    public Long getObjectiveId() { return objectiveId; }
    public String getDifficulty() { return difficulty; }
    @Override public boolean equals(Object other) {
        return other instanceof ReviewEvidenceKey k && Objects.equals(userId, k.userId)
            && Objects.equals(objectiveId, k.objectiveId) && Objects.equals(difficulty, k.difficulty);
    }
    @Override public int hashCode() { return Objects.hash(userId, objectiveId, difficulty); }
}
