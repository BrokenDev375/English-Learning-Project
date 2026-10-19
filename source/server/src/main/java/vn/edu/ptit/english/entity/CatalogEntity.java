package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@MappedSuperclass
public abstract class CatalogEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;
    @Column(name = "owner_id", nullable = false) protected long ownerId;
    @Column(nullable = false) protected long revision;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    protected Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
    protected Instant updatedAt;
    public Long getId() { return id; }
    public long getOwnerId() { return ownerId; }
    public long getRevision() { return revision; }
}
