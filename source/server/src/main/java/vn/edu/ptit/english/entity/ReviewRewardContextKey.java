package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ReviewRewardContextKey implements Serializable {
    @Column(name="attempt_id") private long attemptId;
    @Column(name="objective_id") private long objectiveId;
    protected ReviewRewardContextKey() {}
    public ReviewRewardContextKey(long attemptId, long objectiveId) { this.attemptId=attemptId; this.objectiveId=objectiveId; }
    public long getAttemptId() { return attemptId; }
    public long getObjectiveId() { return objectiveId; }
    @Override public boolean equals(Object other) { return other instanceof ReviewRewardContextKey k && k.attemptId==attemptId && k.objectiveId==objectiveId; }
    @Override public int hashCode() { return Objects.hash(attemptId,objectiveId); }
}
