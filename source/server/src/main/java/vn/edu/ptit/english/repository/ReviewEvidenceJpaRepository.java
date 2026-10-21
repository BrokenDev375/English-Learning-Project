package vn.edu.ptit.english.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.ptit.english.entity.*;

public interface ReviewEvidenceJpaRepository extends JpaRepository<ReviewEvidenceEntity, ReviewEvidenceKey> {
    List<ReviewEvidenceEntity> findByIdUserId(long userId);
}
