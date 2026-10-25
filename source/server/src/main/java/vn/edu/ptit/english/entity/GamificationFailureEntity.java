package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.*;
import java.io.Serializable;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.ptit.english.dto.GamificationViews.Activity;


@Entity @Table(name="gamification_processing_failures")
public class GamificationFailureEntity implements GamificationRow {
    @Id @Column(name="submission_id",nullable=false) private Long submissionId;
    @Column(name="user_id",nullable=false) private long userId;
    @Column(name="error_code",nullable=false,length=64) private String errorCode;
    @Column(name="attempts",nullable=false) private int attempts;
    @Column(name="retry_at",nullable=false,columnDefinition="datetime(6)") private Instant retryAt;
    protected GamificationFailureEntity() {}
    public Long getSubmissionId() { return submissionId; }
    public long getUserId() { return userId; }
    public String getErrorCode() { return errorCode; }
    public int getAttempts() { return attempts; }
    public Instant getRetryAt() { return retryAt; }

    public GamificationFailureEntity(long userId,long source,String error,Instant retryAt) {
        this.userId=userId; submissionId=source; errorCode=error; this.retryAt=retryAt; attempts=1;
    }
    public void retry(String code,Instant at) { errorCode=code; retryAt=at; attempts=Math.addExact(attempts,1); }
}
