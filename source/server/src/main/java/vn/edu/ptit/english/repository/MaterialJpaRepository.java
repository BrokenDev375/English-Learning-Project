package vn.edu.ptit.english.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.edu.ptit.english.entity.MaterialEntity;

public interface MaterialJpaRepository extends JpaRepository<MaterialEntity,Long>,JpaSpecificationExecutor<MaterialEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select m from MaterialEntity m where m.id=:id")
    Optional<MaterialEntity> lockById(@Param("id") long id);
    Optional<MaterialEntity> findBySeedKey(String seedKey);
}
