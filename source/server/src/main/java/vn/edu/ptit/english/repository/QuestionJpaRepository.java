package vn.edu.ptit.english.repository;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import vn.edu.ptit.english.entity.QuestionEntity;
public interface QuestionJpaRepository extends JpaRepository<QuestionEntity,Long>,JpaSpecificationExecutor<QuestionEntity> {
    Optional<QuestionEntity> findBySeedKey(String key);
    
}
