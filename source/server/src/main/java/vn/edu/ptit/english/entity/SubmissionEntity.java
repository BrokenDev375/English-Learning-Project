package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.ptit.english.dto.AttemptViews.Result;

@Entity @Table(name="submissions")
public class SubmissionEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="attempt_id",nullable=false,updatable=false) private long attemptId;
    @Column(name="facts_schema_version",nullable=false,updatable=false) private int factsSchemaVersion;
    @Column(name="submitted_revision",nullable=false,updatable=false) private long submittedRevision;
    @Column(name="total_questions",nullable=false,updatable=false) private int totalQuestions;
    @Column(name="answered_count",nullable=false,updatable=false) private int answeredCount;
    @Column(name="correct_count",nullable=false,updatable=false) private int correctCount;
    @Column(name="unanswered_count",nullable=false,updatable=false) private int unansweredCount;
    @Column(name="independent_fresh_answered_count",nullable=false,updatable=false) private int independentFreshAnsweredCount;
    @Column(name="independent_fresh_correct_count",nullable=false,updatable=false) private int independentFreshCorrectCount;
    @Column(name="score_percent",nullable=false,updatable=false,precision=5,scale=2) private BigDecimal scorePercent;
    @Column(name="lesson_completed",nullable=false,updatable=false) private boolean lessonCompleted;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="result_json",nullable=false) private Result result;
    @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false,columnDefinition="datetime(6)") private Instant createdAt;
    protected SubmissionEntity() {}
    /** Supply submissionId=0; repository binds the generated ID before committing. */
    public SubmissionEntity(Result value) {
        if(value.submissionId()!=0||value.factsSchemaVersion()!=1)throw new IllegalArgumentException("New result must use schema 1 and an unassigned submission ID");
        result=value; attemptId=value.attemptId(); factsSchemaVersion=value.factsSchemaVersion(); submittedRevision=value.submittedRevision();
        totalQuestions=value.totalQuestions(); answeredCount=value.answeredCount(); correctCount=value.correctCount(); unansweredCount=value.unansweredCount();
        independentFreshAnsweredCount=value.independentFreshAnsweredCount(); independentFreshCorrectCount=value.independentFreshCorrectCount();
        scorePercent=value.scorePercent(); lessonCompleted=value.lessonCompleted();
    }
    public void bindGeneratedId() {
        if(id==null||result.submissionId()!=0)throw new IllegalStateException("Submission result can only be finalized once after insert");
        result=result.withSubmissionId(id);
    }
    public Long getId(){return id;} public long getAttemptId(){return attemptId;} public Result getResult(){return result;}
    public BigDecimal getScorePercent(){return scorePercent;} public Instant getCreatedAt(){return createdAt;}
}
