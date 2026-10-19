package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.ptit.english.dto.AttemptData.Answer;
import vn.edu.ptit.english.enums.AttemptEnums.*;

@Entity @Table(name="answer_trials") @Immutable
public class AnswerTrialEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="attempt_question_id",nullable=false) private long attemptQuestionId;
    @Column(name="trial_no",nullable=false) private int trialNo;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable=false,length=20) private TrialKind kind;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable=false,length=12) private TrialSource source;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="response_json") private Answer response;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable=false,length=16) private Outcome outcome;
    @Column(name="response_complete",nullable=false) private boolean responseComplete;
    @Column(name="support_used",nullable=false) private boolean supportUsed;
    @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false,columnDefinition="datetime(6)") private Instant createdAt;
    protected AnswerTrialEntity() {}
    public AnswerTrialEntity(long item,int trialNo,TrialKind kind,TrialSource source,Answer response,Outcome outcome,boolean complete,boolean support) {
        attemptQuestionId=item; this.trialNo=trialNo; this.kind=kind; this.source=source; this.response=response;
        this.outcome=outcome; responseComplete=complete; supportUsed=support;
    }
    public Long getId(){return id;} public long getAttemptQuestionId(){return attemptQuestionId;} public int getTrialNo(){return trialNo;}
    public TrialKind getKind(){return kind;} public TrialSource getSource(){return source;} public Answer getResponse(){return response;}
    public Outcome getOutcome(){return outcome;} public boolean isResponseComplete(){return responseComplete;}
    public boolean isSupportUsed(){return supportUsed;} public Instant getCreatedAt(){return createdAt;}
}
