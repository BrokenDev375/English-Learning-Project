package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.*;
import java.io.Serializable;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.ptit.english.dto.GamificationViews.Activity;


@Entity @Table(name="gamification_daily_stats")
@IdClass(GamificationDayEntity.Key.class)
public class GamificationDayEntity implements GamificationRow {
    @Id @Column(name="user_id",nullable=false) private Long userId;
    @Id @Column(name="activity_date",nullable=false) private LocalDate activityDate;
    @Column(name="awarded_xp",nullable=false) private int awardedXp;
    @Column(name="cap_xp",nullable=false) private int capXp;
    @Column(name="qualified_submissions",nullable=false) private int qualifiedSubmissions;
    protected GamificationDayEntity() {}
    public Long getUserId() { return userId; }
    public LocalDate getActivityDate() { return activityDate; }
    public int getAwardedXp() { return awardedXp; }
    public int getCapXp() { return capXp; }
    public int getQualifiedSubmissions() { return qualifiedSubmissions; }

    public GamificationDayEntity(long userId,LocalDate date,int cap) { this.userId=userId; activityDate=date; capXp=cap; }
    public int award(int requested) { int paid=Math.max(0,Math.min(requested,capXp-awardedXp)); awardedXp+=paid; return paid; }
    public void qualify() { qualifiedSubmissions=Math.addExact(qualifiedSubmissions,1); }

    public static class Key implements Serializable {
        private Long userId;
        private LocalDate activityDate;
        public Key() {}
        public Key(Long userId, LocalDate activityDate) { this.userId=userId; this.activityDate=activityDate; }
        @Override public boolean equals(Object other) { return other instanceof Key k && Objects.equals(userId,k.userId) && Objects.equals(activityDate,k.activityDate); }
        @Override public int hashCode() { return Objects.hash(userId, activityDate); }
    }
}
