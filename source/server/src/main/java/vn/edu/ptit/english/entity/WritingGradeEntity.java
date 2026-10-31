package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="unit_writing_grades")
public class WritingGradeEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="submission_id",nullable=false) private long submissionId;
    @Column(name="graded_by",nullable=false) private long gradedBy;
    @Column(name="graded_at",nullable=false,columnDefinition="datetime(6)") private Instant gradedAt;
    @Column(name="earned_points",nullable=false) private int earnedPoints;
    @Column(name="total_points",nullable=false) private int totalPoints=10;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="criteria_json",nullable=false,columnDefinition="json") private JsonNode marks;
    @Column(name="comment_text",nullable=false,length=4000) private String comment;
    @Column(name="allow_revision",nullable=false) private boolean allowRevision;
    protected WritingGradeEntity() {}
    public WritingGradeEntity(long submissionId,long teacher,int score,JsonNode marks,String comment,boolean allowRevision){this.submissionId=submissionId;gradedBy=teacher;gradedAt=Instant.now();earnedPoints=score;this.marks=marks.deepCopy();this.comment=comment;this.allowRevision=allowRevision;}
    public long getId(){return id;} public long getSubmissionId(){return submissionId;} public int getEarnedPoints(){return earnedPoints;} public int getTotalPoints(){return totalPoints;}
    public JsonNode getMarks(){return marks.deepCopy();} public String getComment(){return comment;} public boolean isAllowRevision(){return allowRevision;} public Instant getGradedAt(){return gradedAt;}
}
