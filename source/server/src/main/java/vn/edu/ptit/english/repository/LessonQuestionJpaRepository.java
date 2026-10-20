package vn.edu.ptit.english.repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.edu.ptit.english.entity.LessonQuestionReferenceEntity;
public interface LessonQuestionJpaRepository extends JpaRepository<LessonQuestionReferenceEntity,Long> {
    @Query("select s.id from LessonQuestionReferenceEntity s where s.lessonVersionId=:lessonVersionId and s.questionVersionId=:questionVersionId")
    Optional<Long> slot(@Param("lessonVersionId") long lessonVersionId,@Param("questionVersionId") long questionVersionId);
}
