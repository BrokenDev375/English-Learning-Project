package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="unit_writing_tasks")
public class WritingTaskEntity {
    @Id @Column(name="unit_id") private long unitId;
    @Column(nullable=false) private long revision;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="draft_json",columnDefinition="json") private JsonNode draft;
    @Column(name="published_version_id") private Long publishedVersionId;
    protected WritingTaskEntity() {}
    public WritingTaskEntity(long unitId){this.unitId=unitId;}
    public long getUnitId(){return unitId;} public long getRevision(){return revision;}
    public JsonNode getDraft(){return draft==null?null:draft.deepCopy();} public Long getPublishedVersionId(){return publishedVersionId;}
    public void draft(JsonNode value){draft=value.deepCopy();revision++;}
    public void publish(long id){publishedVersionId=id;revision++;}
    public void retire(){publishedVersionId=null;revision++;}
}
