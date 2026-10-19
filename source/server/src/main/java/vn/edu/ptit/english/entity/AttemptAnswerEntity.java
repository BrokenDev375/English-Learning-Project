package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.ptit.english.dto.AttemptData.Answer;

@Entity @Table(name="attempt_answers")
public class AttemptAnswerEntity {
    @Id @Column(name="attempt_question_id") private Long attemptQuestionId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="response_json") private Answer response;
    @UpdateTimestamp @Column(name="updated_at",nullable=false,columnDefinition="datetime(6)") private Instant updatedAt;
    protected AttemptAnswerEntity() {}
    public AttemptAnswerEntity(long item,Answer response){attemptQuestionId=item; this.response=response;}
    public Long getAttemptQuestionId(){return attemptQuestionId;} public Answer getResponse(){return response;}
    public Instant getUpdatedAt(){return updatedAt;} public void update(Answer value){response=value;}
}
