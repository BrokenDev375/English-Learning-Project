package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.*;
import java.io.Serializable;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.ptit.english.dto.GamificationViews.Activity;


@Entity @Table(name="gamification_profiles")
public class GamificationProfileEntity implements GamificationRow {
    @Id @Column(name="user_id",nullable=false) private Long userId;
    @Column(name="total_xp",nullable=false) private long totalXp;
    @Column(name="revision",nullable=false) private long revision;
    protected GamificationProfileEntity() {}
    public Long getUserId() { return userId; }
    public long getTotalXp() { return totalXp; }
    public long getRevision() { return revision; }

    public GamificationProfileEntity(long userId) { this.userId=userId; }
    public void record(int awarded) {
        long next=Math.addExact(totalXp,awarded);
        if(next>9007199254740991L) throw new IllegalStateException("XP exceeds safe integer range");
        totalXp=next; revision=Math.addExact(revision,1);
    }
}
