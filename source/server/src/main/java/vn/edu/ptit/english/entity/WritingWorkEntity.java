package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="unit_writing_works")
public class WritingWorkEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="unit_id",nullable=false) private long unitId;
    @Column(name="student_id",nullable=false) private long studentId;
    @Column(name="version_id",nullable=false) private long versionId;
    @Column(nullable=false) private long revision;
    @Column(nullable=false,length=16) private String state="DRAFT";
    @Column(name="draft_text",nullable=false,columnDefinition="mediumtext") private String text="";
    @Column(name="latest_submission_id") private Long latestSubmissionId;
    @Column(name="updated_at",nullable=false,columnDefinition="datetime(6)") private Instant updatedAt;
    protected WritingWorkEntity() {}
    public WritingWorkEntity(long unitId,long studentId,long versionId){this.unitId=unitId;this.studentId=studentId;this.versionId=versionId;updatedAt=Instant.now();}
    public long getId(){return id;} public long getUnitId(){return unitId;} public long getStudentId(){return studentId;} public long getVersionId(){return versionId;}
    public long getRevision(){return revision;} public String getState(){return state;} public String getText(){return text;}
    public Long getLatestSubmissionId(){return latestSubmissionId;} public Instant getUpdatedAt(){return updatedAt;}
    private void changed(){revision++;updatedAt=Instant.now();}
    public void draft(String value){if(!text.equals(value)){text=value;changed();}}
    public void submit(long id,String value){latestSubmissionId=id;text=value;state="SUBMITTED";changed();}
    public void grade(boolean allowRevision){state=allowRevision?"RETURNED":"GRADED";changed();}
}
