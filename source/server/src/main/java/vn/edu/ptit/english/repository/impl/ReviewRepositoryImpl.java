package vn.edu.ptit.english.repository.impl;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.repository.*;

@Repository
public class ReviewRepositoryImpl implements ReviewRepository {
    private final EntityManager em;
    private final ReviewProgressJpaRepository progress;
    private final ReviewEvidenceJpaRepository evidence;
    private final ReviewProcessedSubmissionJpaRepository processed;
    private final ObjectiveJpaRepository objectives;
    public ReviewRepositoryImpl(EntityManager em, ReviewProgressJpaRepository progress, ReviewEvidenceJpaRepository evidence,
                                ReviewProcessedSubmissionJpaRepository processed, ObjectiveJpaRepository objectives) {
        this.em = em; this.progress = progress; this.evidence = evidence; this.processed = processed; this.objectives = objectives;
    }
    private static final String PENDING = " from SubmissionEntity s, AttemptEntity a where s.attemptId = a.id and a.userId = :userId"
        + " and not exists (select p.submissionId from ReviewProcessedSubmissionEntity p where p.submissionId = s.id)";
    public List<SubmissionEntity> pending(long userId, int limit) {
        return em.createQuery("select s" + PENDING + " order by s.id", SubmissionEntity.class)
            .setParameter("userId", userId).setMaxResults(limit).getResultList();
    }
    public long pendingCount(long userId) {
        return em.createQuery("select count(s)" + PENDING, Long.class).setParameter("userId", userId).getSingleResult();
    }
    public List<ReviewProgressEntity> progress(long userId) { return progress.findByIdUserId(userId); }
    public Optional<ReviewProgressEntity> progress(ReviewObjectiveKey id) { return progress.findById(id); }
    public List<ReviewEvidenceEntity> evidence(long userId) { return evidence.findByIdUserId(userId); }
    public Optional<ReviewEvidenceEntity> evidence(ReviewEvidenceKey id) { return evidence.findById(id); }
    public Map<Long, ObjectiveEntity> objectives(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return objectives.findAllById(ids).stream().collect(Collectors.toMap(ObjectiveEntity::getId, o -> o));
    }
    public ReviewProgressEntity save(ReviewProgressEntity v) { return progress.saveAndFlush(v); }
    public ReviewEvidenceEntity save(ReviewEvidenceEntity v) { return evidence.saveAndFlush(v); }
    public void processed(long userId, long submissionId) {
        processed.saveAndFlush(new ReviewProcessedSubmissionEntity(submissionId, userId, Instant.now()));
    }
}
