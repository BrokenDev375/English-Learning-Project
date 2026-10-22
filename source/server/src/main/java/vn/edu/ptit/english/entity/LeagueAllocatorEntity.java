package vn.edu.ptit.english.entity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import vn.edu.ptit.english.dto.LeagueViews.Tier;

@Entity @Table(name="league_group_allocators") @IdClass(LeagueAllocatorEntity.Key.class)
public class LeagueAllocatorEntity implements GamificationRow {
    @Id @Column(name="season_id") private Long seasonId;
    @Id @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR) @Column(length=16) private Tier tier;
    protected LeagueAllocatorEntity() {}
    public LeagueAllocatorEntity(long season,Tier tier){seasonId=season;this.tier=tier;}
    public static class Key implements Serializable {
        public Long seasonId;public Tier tier;
        public Key(){} public Key(long season,Tier tier){seasonId=season;this.tier=tier;}
        @Override public boolean equals(Object o){return o instanceof Key k&&Objects.equals(seasonId,k.seasonId)&&tier==k.tier;}
        @Override public int hashCode(){return Objects.hash(seasonId,tier);}
    }
}
