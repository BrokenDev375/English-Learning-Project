package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ClassMemberKey implements Serializable {
    @Column(name="classroom_id") private Long classroomId;
    @Column(name="student_id") private Long studentId;
    protected ClassMemberKey(){}
    public ClassMemberKey(long classroomId,long studentId){this.classroomId=classroomId;this.studentId=studentId;}
    public Long getClassroomId(){return classroomId;}
    public Long getStudentId(){return studentId;}
    @Override public boolean equals(Object other){return other instanceof ClassMemberKey k && Objects.equals(classroomId,k.classroomId) && Objects.equals(studentId,k.studentId);}
    @Override public int hashCode(){return Objects.hash(classroomId,studentId);}
}
