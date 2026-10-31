package vn.edu.ptit.english.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.ptit.english.entity.WritingWorkEntity;
public interface WritingWorkJpaRepository extends JpaRepository<WritingWorkEntity,Long> {
    Optional<WritingWorkEntity> findByStudentIdAndUnitId(long studentId,long unitId);
    List<WritingWorkEntity> findByStudentIdAndUnitIdIn(long studentId,Collection<Long> unitIds);
}
