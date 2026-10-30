package vn.edu.ptit.english.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.ptit.english.dto.ContestData.Settings;

@Entity @Table(name="contests")
public class ContestEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="owner_id",nullable=false) private long ownerId;
    @Column(nullable=false) private long revision;
    @Column(nullable=false,length=16) private String state="DRAFT";
    @Column(nullable=false,length=160) private String title;
    @Column(nullable=false,length=4000) private String description;
    @Column(name="registration_closes_at",nullable=false) private Instant registrationClosesAt;
    @Column(name="starts_at",nullable=false) private Instant startsAt;
    @Column(name="ends_at",nullable=false) private Instant endsAt;
    @Column(name="penalty_minutes",nullable=false) private int penaltyMinutes;
    @Column(name="shuffle_questions",nullable=false) private boolean shuffleQuestions;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="proctor_ids_json",nullable=false) private JsonNode proctorIds;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="draft_json",nullable=false) private JsonNode draft=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="paper_json") private JsonNode paper;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="results_json") private JsonNode results;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="closed_at") private Instant closedAt;
    @Column(name="finalized_at") private Instant finalizedAt;
    @Column(name="source_paper_id") private Long sourcePaperId;
    @Column(name="source_paper_revision") private Long sourcePaperRevision;
    @Column(name="source_paper_title",length=160) private String sourcePaperTitle;
    protected ContestEntity() {}
    public ContestEntity(long actor,Settings input,JsonNode proctors,Instant at){ownerId=actor;createdAt=at;configure(input,proctors);revision=0;}
    public void configure(Settings input,JsonNode proctors){title=input.title();description=input.description();registrationClosesAt=input.registrationClosesAt();startsAt=input.startsAt();endsAt=input.endsAt();penaltyMinutes=input.penaltyMinutes();shuffleQuestions=input.shuffleQuestions();proctorIds=proctors.deepCopy();revision++;}
    public void selectPaper(ContestPaperEntity value){sourcePaperId=value==null?null:value.id();sourcePaperRevision=value==null?null:value.revision();sourcePaperTitle=value==null?null:value.title();draft=value==null?com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode():value.questions();}
    public void paper(JsonNode value){draft=value.deepCopy();revision++;}
    public void publish(){paper=draft.deepCopy();state="PUBLISHED";revision++;}
    public void start(){state="RUNNING";revision++;}
    public void close(Instant at){state="CLOSED";closedAt=at;revision++;}
    public void finalizeResults(JsonNode value,Instant at){results=value.deepCopy();finalizedAt=at;state="FINALIZED";revision++;}
    public void archive(){state="ARCHIVED";revision++;}
    public void cancel(Instant at){state="CANCELLED";closedAt=at;revision++;}
    public long id(){return id;} public long ownerId(){return ownerId;} public long revision(){return revision;}
    public String state(){return state;} public String title(){return title;} public String description(){return description;}
    public Instant registrationClosesAt(){return registrationClosesAt;} public Instant startsAt(){return startsAt;} public Instant endsAt(){return endsAt;}
    public int penaltyMinutes(){return penaltyMinutes;} public boolean shuffleQuestions(){return shuffleQuestions;}
    public JsonNode proctorIds(){return proctorIds.deepCopy();} public JsonNode draft(){return draft.deepCopy();}
    public JsonNode paper(){return paper==null?null:paper.deepCopy();} public JsonNode results(){return results==null?null:results.deepCopy();}
    public Instant createdAt(){return createdAt;} public Instant closedAt(){return closedAt;} public Instant finalizedAt(){return finalizedAt;}
    public Long sourcePaperId(){return sourcePaperId;} public Long sourcePaperRevision(){return sourcePaperRevision;} public String sourcePaperTitle(){return sourcePaperTitle;}
}
