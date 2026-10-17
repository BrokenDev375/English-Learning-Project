package vn.edu.ptit.english.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.edu.ptit.english.entity.UnitEntity;

public interface UnitJpaRepository extends JpaRepository<UnitEntity, Long>, JpaSpecificationExecutor<UnitEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from UnitEntity item where item.id = :id")
    Optional<UnitEntity> lockById(@Param("id") long id);
    Optional<UnitEntity> findBySeedKey(String seedKey);
}
