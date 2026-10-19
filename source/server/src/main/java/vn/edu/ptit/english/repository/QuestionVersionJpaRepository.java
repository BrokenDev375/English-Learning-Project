package vn.edu.ptit.english.repository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.edu.ptit.english.entity.QuestionVersionEntity;
public interface QuestionVersionJpaRepository extends JpaRepository<QuestionVersionEntity,Long> {
    @Query("select count(v) from LessonVersionEntity v join v.questions s where s.questionVersionId=:id")
    long countReferences(@Param("id") long version);
    @Query("select count(l) from LessonEntity l,LessonVersionEntity v join v.questions s,QuestionVersionEntity q where l.published.id=v.id and l.retiredAt is null and s.questionVersionId=q.id and q.questionId=:id")
    long countPublishedUsage(@Param("id") long root);
}
