package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity @Table(name="review_reward_context")
public class ReviewRewardContextEntity {
    @EmbeddedId private ReviewRewardContextKey id;
    @Column(name="user_id",nullable=false,updatable=false) private long userId;
    @Column(name="policy_version",nullable=false,length=32,updatable=false) private String policyVersion;
    @Column(name="cycle_key",nullable=false,length=64,updatable=false,columnDefinition="char(64)") private String cycleKey;
    @Column(name="selected_at",nullable=false,updatable=false,columnDefinition="datetime(6)") private Instant selectedAt;
    @Column(name="due_at",nullable=false,updatable=false,columnDefinition="datetime(6)") private Instant dueAt;
    @Column(nullable=false,updatable=false) private int stage;
    @Column(name="needs_practice",nullable=false,updatable=false) private boolean needsPractice;
    @Column(name="has_mistakes",nullable=false,updatable=false) private boolean hasMistakes;
    @Column(nullable=false,updatable=false) private boolean eligible;
    @Column(name="last_submission_id",nullable=false,updatable=false) private long lastSubmissionId;
    protected ReviewRewardContextEntity() {}
    public ReviewRewardContextEntity(long attemptId, long userId, ReviewProgressEntity p, String policyVersion, String cycleKey, Instant at) {
        id=new ReviewRewardContextKey(attemptId,p.getId().getObjectiveId()); this.userId=userId;
        this.policyVersion=policyVersion; this.cycleKey=cycleKey; selectedAt=at.truncatedTo(ChronoUnit.MICROS);
        dueAt=p.getNextReviewAt().truncatedTo(ChronoUnit.MICROS); stage=p.getStage();
        needsPractice=p.isNeedsPractice(); hasMistakes=p.isHasMistakes(); lastSubmissionId=p.getLastSubmissionId();
        eligible=stage>0&&!needsPractice&&!hasMistakes&&!dueAt.isAfter(selectedAt);
    }
    public ReviewRewardContextKey getId() { return id; }
    public long getUserId() { return userId; }
    public String getPolicyVersion() { return policyVersion; }
    public String getCycleKey() { return cycleKey; }
    public Instant getSelectedAt() { return selectedAt; }
    public Instant getDueAt() { return dueAt; }
    public int getStage() { return stage; }
    public boolean isEligible() { return eligible; }
    public long getLastSubmissionId() { return lastSubmissionId; }
}
