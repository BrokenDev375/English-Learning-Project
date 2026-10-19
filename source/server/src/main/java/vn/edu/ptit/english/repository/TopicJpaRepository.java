package vn.edu.ptit.english.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.edu.ptit.english.entity.TopicEntity;

public interface TopicJpaRepository extends JpaRepository<TopicEntity, Long>, JpaSpecificationExecutor<TopicEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from TopicEntity item where item.id = :id")
    Optional<TopicEntity> lockById(@Param("id") long id);
    Optional<TopicEntity> findByCode(String code);
}
