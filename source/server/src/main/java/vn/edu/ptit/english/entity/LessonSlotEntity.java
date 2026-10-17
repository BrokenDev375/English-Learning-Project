package vn.edu.ptit.english.entity;
import jakarta.persistence.*;
import vn.edu.ptit.english.dto.AuthoringData.Slot;
@Embeddable
public class LessonSlotEntity {
    @Column(name="question_version_id",nullable=false) private long questionVersionId;
    @Column(name="practice_role",nullable=false,length=20) private String practiceRole;
    @Column(name="sort_order",nullable=false) private int position;
    protected LessonSlotEntity(){}
    public LessonSlotEntity(Slot s){questionVersionId=s.questionVersionId();practiceRole=s.practiceRole();position=s.position();}
    public Slot view(){return new Slot(questionVersionId,practiceRole,position);}
}
