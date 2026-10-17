package vn.edu.ptit.english.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.ptit.english.entity.RequestReceiptEntity;

public interface RequestReceiptJpaRepository extends JpaRepository<RequestReceiptEntity, Long> {
    Optional<RequestReceiptEntity> findByActorIdAndRequestId(long actorId, String requestId);
}
