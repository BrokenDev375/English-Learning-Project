package vn.edu.ptit.english.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="contest_submissions")
public class ContestSubmissionEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="participant_id",nullable=false,updatable=false) private long participantId;
    @Column(name="contest_id",nullable=false,updatable=false) private long contestId;
    @Column(name="user_id",nullable=false,updatable=false) private long userId;
    @Column(nullable=false,updatable=false,length=16) private String source;
    @Column(name="submitted_at",nullable=false,updatable=false) private Instant submittedAt;
    @Column(name="processed_at",nullable=false,updatable=false) private Instant processedAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="answers_json",nullable=false,updatable=false) private JsonNode answers;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="result_json",nullable=false,updatable=false) private JsonNode result;
    protected ContestSubmissionEntity() {}
    public ContestSubmissionEntity(ContestParticipantEntity p,String reason,Instant effective,Instant processed,JsonNode data){participantId=p.id();contestId=p.contestId();userId=p.userId();source=reason;submittedAt=effective;processedAt=processed;answers=p.answers();result=data.deepCopy();}
    public long id(){return id;} public long participantId(){return participantId;} public long userId(){return userId;}
    public String source(){return source;} public Instant submittedAt(){return submittedAt;} public Instant processedAt(){return processedAt;}
    public JsonNode answers(){return answers.deepCopy();} public JsonNode result(){return result.deepCopy();}
}
