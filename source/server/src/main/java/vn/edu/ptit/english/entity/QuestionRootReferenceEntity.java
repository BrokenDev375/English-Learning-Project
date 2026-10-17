package vn.edu.ptit.english.entity;

import jakarta.persistence.*;

@Entity @Table(name="questions") @org.hibernate.annotations.Immutable
public class QuestionRootReferenceEntity {
    @Id private Long id;
    @Column(name="published_version_id") private Long publishedVersionId;
    @Column(name="retired_at",columnDefinition="datetime(6)") private java.time.Instant retiredAt;
    protected QuestionRootReferenceEntity(){}
}
