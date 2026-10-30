package vn.edu.ptit.english.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="contest_events")
public class ContestEventEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="contest_id",nullable=false,updatable=false) private long contestId;
    @Column(name="participant_id",updatable=false) private Long participantId;
    @Column(name="actor_id",updatable=false) private Long actorId;
    @Column(nullable=false,updatable=false,length=40) private String type;
    @Column(nullable=false,updatable=false,length=2000) private String reason;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="details_json",nullable=false,updatable=false) private JsonNode details;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected ContestEventEntity() {}
    public ContestEventEntity(long contest,Long participant,Long actor,String type,String reason,JsonNode details,Instant at){contestId=contest;participantId=participant;actorId=actor;this.type=type;this.reason=reason;this.details=details.deepCopy();createdAt=at;}
    public Map<String,Object> view(){var m=new LinkedHashMap<String,Object>();m.put("id",id);m.put("participantId",participantId);m.put("actorId",actorId);m.put("type",type);m.put("reason",reason);m.put("details",details.deepCopy());m.put("createdAt",createdAt);return m;}
}
