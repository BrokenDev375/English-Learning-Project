package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SourceType;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="classrooms")
public class ClassroomEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="teacher_id",nullable=false) private UserEntity teacher;
    @Column(nullable=false,length=100) private String name;
    @Column(nullable=false,length=1000) private String description;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="join_code",nullable=false,length=8,columnDefinition="char(8)") private String joinCode;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="create_request_id",nullable=false,length=36,columnDefinition="char(36)") private String createRequestId;
    @CreationTimestamp(source=SourceType.DB)
    @Column(name="created_at",nullable=false,updatable=false,columnDefinition="timestamp(6)") private Instant createdAt;
    protected ClassroomEntity() {}
    public ClassroomEntity(UserEntity teacher,String name,String description,String code,String request) {
        this.teacher=teacher;this.name=name;this.description=description;this.joinCode=code;this.createRequestId=request;
    }
    public Long getId(){return id;}
    public UserEntity getTeacher(){return teacher;}
    public String getName(){return name;}
    public String getDescription(){return description;}
    public String getJoinCode(){return joinCode;}
    public String getCreateRequestId(){return createRequestId;}
    public Instant getCreatedAt(){return createdAt;}
    public void edit(String name,String description){this.name=name;this.description=description;}
}
