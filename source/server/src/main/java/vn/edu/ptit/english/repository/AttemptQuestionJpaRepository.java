package vn.edu.ptit.english.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.ptit.english.entity.AttemptQuestionEntity;
public interface AttemptQuestionJpaRepository extends JpaRepository<AttemptQuestionEntity,Long> {
    List<AttemptQuestionEntity> findByAttemptIdOrderByPositionAsc(long attemptId);
    Optional<AttemptQuestionEntity> findByIdAndAttemptId(long id,long attemptId);
}
