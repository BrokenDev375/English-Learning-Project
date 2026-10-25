package vn.edu.ptit.english.entity;
import jakarta.persistence.*;
import java.time.Instant;
import vn.edu.ptit.english.dto.LeagueViews.Tier;

@Entity @Table(name="league_groups")
public class LeagueGroupEntity implements GamificationRow {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="season_id",nullable=false) private long seasonId;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR) @Column(nullable=false,length=16) private Tier tier;
    @Column(name="group_no",nullable=false) private int groupNo;
    @Column(name="member_count",nullable=false) private int memberCount;
    @Column(nullable=false) private long revision;
    @Column(name="finalized_at",columnDefinition="datetime(6)") private Instant finalizedAt;
    protected LeagueGroupEntity() {}
    public LeagueGroupEntity(long season,Tier tier,int number){seasonId=season;this.tier=tier;groupNo=number;}
    public Long getId(){return id;} public long getSeasonId(){return seasonId;} public Tier getTier(){return tier;}
    public int getGroupNo(){return groupNo;} public int getMemberCount(){return memberCount;}
    public long getRevision(){return revision;} public Instant getFinalizedAt(){return finalizedAt;}
    public void addMember(){if(memberCount>=25)throw new IllegalStateException("Full league group");memberCount++;changed();}
    public void changed(){revision=Math.addExact(revision,1);}
    public void finish(Instant at){if(finalizedAt==null){finalizedAt=at;changed();}}
}
