package vn.edu.ptit.english.repository;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.edu.ptit.english.entity.WritingSubmissionEntity;
public interface WritingSubmissionJpaRepository extends JpaRepository<WritingSubmissionEntity,Long> {
    List<WritingSubmissionEntity> findByWorkIdOrderByIdDesc(long workId);
    @Query("select s from WritingSubmissionEntity s where s.unitId=:unit and (:state='ALL' or (:state='PENDING' and not exists (select g.id from WritingGradeEntity g where g.submissionId=s.id)) or (:state='GRADED' and exists (select g.id from WritingGradeEntity g where g.submissionId=s.id)))")
    Page<WritingSubmissionEntity> queue(@Param("unit") long unit,@Param("state") String state,Pageable pageable);
}
