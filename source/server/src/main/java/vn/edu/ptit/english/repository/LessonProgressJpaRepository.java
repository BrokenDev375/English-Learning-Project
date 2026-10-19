package vn.edu.ptit.english.repository;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.edu.ptit.english.entity.*;
public interface LessonProgressJpaRepository extends JpaRepository<LessonProgressEntity,LessonProgressKey> {
    @Query("select p from LessonProgressEntity p join LessonEntity l on l.id=p.id.lessonId where p.id.userId=:userId and (:unitId is null or l.unitId=:unitId) order by l.sortOrder,l.id")
    Page<LessonProgressEntity> pageOwned(@Param("userId") long userId,@Param("unitId") Long unitId,Pageable page);
}
