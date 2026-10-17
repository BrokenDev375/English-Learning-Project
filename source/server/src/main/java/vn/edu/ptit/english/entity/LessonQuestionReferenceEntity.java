package vn.edu.ptit.english.entity;

import jakarta.persistence.*;

@Entity @Table(name="lesson_questions") @org.hibernate.annotations.Immutable
public class LessonQuestionReferenceEntity {
    @Id private Long id;
    @Column(name="lesson_version_id") private long lessonVersionId;
    @Column(name="question_version_id") private long questionVersionId;
    protected LessonQuestionReferenceEntity(){}
}
