package vn.edu.ptit.english.entity;

import jakarta.persistence.*;

@Entity @Table(name="question_versions") @org.hibernate.annotations.Immutable
public class QuestionVersionReferenceEntity {
    @Id private Long id;
    @Column(name="material_version_id") private Long materialVersionId;
    protected QuestionVersionReferenceEntity(){}
}
