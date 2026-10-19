package vn.edu.ptit.english.repository;

import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.edu.ptit.english.entity.AttemptEntity;
import vn.edu.ptit.english.enums.AttemptEnums.*;

public interface AttemptJpaRepository extends JpaRepository<AttemptEntity,Long>, JpaSpecificationExecutor<AttemptEntity> {
    Optional<AttemptEntity> findByIdAndUserId(long id,long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AttemptEntity a where a.id=:id and a.userId=:userId")
    Optional<AttemptEntity> lockOwned(@Param("id") long id,@Param("userId") long userId);
    List<AttemptEntity> findByUserIdAndModeAndStateOrderByIdAsc(long userId,Mode mode,State state);
}
