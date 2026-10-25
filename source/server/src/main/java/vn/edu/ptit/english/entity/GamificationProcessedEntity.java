package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.*;
import java.io.Serializable;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.ptit.english.dto.GamificationViews.Activity;


@Entity @Table(name="gamification_processed_submissions")
public class GamificationProcessedEntity implements GamificationRow {
    @Id @Column(name="submission_id",nullable=false) private Long submissionId;
    @Column(name="user_id",nullable=false) private long userId;
    @Column(name="policy_version",nullable=false,length=32) private String policyVersion;
    @Column(name="disposition",nullable=false,length=24) private String disposition;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="result_json",nullable=false) private Activity result;
    @Column(name="processed_at",nullable=false,columnDefinition="datetime(6)") private Instant processedAt;
    protected GamificationProcessedEntity() {}
    public Long getSubmissionId() { return submissionId; }
    public long getUserId() { return userId; }
    public String getPolicyVersion() { return policyVersion; }
    public String getDisposition() { return disposition; }
    public Activity getResult() { return result; }
    public Instant getProcessedAt() { return processedAt; }

    public GamificationProcessedEntity(long userId,Activity result) {
        submissionId=result.submissionId(); this.userId=userId; policyVersion=result.policyVersion(); disposition=result.disposition();
        this.result=result; processedAt=result.processedAt();
    }
}
