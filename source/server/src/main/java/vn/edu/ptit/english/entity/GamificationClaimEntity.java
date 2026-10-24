package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.*;
import java.io.Serializable;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.ptit.english.dto.GamificationViews.Activity;


@Entity @Table(name="gamification_reward_claims")
public class GamificationClaimEntity implements GamificationRow {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id",nullable=false) private long userId;
    @Column(name="submission_id",nullable=false) private long submissionId;
    @Column(name="reward_type",nullable=false,length=20) private String rewardType;
    @Column(name="source_key",nullable=false,length=128) private String sourceKey;
    @Column(name="objective_id",nullable=true) private Long objectiveId;
    @Column(name="activity_date",nullable=false) private LocalDate activityDate;
    protected GamificationClaimEntity() {}
    public Long getId() { return id; }
    public long getUserId() { return userId; }
    public long getSubmissionId() { return submissionId; }
    public String getRewardType() { return rewardType; }
    public String getSourceKey() { return sourceKey; }
    public Long getObjectiveId() { return objectiveId; }
    public LocalDate getActivityDate() { return activityDate; }

    public GamificationClaimEntity(long userId,long submissionId,String type,String key,Long objective,LocalDate date) {
        this.userId=userId; this.submissionId=submissionId; rewardType=type; sourceKey=key; objectiveId=objective; activityDate=date;
    }
}
