package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
import vn.edu.ptit.english.dto.MaterialData;

@Entity @jakarta.persistence.Table(name="material_versions")
public class MaterialVersionEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="material_id",nullable=false) private long materialId;
    @Column(name="version_no",nullable=false) private int versionNo;
    @Column(nullable=false) private long revision;
    @Column(nullable=false,length=16) private String state="DRAFT";
    @Column(name="schema_version",nullable=false) private int schemaVersion=1;
    @Column(nullable=false,length=16) private String kind;
    @Column(nullable=false,length=160) private String title;
    @Column(name="reading_text",columnDefinition="mediumtext") private String readingText;
    @Column(name="asset_id") private Long assetId;
    @Column(columnDefinition="mediumtext") private String transcript;
    @Column(name="alt_text",length=500) private String altText;
    @Column(length=1000) private String caption;
    @Column(name="start_seconds",precision=10,scale=3) private BigDecimal startSeconds;
    @Column(name="end_seconds",precision=10,scale=3) private BigDecimal endSeconds;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="source_json",nullable=false) private MaterialData.Source source;
    @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false,columnDefinition="datetime(6)") private Instant createdAt;
    @UpdateTimestamp @Column(name="updated_at",nullable=false,columnDefinition="datetime(6)") private Instant updatedAt;
    @Column(name="published_at",columnDefinition="datetime(6)") private Instant publishedAt;
    protected MaterialVersionEntity(){}
    public MaterialVersionEntity(long materialId,int version,MaterialData data){this.materialId=materialId;versionNo=version;assign(data);}
    private void assign(MaterialData data){kind=data.kind();title=data.title();readingText=data.readingText();assetId=data.assetId();transcript=data.transcript();altText=data.altText();caption=data.caption();startSeconds=data.startSeconds();endSeconds=data.endSeconds();source=data.source();}
    public boolean update(MaterialData data){if(!state.equals("DRAFT"))throw new IllegalStateException("Published material is immutable");if(data().equals(data))return false;assign(data);revision++;return true;}
    public void publish(){if(!state.equals("DRAFT"))throw new IllegalStateException("Published material is immutable");state="PUBLISHED";publishedAt=Instant.now();revision++;}
    public MaterialData data(){return new MaterialData(kind,title,readingText,assetId,transcript,altText,caption,startSeconds,endSeconds,source);}
    public Map<String,Object> view(){var result=data().fields();result.put("id",id);result.put("materialId",materialId);result.put("versionNo",versionNo);result.put("revision",revision);result.put("state",state);result.put("schemaVersion",schemaVersion);return result;}
    public Long getId(){return id;}
    public long getMaterialId(){return materialId;}
    public int getVersionNo(){return versionNo;}
    public long getRevision(){return revision;}
    public String getState(){return state;}
}
