package vn.edu.ptit.english.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.ptit.english.entity.WritingGradeEntity;
public interface WritingGradeJpaRepository extends JpaRepository<WritingGradeEntity,Long> {
    List<WritingGradeEntity> findBySubmissionIdIn(Collection<Long> ids);
}
