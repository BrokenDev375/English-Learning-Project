package vn.edu.ptit.english.repository;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import vn.edu.ptit.english.entity.LessonEntity;
public interface LessonJpaRepository extends JpaRepository<LessonEntity,Long>,JpaSpecificationExecutor<LessonEntity> {
    Optional<LessonEntity> findBySeedKey(String key);
    List<LessonEntity> findByUnitIdOrderBySortOrderAscIdAsc(long unitId);
    @Query("""
        select l from LessonEntity l where (:unitId is null or l.unitId=:unitId)
        and ((l.published is not null and l.retiredAt is null)
          or exists (select p from LessonProgressEntity p where p.id.userId=:userId and p.id.lessonId=l.id))
        order by l.unitId,l.sortOrder,l.id
        """)
    org.springframework.data.domain.Page<LessonEntity> progressLessons(long userId,Long unitId,org.springframework.data.domain.Pageable page);
}
