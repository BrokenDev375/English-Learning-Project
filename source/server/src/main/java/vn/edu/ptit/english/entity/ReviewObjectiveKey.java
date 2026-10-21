package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ReviewObjectiveKey implements Serializable {
    @Column(name = "user_id") private Long userId;
    @Column(name = "objective_id") private Long objectiveId;
    protected ReviewObjectiveKey() {}
    public ReviewObjectiveKey(long userId, long objectiveId) { this.userId = userId; this.objectiveId = objectiveId; }
    public Long getUserId() { return userId; }
    public Long getObjectiveId() { return objectiveId; }
    @Override public boolean equals(Object other) {
        return other instanceof ReviewObjectiveKey k && Objects.equals(userId, k.userId) && Objects.equals(objectiveId, k.objectiveId);
    }
    @Override public int hashCode() { return Objects.hash(userId, objectiveId); }
}
