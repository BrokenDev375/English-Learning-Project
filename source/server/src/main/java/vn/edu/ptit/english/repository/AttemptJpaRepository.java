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
    @Query("select (count(i)>0) from AttemptEntity a, AttemptQuestionEntity i, QuestionVersionEntity q, MaterialVersionEntity m where a.userId=:userId and i.attemptId=a.id and q.id=i.questionVersionId and m.id=q.materialVersionId and m.assetId=:assetId")
    boolean assetReadable(@Param("userId") long userId,@Param("assetId") long assetId);
}
