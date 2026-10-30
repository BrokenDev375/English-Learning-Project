package vn.edu.ptit.english.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="contest_participants")
public class ContestParticipantEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="contest_id",nullable=false) private long contestId;
    @Column(name="user_id",nullable=false) private long userId;
    @Column(name="display_name",nullable=false,length=120) private String displayName;
    @Column(nullable=false,length=16) private String status="REQUESTED";
    @Column(nullable=false) private long revision;
    @Column(name="control_revision",nullable=false) private long controlRevision;
    @Column(name="requested_at",nullable=false) private Instant requestedAt;
    @Column(name="decided_at") private Instant decidedAt;
    @Column(name="checked_in_at") private Instant checkedInAt;
    @Column(name="writer_id",length=36) private String writerId;
    @Column(name="lease_until") private Instant leaseUntil;
    @Column(name="last_seen_at") private Instant lastSeenAt;
    @Column(name="last_saved_at") private Instant lastSavedAt;
    @Column(name="effective_deadline") private Instant effectiveDeadline;
    @Column(nullable=false) private boolean online;
    @Column(name="tab_hidden",nullable=false) private boolean tabHidden;
    @Column(name="tab_leaves",nullable=false) private int tabLeaves;
    @Column(name="flag_seen_count",nullable=false) private int flagSeenCount;
    @Column(name="penalty_applied",nullable=false) private boolean penaltyApplied;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="answers_json",nullable=false) private JsonNode answers=JsonNodeFactory.instance.objectNode();
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="display_json") private JsonNode display;
    @Column(name="submission_id") private Long submissionId;
    protected ContestParticipantEntity() {}
    public ContestParticipantEntity(long contest,long user,String name,Instant at){contestId=contest;userId=user;displayName=name;requestedAt=at;}
    public void approve(Instant at,Instant deadline){status="APPROVED";decidedAt=at;effectiveDeadline=deadline;revision++;controlRevision++;}
    public void reject(Instant at){status="REJECTED";decidedAt=at;revision++;controlRevision++;}
    public void revoke(Instant at){status="REVOKED";decidedAt=at;online=false;revision++;controlRevision++;}
    public void checkIn(String writer,JsonNode layout,Instant at){if(checkedInAt==null){checkedInAt=at;display=layout.deepCopy();revision++;controlRevision++;}writerId=writer;heartbeat(at);}
    public void claim(String writer,Instant at){writerId=writer;heartbeat(at);}
    public void heartbeat(Instant at){lastSeenAt=at;leaseUntil=at.plusSeconds(45);online=true;}
    public void offline(){online=false;}
    public void saveAnswers(JsonNode value,Instant at){answers=value.deepCopy();lastSavedAt=at;revision++;}
    public void visibility(boolean hidden,int minutes,Instant at){if(tabHidden!=hidden){tabHidden=hidden;if(hidden){tabLeaves++;controlRevision++;if(tabLeaves==2&&!penaltyApplied){penaltyApplied=true;var reduced=effectiveDeadline.minusSeconds(minutes*60L);effectiveDeadline=reduced.isBefore(at)?at:reduced;}}revision++;}}
    public void acknowledge(){flagSeenCount=tabLeaves;revision++;controlRevision++;}
    public void submitted(long submission){submissionId=submission;online=false;revision++;controlRevision++;}
    public long id(){return id;} public long contestId(){return contestId;} public long userId(){return userId;} public String displayName(){return displayName;}
    public String status(){return status;} public long revision(){return revision;} public long controlRevision(){return controlRevision;} public Instant requestedAt(){return requestedAt;} public Instant decidedAt(){return decidedAt;}
    public Instant checkedInAt(){return checkedInAt;} public String writerId(){return writerId;} public Instant leaseUntil(){return leaseUntil;}
    public Instant lastSeenAt(){return lastSeenAt;} public Instant lastSavedAt(){return lastSavedAt;} public Instant effectiveDeadline(){return effectiveDeadline;}
    public boolean online(){return online;} public boolean tabHidden(){return tabHidden;} public int tabLeaves(){return tabLeaves;}
    public boolean flagged(){return tabLeaves>=3&&flagSeenCount<tabLeaves;} public boolean penaltyApplied(){return penaltyApplied;}
    public JsonNode answers(){return answers.deepCopy();} public JsonNode display(){return display==null?null:display.deepCopy();} public Long submissionId(){return submissionId;}
}
