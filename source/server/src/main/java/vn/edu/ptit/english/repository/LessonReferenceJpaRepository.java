package vn.edu.ptit.english.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.ptit.english.entity.LessonReferenceEntity;

public interface LessonReferenceJpaRepository extends JpaRepository<LessonReferenceEntity, Long> {
    boolean existsByUnitId(long unitId);
}
