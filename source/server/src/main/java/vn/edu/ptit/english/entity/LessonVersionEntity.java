package vn.edu.ptit.english.entity;
import jakarta.persistence.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.ptit.english.dto.AuthoringData.*;
@Entity @Table(name="lesson_versions")
public class LessonVersionEntity extends AuthoredVersion {
    @Column(name="lesson_id",nullable=false) private long lessonId;
    @Column(nullable=false,length=160) private String title;
    @Column(nullable=false,length=2000) private String description;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="instructions_json",nullable=false) private JsonNode instructions;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="activities_json") private JsonNode activities;
    @ElementCollection @CollectionTable(name="lesson_objectives",joinColumns=@JoinColumn(name="lesson_version_id")) @Column(name="objective_id",nullable=false) private Set<Long> objectiveIds=new HashSet<>();
    @ElementCollection @CollectionTable(name="lesson_questions",joinColumns=@JoinColumn(name="lesson_version_id")) private List<LessonSlotEntity> questions=new ArrayList<>();
    protected LessonVersionEntity(){}
    public LessonVersionEntity(long root,int number,Lesson data){lessonId=root;versionNo=number;assign(data);}
    private void assign(Lesson d){title=d.title();description=d.description();instructions=d.instructions().deepCopy();activities=d.activities();objectiveIds.clear();objectiveIds.addAll(d.objectiveIds());questions.clear();d.questions().forEach(s -> questions.add(new LessonSlotEntity(s)));}
    public Lesson data(){var slots=questions.stream().map(LessonSlotEntity::view).sorted(Comparator.comparing((Slot s)->List.of("GUIDED","INDEPENDENT","REVIEW_VARIANT").indexOf(s.practiceRole())).thenComparingInt(Slot::position)).toList();return new Lesson(title,description,instructions.deepCopy(),objectiveIds.stream().sorted().toList(),slots,activities);}
    public boolean update(Lesson d){editable();var sorted=new Lesson(d.title(),d.description(),d.instructions(),d.objectiveIds().stream().sorted().toList(),d.questions().stream().sorted(Comparator.comparing((Slot s)->List.of("GUIDED","INDEPENDENT","REVIEW_VARIANT").indexOf(s.practiceRole())).thenComparingInt(Slot::position)).toList(),d.activities());if(data().equals(sorted))return false;assign(sorted);revision++;return true;}
    public long getLessonId(){return lessonId;}
    public Map<String,Object> view(){return stamp(data().fields(),"lessonId",lessonId);}
}
