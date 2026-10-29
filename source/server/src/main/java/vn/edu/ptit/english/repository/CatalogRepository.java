package vn.edu.ptit.english.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import vn.edu.ptit.english.entity.*;

public interface CatalogRepository {
    Page<TopicEntity> topics(Long ownerId, String query, int page, int size);
    Page<ObjectiveEntity> objectives(Long ownerId, Long topicId, String query, int page, int size);
    Page<UnitEntity> units(Long ownerId, Long topicId, String query, int page, int size, boolean assigned);
    Optional<TopicEntity> topic(long id, boolean lock);
    Optional<ObjectiveEntity> objective(long id, boolean lock);
    Optional<UnitEntity> unit(long id, boolean lock);
    TopicEntity save(TopicEntity value);
    ObjectiveEntity save(ObjectiveEntity value);
    UnitEntity save(UnitEntity value);
    int maxRoundNumber();
    boolean roundNumberUsed(int number, long exceptId);
    boolean hasLessons(long unitId);
    Optional<RequestReceiptEntity> receipt(long actorId, String requestId);
    void save(RequestReceiptEntity value);
}
