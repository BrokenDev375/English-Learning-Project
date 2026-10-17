package vn.edu.ptit.english.entity;
import jakarta.persistence.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.dto.AuthoringData.Question;
@Entity @Table(name="question_versions")
public class QuestionVersionEntity extends AuthoredVersion {
    @Column(name="question_id",nullable=false) private long questionId;
    @Column(nullable=false,length=24) private String type;
    @Column(name="topic_id",nullable=false) private long topicId;
    @Column(name="objective_id",nullable=false) private long objectiveId;
    @Column(nullable=false,length=8) private String difficulty;
    @Column(nullable=false,length=2000) private String prompt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="content_json",nullable=false) private JsonNode content;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="answer_json",nullable=false) private JsonNode answerKey;
    @Column(nullable=false,columnDefinition="text") private String explanation;
    @Column(columnDefinition="text") private String hint;
    @Column(name="material_version_id") private Long materialVersionId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="source_json",nullable=false) private MaterialData.Source source;
    protected QuestionVersionEntity(){}
    public QuestionVersionEntity(long root,int number,Question data){questionId=root;versionNo=number;assign(data);}
    private void assign(Question d){type=d.type();topicId=d.topicId();objectiveId=d.objectiveId();difficulty=d.difficulty();prompt=d.prompt();content=d.content().deepCopy();answerKey=d.answerKey().deepCopy();explanation=d.explanation();hint=d.hint();materialVersionId=d.materialVersionId();source=d.source();}
    public Question data(){return new Question(type,topicId,objectiveId,difficulty,prompt,content.deepCopy(),answerKey.deepCopy(),explanation,hint,materialVersionId,source);}
    public boolean update(Question d){editable();if(data().equals(d))return false;assign(d);revision++;return true;}
    public long getQuestionId(){return questionId;}
    public Map<String,Object> view(){return stamp(data().fields(),"questionId",questionId);}
}
