package vn.edu.ptit.english.entity;
import jakarta.persistence.*;
import java.time.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.ptit.english.dto.LeagueViews.Rules;

@Entity @Table(name="league_seasons")
public class LeagueSeasonEntity implements GamificationRow {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="start_at",nullable=false,columnDefinition="datetime(6)") private Instant startAt;
    @Column(name="end_at",nullable=false,columnDefinition="datetime(6)") private Instant endAt;
    @Column(name="cutoff_at",nullable=false,columnDefinition="datetime(6)") private Instant cutoffAt;
    @Column(nullable=false,length=16) private String status;
    @Column(name="policy_version",nullable=false,length=32) private String policyVersion;
    @Column(nullable=false) private long revision;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="rules_json",nullable=false,columnDefinition="json") private Rules rules;
    protected LeagueSeasonEntity() {}
    public LeagueSeasonEntity(Instant start,Rules rules,String policy) {
        startAt=start;endAt=start.plus(Duration.ofDays(7));cutoffAt=endAt.plusSeconds(120);
        this.rules=rules;policyVersion=policy;status="OPEN";
    }
    public Long getId(){return id;} public Instant getStartAt(){return startAt;} public Instant getEndAt(){return endAt;}
    public Instant getCutoffAt(){return cutoffAt;} public String getStatus(){return status;} public String getPolicyVersion(){return policyVersion;}
    public long getRevision(){return revision;} public Rules getRules(){return rules;}
    public void changed(){revision=Math.addExact(revision,1);}
    public void closing(){if(status.equals("OPEN")){status="CLOSING";changed();}}
    public void finish(){if(!status.equals("FINALIZED")){status="FINALIZED";changed();}}
}
