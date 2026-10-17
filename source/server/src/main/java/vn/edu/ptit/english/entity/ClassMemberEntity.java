package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;
@Entity @Table(name="class_members")
public class ClassMemberEntity {
    @EmbeddedId private ClassMemberKey id;
    @MapsId("classroomId") @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="classroom_id") private ClassroomEntity classroom;
    @MapsId("studentId") @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="student_id") private UserEntity student;
    @CreationTimestamp(source=SourceType.DB)
    @Column(name="joined_at",nullable=false,updatable=false,columnDefinition="timestamp(6)") private Instant joinedAt;
    protected ClassMemberEntity(){}
    public ClassMemberEntity(ClassroomEntity room,UserEntity student) {
        this.id=new ClassMemberKey(room.getId(),student.getId());this.classroom=room;this.student=student;
    }
    public ClassMemberKey getId(){return id;}
    public UserEntity getStudent(){return student;}
    public ClassroomEntity getClassroom(){return classroom;}
    public Instant getJoinedAt(){return joinedAt;}
}
