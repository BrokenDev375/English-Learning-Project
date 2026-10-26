package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.ptit.english.enums.AttemptEnums.ProgressState;

@Entity @Table(name="lesson_progress")
public class LessonProgressEntity {
    @EmbeddedId private LessonProgressKey id;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable=false,length=16) private ProgressState state=ProgressState.STARTED;
    @Column(name="last_attempt_id",nullable=false) private long lastAttemptId;
    @Column(name="last_completed_version_id") private Long lastCompletedVersionId;
    @Column(name="completed_at",columnDefinition="datetime(6)") private Instant completedAt;
    @Column(name="last_score_percent",precision=5,scale=2) private BigDecimal lastScorePercent;
    @UpdateTimestamp @Column(name="updated_at",nullable=false,columnDefinition="datetime(6)") private Instant updatedAt;
    protected LessonProgressEntity() {}
    public LessonProgressEntity(long userId,long lessonId,long attempt){id=new LessonProgressKey(userId,lessonId); lastAttemptId=attempt;}
    public LessonProgressKey getId(){return id;} public ProgressState getState(){return state;} public long getLastAttemptId(){return lastAttemptId;}
    public Long getLastCompletedVersionId(){return lastCompletedVersionId;} public Instant getCompletedAt(){return completedAt;}
    public BigDecimal getLastScorePercent(){return lastScorePercent;} public Instant getUpdatedAt(){return updatedAt;}
    public void started(long attempt){lastAttemptId=attempt;}
    public boolean confirmCompletion(long version,Instant time) {
        if(state==ProgressState.COMPLETED)return false;
        state=ProgressState.COMPLETED;lastCompletedVersionId=version;completedAt=time;return true;
    }
    public void recordSubmission(long attempt,long version,BigDecimal score,boolean completed,Instant time) {
        lastAttemptId=attempt; lastScorePercent=score;
        if(completed){state=ProgressState.COMPLETED; lastCompletedVersionId=version; completedAt=time;}
    }
}
