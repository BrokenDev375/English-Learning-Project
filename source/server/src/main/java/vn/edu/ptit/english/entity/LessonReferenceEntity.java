package vn.edu.ptit.english.entity;

import jakarta.persistence.*;

/** Read-only root mapping for catalog constraints; lesson authoring is implemented in the next step. */
@Entity @Table(name = "lessons")
public class LessonReferenceEntity {
    @Id private Long id;
    @Column(name = "unit_id", nullable = false) private long unitId;
    @Column(name = "published_version_id") private Long publishedVersionId;
    @Column(name = "retired_at",columnDefinition = "datetime(6)") private java.time.Instant retiredAt;
    protected LessonReferenceEntity() {}
}
