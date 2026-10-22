package vn.edu.ptit.english.entity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.ptit.english.dto.LeagueViews.*;

@Entity @Table(name="league_members") @org.hibernate.annotations.DynamicUpdate
public class LeagueMemberEntity implements GamificationRow {
    @Id @Column(name="member_key",length=36,columnDefinition="char(36)") private String memberKey;
    @Column(name="user_id",nullable=false,updatable=false) private long userId;
    @Column(name="season_id",nullable=false,updatable=false) private long seasonId;
    @Column(name="group_id",nullable=false,updatable=false) private long groupId;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR) @Column(nullable=false,length=16) private Tier tier;
    @Column(name="joined_at",nullable=false,updatable=false,columnDefinition="datetime(6)") private Instant joinedAt;
    @Column(name="weekly_xp",nullable=false) private long weeklyXp;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="result_json",columnDefinition="json") private Result result;
    protected LeagueMemberEntity() {}
    public LeagueMemberEntity(long user,LeagueGroupEntity group,Instant at){memberKey=UUID.randomUUID().toString();userId=user;
        groupId=group.getId();seasonId=group.getSeasonId();tier=group.getTier();joinedAt=at;}
    public String getMemberKey(){return memberKey;} public long getUserId(){return userId;} public long getSeasonId(){return seasonId;}
    public long getGroupId(){return groupId;} public Tier getTier(){return tier;} public Instant getJoinedAt(){return joinedAt;}
    public long getWeeklyXp(){return weeklyXp;} public Result getResult(){return result;}
    public void award(int xp){if(result!=null)throw new IllegalStateException("Frozen member");weeklyXp=Math.addExact(weeklyXp,xp);
        if(weeklyXp>9007199254740991L)throw new ArithmeticException("Unsafe XP");}
    public void finish(Result result){if(this.result==null)this.result=result;}
}
