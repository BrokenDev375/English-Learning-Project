package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="unit_writing_submissions")
public class WritingSubmissionEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="work_id",nullable=false) private long workId;
    @Column(name="unit_id",nullable=false) private long unitId;
    @Column(name="student_id",nullable=false) private long studentId;
    @Column(name="version_id",nullable=false) private long versionId;
    @Column(name="body_text",nullable=false,columnDefinition="mediumtext") private String text;
    @Column(name="submitted_at",nullable=false,columnDefinition="datetime(6)") private Instant submittedAt;
    protected WritingSubmissionEntity() {}
    public WritingSubmissionEntity(WritingWorkEntity work,String text){workId=work.getId();unitId=work.getUnitId();studentId=work.getStudentId();versionId=work.getVersionId();this.text=text;submittedAt=Instant.now();}
    public long getId(){return id;} public long getWorkId(){return workId;} public long getUnitId(){return unitId;} public long getStudentId(){return studentId;}
    public long getVersionId(){return versionId;} public String getText(){return text;} public Instant getSubmittedAt(){return submittedAt;}
}
