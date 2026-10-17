package vn.edu.ptit.english.repository;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import vn.edu.ptit.english.entity.LessonEntity;
public interface LessonJpaRepository extends JpaRepository<LessonEntity,Long>,JpaSpecificationExecutor<LessonEntity> {
    Optional<LessonEntity> findBySeedKey(String key);
    List<LessonEntity> findByUnitIdOrderBySortOrderAscIdAsc(long unitId);
}
