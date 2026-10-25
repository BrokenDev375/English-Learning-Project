package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.*;
import java.io.Serializable;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.ptit.english.dto.GamificationViews.Activity;


@Entity @Table(name="gamification_outbox") @org.hibernate.annotations.DynamicUpdate
public class GamificationOutboxEntity implements GamificationRow {
    @Id @Column(name="event_id",nullable=false,length=36,columnDefinition="char(36)") private String eventId;
    @Column(name="user_id",nullable=false) private long userId;
    @Column(name="revision",nullable=false) private long revision;
    @Column(name="event_type",nullable=false,length=32) private String eventType;
    @Column(name="created_at",nullable=false,columnDefinition="datetime(6)") private Instant createdAt;
    @Column(name="delivered_at",nullable=true,columnDefinition="datetime(6)") private Instant deliveredAt;
    protected GamificationOutboxEntity() {}
    public String getEventId() { return eventId; }
    public long getUserId() { return userId; }
    public long getRevision() { return revision; }
    public String getEventType() { return eventType; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeliveredAt() { return deliveredAt; }

    public GamificationOutboxEntity(long userId,long revision,Instant now) {
        eventId=java.util.UUID.randomUUID().toString(); this.userId=userId; this.revision=revision;
        eventType="GAMIFICATION_CHANGED"; createdAt=now;
    }
    public void delivered(Instant now) { deliveredAt=now; }
}
