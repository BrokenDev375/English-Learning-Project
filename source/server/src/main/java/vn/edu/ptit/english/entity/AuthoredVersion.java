package vn.edu.ptit.english.entity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
@MappedSuperclass
public abstract class AuthoredVersion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) protected Long id;
    @Column(name="version_no",nullable=false) protected int versionNo;
    @Column(nullable=false) protected long revision;
    @Column(nullable=false,length=16) protected String state="DRAFT";
    @Column(name="schema_version",nullable=false) protected int schemaVersion=1;
    @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false,columnDefinition="datetime(6)") private Instant createdAt;
    @UpdateTimestamp @Column(name="updated_at",nullable=false,columnDefinition="datetime(6)") private Instant updatedAt;
    @Column(name="published_at",columnDefinition="datetime(6)") private Instant publishedAt;
    protected void editable(){if(!state.equals("DRAFT"))throw new IllegalStateException("Published version is immutable");}
    public void publish(){editable();state="PUBLISHED";publishedAt=Instant.now();revision++;}
    protected Map<String,Object> stamp(Map<String,Object> value,String parent,long parentId){value.put("id",id);value.put(parent,parentId);value.put("versionNo",versionNo);value.put("revision",revision);value.put("state",state);value.put("schemaVersion",schemaVersion);return value;}
    public Long getId(){return id;} public long getRevision(){return revision;} public int getVersionNo(){return versionNo;} public String getState(){return state;}
}
