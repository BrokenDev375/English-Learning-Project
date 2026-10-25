package vn.edu.ptit.english.entity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="league_outbox") @org.hibernate.annotations.DynamicUpdate
public class LeagueOutboxEntity implements GamificationRow {
    @Id @Column(name="event_id",length=36,columnDefinition="char(36)") private String eventId;
    @Column(name="group_id") private Long groupId;
    @Column(name="user_id") private Long userId;
    @Column(nullable=false) private long revision;
    @Column(name="event_type",nullable=false,length=32) private String eventType;
    @Column(name="created_at",nullable=false,columnDefinition="datetime(6)") private Instant createdAt;
    @Column(name="delivered_at",columnDefinition="datetime(6)") private Instant deliveredAt;
    protected LeagueOutboxEntity() {}
    public LeagueOutboxEntity(Long group,Long user,long revision,String type,Instant at){eventId=UUID.randomUUID().toString();
        groupId=group;userId=user;this.revision=revision;eventType=type;createdAt=at;}
    public String getEventId(){return eventId;} public Long getGroupId(){return groupId;} public Long getUserId(){return userId;}
    public long getRevision(){return revision;} public String getEventType(){return eventType;} public Instant getCreatedAt(){return createdAt;}
    public void delivered(Instant at){deliveredAt=at;}
}
