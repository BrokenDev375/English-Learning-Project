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
    @Override public List<WrongLearnQuestion> wrongLearnQuestions(long userId) {
        return em.createQuery("select i.originKey,q.objectiveId,max(s.id) from AnswerTrialEntity t,"
            + " AttemptQuestionEntity i,AttemptEntity a,SubmissionEntity s,QuestionVersionEntity q"
            + " where t.attemptQuestionId=i.id and i.attemptId=a.id and s.attemptId=a.id"
            + " and i.questionVersionId=q.id and a.userId=:userId and a.mode=:mode"
            + " and t.kind=:first and t.outcome=:wrong group by i.originKey,q.objectiveId", Object[].class)
            .setParameter("userId", userId).setParameter("mode", vn.edu.ptit.english.enums.AttemptEnums.Mode.LEARN)
            .setParameter("first", vn.edu.ptit.english.enums.AttemptEnums.TrialKind.FIRST)
            .setParameter("wrong", vn.edu.ptit.english.enums.AttemptEnums.Outcome.INCORRECT)
            .getResultList().stream().map(r -> new WrongLearnQuestion((String) r[0], ((Number) r[1]).longValue(), ((Number) r[2]).longValue())).toList();
    }
    @Override public List<CorrectReviewQuestion> correctReviewQuestions(long userId, long afterSubmissionId) {
        // Read the immutable final outcome, not mutable correction trials or current answer keys.
        return em.createQuery("select s from SubmissionEntity s,AttemptEntity a where s.attemptId=a.id"
            + " and a.userId=:userId and a.mode=:mode and s.id>:after", SubmissionEntity.class)
            .setParameter("userId", userId).setParameter("mode", vn.edu.ptit.english.enums.AttemptEnums.Mode.REVIEW)
            .setParameter("after", afterSubmissionId).getResultList().stream()
            .flatMap(s -> s.getResult().items().stream()
                .filter(i -> i.finalOutcome() == vn.edu.ptit.english.enums.AttemptEnums.Outcome.CORRECT)
                .map(i -> new CorrectReviewQuestion(i.originKey(), s.getId()))).toList();
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
