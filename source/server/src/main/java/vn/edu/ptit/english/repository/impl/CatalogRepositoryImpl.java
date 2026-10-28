package vn.edu.ptit.english.repository.impl;

import java.util.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.repository.*;

@Repository
public class CatalogRepositoryImpl implements CatalogRepository {
    private final TopicJpaRepository topics;
    private final ObjectiveJpaRepository objectives;
    private final UnitJpaRepository units;
    private final LessonReferenceJpaRepository lessons;
    private final RequestReceiptJpaRepository receipts;
    public CatalogRepositoryImpl(TopicJpaRepository topics, ObjectiveJpaRepository objectives,
        UnitJpaRepository units, LessonReferenceJpaRepository lessons, RequestReceiptJpaRepository receipts) {
        this.topics = topics; this.objectives = objectives; this.units = units; this.lessons = lessons; this.receipts = receipts;
    }
    private <T> Specification<T> filter(Long ownerId, Long topicId, String query, String name, boolean hasCode) {
        return (root, criteria, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (ownerId != null) predicates.add(cb.equal(root.get("ownerId"), ownerId));
            if (topicId != null) predicates.add(cb.equal(root.get("topicId"), topicId));
            if (!query.isBlank()) {
                String escaped = query.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
                String pattern = "%" + escaped + "%";
                Predicate text = cb.like(cb.lower(root.get(name)), pattern, '!');
                if (hasCode) text = cb.or(text, cb.like(cb.lower(root.get("code")), pattern, '!'));
                predicates.add(text);
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
    public Page<TopicEntity> topics(Long ownerId, String query, int page, int size) {
        return topics.findAll(filter(ownerId, null, query, "name", true), PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id")));
    }
    public Page<ObjectiveEntity> objectives(Long ownerId, Long topicId, String query, int page, int size) {
        return objectives.findAll(filter(ownerId, topicId, query, "name", true), PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id")));
    }
    public Page<UnitEntity> units(Long ownerId, Long topicId, String query, int page, int size, boolean assigned) {
        return units.findAll(this.<UnitEntity>filter(null, topicId, query, "title", false).and((r,q,cb)->ownerId==null?cb.conjunction():cb.equal(r.get(assigned?"assignedTeacherId":"ownerId"),ownerId)), PageRequest.of(page,size,Sort.by("roundNumber","id")));
    }
    public Optional<TopicEntity> topic(long id, boolean lock) { return lock ? topics.lockById(id) : topics.findById(id); }
    public Optional<ObjectiveEntity> objective(long id, boolean lock) { return lock ? objectives.lockById(id) : objectives.findById(id); }
    public Optional<UnitEntity> unit(long id, boolean lock) { return lock ? units.lockById(id) : units.findById(id); }
    public TopicEntity save(TopicEntity value) { return topics.saveAndFlush(value); }
    public ObjectiveEntity save(ObjectiveEntity value) { return objectives.saveAndFlush(value); }
    public UnitEntity save(UnitEntity value) { return units.saveAndFlush(value); }
    public int maxRoundNumber() { return units.maxRoundNumber(); }
    public boolean roundNumberUsed(int number, long exceptId) { return units.existsByRoundNumberAndIdNot(number,exceptId); }
    public boolean hasLessons(long unitId) { return lessons.existsByUnitId(unitId); }
    public Optional<RequestReceiptEntity> receipt(long actorId, String requestId) { return receipts.findByActorIdAndRequestId(actorId,requestId); }
    public void save(RequestReceiptEntity value) { receipts.saveAndFlush(value); }
}
