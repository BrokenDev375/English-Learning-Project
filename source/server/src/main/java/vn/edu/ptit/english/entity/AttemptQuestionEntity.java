package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.ptit.english.dto.AttemptData.Display;
import vn.edu.ptit.english.enums.AttemptEnums.PracticeRole;

@Entity @Table(name="attempt_questions")
public class AttemptQuestionEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="attempt_id",nullable=false,updatable=false) private long attemptId;
    @Column(name="question_version_id",nullable=false,updatable=false) private long questionVersionId;
    @Column(name="lesson_question_id",updatable=false) private Long lessonQuestionId;
    @Column(name="origin_key",nullable=false,updatable=false,columnDefinition="char(36)") private String originKey;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name="practice_role",nullable=false,length=20,updatable=false) private PracticeRole practiceRole;
    @Column(nullable=false,updatable=false) private int position;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="display_json",nullable=false,updatable=false) private Display display;
    @Column(name="was_seen_before",nullable=false,updatable=false) private boolean wasSeenBefore;
    @Column(name="seen_count_before",nullable=false,updatable=false) private int seenCountBefore;
    @Column(name="hint_opened_at",columnDefinition="datetime(6)") private Instant hintOpenedAt;
    @Column(name="feedback_opened_at",columnDefinition="datetime(6)") private Instant feedbackOpenedAt;
    protected AttemptQuestionEntity() {}
    public AttemptQuestionEntity(long attemptId,long versionId,Long slot,String origin,PracticeRole role,int position,Display display,int seenCountBefore) {
        this.attemptId=attemptId; questionVersionId=versionId; lessonQuestionId=slot; originKey=origin;
        practiceRole=role; this.position=position; this.display=display; this.seenCountBefore=seenCountBefore; wasSeenBefore=seenCountBefore>0;
    }
    public Long getId(){return id;} public long getAttemptId(){return attemptId;} public long getQuestionVersionId(){return questionVersionId;}
    public Long getLessonQuestionId(){return lessonQuestionId;} public String getOriginKey(){return originKey;}
    public PracticeRole getPracticeRole(){return practiceRole;} public int getPosition(){return position;}
    public Display getDisplay(){return display;} public boolean isWasSeenBefore(){return wasSeenBefore;}
    public int getSeenCountBefore(){return seenCountBefore;} public Instant getHintOpenedAt(){return hintOpenedAt;}
    public Instant getFeedbackOpenedAt(){return feedbackOpenedAt;}
    public boolean openHint(Instant now){if(hintOpenedAt!=null)return false; hintOpenedAt=now; return true;}
    public void openFeedback(Instant now){if(feedbackOpenedAt==null)feedbackOpenedAt=now;}
}
