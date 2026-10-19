package vn.edu.ptit.english.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.edu.ptit.english.entity.ObjectiveEntity;

public interface ObjectiveJpaRepository extends JpaRepository<ObjectiveEntity, Long>, JpaSpecificationExecutor<ObjectiveEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from ObjectiveEntity item where item.id = :id")
    Optional<ObjectiveEntity> lockById(@Param("id") long id);
    Optional<ObjectiveEntity> findByCode(String code);
}
