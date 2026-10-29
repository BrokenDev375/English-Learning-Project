package vn.edu.ptit.english.entity;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="content_review_requests")
public class ContentReviewEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=16) private String kind;
    @Column(name="question_id") private Long questionId;
    @Column(name="lesson_id") private Long lessonId;
    @Column(name="version_id",nullable=false) private long versionId;
    @Column(name="version_revision",nullable=false) private long versionRevision;
    @Column(name="preview_token",nullable=false,length=64,columnDefinition="char(64)") private String previewToken;
    @Column(nullable=false,length=200) private String title;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="snapshot_json",nullable=false,columnDefinition="json") private JsonNode snapshot;
    @Column(nullable=false,length=16) private String state="PENDING";
    @Column(nullable=false) private long revision;
    @Column(name="submitted_by",nullable=false) private long submittedBy;
    @Column(name="submitted_at",nullable=false,columnDefinition="datetime(6)") private Instant submittedAt;
    @Column(name="reviewed_by") private Long reviewedBy;
    @Column(name="reviewed_at",columnDefinition="datetime(6)") private Instant reviewedAt;
    @Column(nullable=false,length=2000) private String note="";
    protected ContentReviewEntity() {}
    public ContentReviewEntity(String kind,long resourceId,long versionId,long versionRevision,String previewToken,String title,JsonNode snapshot,long submittedBy) {
        this.kind=kind;this.questionId=kind.equals("QUESTION")?resourceId:null;this.lessonId=kind.equals("LESSON")?resourceId:null;
        this.versionId=versionId;this.versionRevision=versionRevision;this.previewToken=previewToken;this.title=title;
        this.snapshot=snapshot.deepCopy();this.submittedBy=submittedBy;this.submittedAt=Instant.now();
    }
    public long getId(){return id;} public String getKind(){return kind;} public long getResourceId(){return questionId!=null?questionId:lessonId;}
    public long getVersionId(){return versionId;} public long getVersionRevision(){return versionRevision;}
    public String getPreviewToken(){return previewToken;} public String getTitle(){return title;} public JsonNode getSnapshot(){return snapshot.deepCopy();}
    public String getState(){return state;} public long getRevision(){return revision;} public long getSubmittedBy(){return submittedBy;}
    public Instant getSubmittedAt(){return submittedAt;} public Long getReviewedBy(){return reviewedBy;} public Instant getReviewedAt(){return reviewedAt;} public String getNote(){return note;}
    public void decide(String state,long actor,String note){this.state=state;this.reviewedBy=actor;this.reviewedAt=Instant.now();this.note=note;revision++;}
}
