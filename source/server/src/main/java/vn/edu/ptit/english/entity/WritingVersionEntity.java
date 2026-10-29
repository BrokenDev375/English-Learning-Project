package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="unit_writing_versions")
public class WritingVersionEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="unit_id",nullable=false) private long unitId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="definition_json",nullable=false,columnDefinition="json") private JsonNode definition;
    @Column(name="created_by",nullable=false) private long createdBy;
    @Column(name="created_at",nullable=false,columnDefinition="datetime(6)") private Instant createdAt;
    protected WritingVersionEntity() {}
    public WritingVersionEntity(long unitId,JsonNode definition,long actor){this.unitId=unitId;this.definition=definition.deepCopy();createdBy=actor;createdAt=Instant.now();}
    public long getId(){return id;} public long getUnitId(){return unitId;}
    public JsonNode getDefinition(){return definition.deepCopy();} public Instant getCreatedAt(){return createdAt;}
}
