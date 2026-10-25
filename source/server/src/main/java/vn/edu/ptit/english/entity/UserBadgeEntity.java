package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.*;
import java.io.Serializable;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.ptit.english.dto.GamificationViews.Activity;


@Entity @Table(name="user_badges")
@IdClass(UserBadgeEntity.Key.class)
public class UserBadgeEntity implements GamificationRow {
    @Id @Column(name="user_id",nullable=false) private Long userId;
    @Id @Column(name="badge_code",nullable=false,length=32) private String badgeCode;
    @Column(name="earned_at",nullable=false,columnDefinition="datetime(6)") private Instant earnedAt;
    @Column(name="evidence_source_at",nullable=false,columnDefinition="datetime(6)") private Instant evidenceSourceAt;
    @Column(name="source_submission_id",nullable=false) private long sourceSubmissionId;
    protected UserBadgeEntity() {}
    public Long getUserId() { return userId; }
    public String getBadgeCode() { return badgeCode; }
    public Instant getEarnedAt() { return earnedAt; }
    public Instant getEvidenceSourceAt() { return evidenceSourceAt; }
    public long getSourceSubmissionId() { return sourceSubmissionId; }

    public UserBadgeEntity(long userId,String code,Instant at,Instant evidenceAt,long source) {
        this.userId=userId; badgeCode=code; earnedAt=at; evidenceSourceAt=evidenceAt; sourceSubmissionId=source;
    }
    public static class Key implements Serializable {
        private Long userId;
        private String badgeCode;
        public Key() {}
        public Key(Long userId, String badgeCode) { this.userId=userId; this.badgeCode=badgeCode; }
        @Override public boolean equals(Object other) { return other instanceof Key k && Objects.equals(userId,k.userId) && Objects.equals(badgeCode,k.badgeCode); }
        @Override public int hashCode() { return Objects.hash(userId, badgeCode); }
    }
}
