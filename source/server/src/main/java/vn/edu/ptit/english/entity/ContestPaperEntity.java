package vn.edu.ptit.english.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="contest_papers")
public class ContestPaperEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="owner_id",nullable=false) private long ownerId;
    @Column(nullable=false) private long revision;
    @Column(nullable=false,length=16) private String state="DRAFT";
    @Column(nullable=false,length=160) private String title;
    @Column(nullable=false,length=4000) private String description;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="questions_json",nullable=false) private JsonNode questions;
    @Column(name="review_note",nullable=false,length=2000) private String reviewNote="";
    @Column(name="reviewed_by") private Long reviewedBy;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Column(name="submitted_at") private Instant submittedAt;
    @Column(name="reviewed_at") private Instant reviewedAt;
    @Column(name="legacy_contest_id") private Long legacyContestId;
    protected ContestPaperEntity() {}
    public ContestPaperEntity(long owner,String name,String description,JsonNode questions,Instant now){ownerId=owner;title=name;this.description=description;this.questions=questions.deepCopy();createdAt=now;updatedAt=now;}
    public void edit(String name,String description,JsonNode value,Instant now){title=name;this.description=description;questions=value.deepCopy();updatedAt=now;revision++;}
    public void submit(Instant now){state="PENDING";submittedAt=now;updatedAt=now;reviewNote="";revision++;}
    public void review(boolean approved,String note,long actor,Instant now){state=approved?"READY":"DRAFT";reviewNote=note;reviewedBy=actor;reviewedAt=now;updatedAt=now;revision++;}
    public void archive(Instant now){state="ARCHIVED";updatedAt=now;revision++;}
    public long id(){return id;} public long ownerId(){return ownerId;} public long revision(){return revision;} public String state(){return state;}
    public String title(){return title;} public String description(){return description;} public JsonNode questions(){return questions.deepCopy();}
    public String reviewNote(){return reviewNote;} public Long reviewedBy(){return reviewedBy;} public Instant reviewedAt(){return reviewedAt;}
    public Instant createdAt(){return createdAt;} public Instant updatedAt(){return updatedAt;} public Instant submittedAt(){return submittedAt;}
}
