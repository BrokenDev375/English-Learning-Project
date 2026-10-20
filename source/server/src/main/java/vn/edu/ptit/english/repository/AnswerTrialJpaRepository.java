package vn.edu.ptit.english.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.ptit.english.entity.AnswerTrialEntity;
public interface AnswerTrialJpaRepository extends JpaRepository<AnswerTrialEntity,Long> {
    List<AnswerTrialEntity> findByAttemptQuestionIdOrderByTrialNoAsc(long item);
}
