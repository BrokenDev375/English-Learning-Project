package vn.edu.ptit.english.repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.ptit.english.entity.SubmissionEntity;
public interface SubmissionJpaRepository extends JpaRepository<SubmissionEntity,Long> {
    Optional<SubmissionEntity> findByAttemptId(long attemptId);
}
