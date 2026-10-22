package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.*;
import java.io.Serializable;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.ptit.english.dto.GamificationViews.Activity;


@Entity @Table(name="xp_ledger")
public class XpLedgerEntity implements GamificationRow {
    @Id @Column(name="claim_id",nullable=false) private Long claimId;
    @Column(name="user_id",nullable=false) private long userId;
    @Column(name="submission_id",nullable=false) private long submissionId;
    @Column(name="policy_version",nullable=false,length=32) private String policyVersion;
    @Column(name="requested_xp",nullable=false) private int requestedXp;
    @Column(name="awarded_xp",nullable=false) private int awardedXp;
    @Column(name="activity_date",nullable=false) private LocalDate activityDate;
    @Column(name="submitted_at",nullable=false,columnDefinition="datetime(6)") private Instant submittedAt;
    @Column(name="processed_at",nullable=false,columnDefinition="datetime(6)") private Instant processedAt;
    @Column(name="season_id",nullable=true) private Long seasonId;
    @Column(name="league_counted_xp",nullable=false) private int leagueCountedXp;
    @Column(name="league_reason",nullable=false,length=32) private String leagueReason;
    protected XpLedgerEntity() {}
    public Long getClaimId() { return claimId; }
    public long getUserId() { return userId; }
    public long getSubmissionId() { return submissionId; }
    public String getPolicyVersion() { return policyVersion; }
    public int getRequestedXp() { return requestedXp; }
    public int getAwardedXp() { return awardedXp; }
    public LocalDate getActivityDate() { return activityDate; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getProcessedAt() { return processedAt; }
    public Long getSeasonId() { return seasonId; }
    public int getLeagueCountedXp() { return leagueCountedXp; }
    public String getLeagueReason() { return leagueReason; }
    public void league(Long season,int counted,String reason) { seasonId=season;leagueCountedXp=counted;leagueReason=reason; }

    public XpLedgerEntity(GamificationClaimEntity claim,String policy,int requested,int awarded,Instant sourceAt,Instant at) {
        claimId=claim.getId(); userId=claim.getUserId(); submissionId=claim.getSubmissionId(); policyVersion=policy;
        requestedXp=requested; awardedXp=awarded; activityDate=claim.getActivityDate(); submittedAt=sourceAt; processedAt=at;
        leagueReason=awarded>0?"NOT_JOINED":"NO_AWARDED_XP";
    }
}
