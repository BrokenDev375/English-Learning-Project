package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import vn.edu.ptit.english.dto.AssetView;

@Entity @Table(name="assets")
public class AssetEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="owner_id",nullable=false) private long ownerId;
    @Column(name="storage_key",nullable=false,length=200) private String storageKey;
    @Column(name="original_name",nullable=false,length=200) private String originalName;
    @Column(name="mime_type",nullable=false,length=64) private String mimeType;
    @Column(name="size_bytes",nullable=false) private long sizeBytes;
    @Column(nullable=false,columnDefinition="char(64)") private String sha256;
    @Column(nullable=false,length=16) private String state="READY";
    @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false,columnDefinition="datetime(6)") private Instant createdAt;
    protected AssetEntity() {}
    public AssetEntity(long ownerId,String key,String name,String mime,long bytes,String sha) {
        this.ownerId=ownerId;storageKey=key;originalName=name;mimeType=mime;sizeBytes=bytes;sha256=sha;
    }
    public Long getId(){return id;}
    public long getOwnerId(){return ownerId;}
    public String getStorageKey(){return storageKey;}
    public String getMimeType(){return mimeType;}
    public String getState(){return state;}
    public AssetView view(){return new AssetView(id,ownerId,originalName,mimeType,sizeBytes,sha256,"/api/assets/"+id);}
}
