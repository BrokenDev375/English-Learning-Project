package vn.edu.ptit.english.repository;

import java.util.*;
import vn.edu.ptit.english.entity.*;

/** Writes run under the actor lock, in the service transaction. */
public interface ReviewRepository {
    record WrongLearnQuestion(String originKey, long objectiveId, long submissionId) {}
    record CorrectReviewQuestion(String originKey, long submissionId) {}
    List<WrongLearnQuestion> wrongLearnQuestions(long userId);
    List<CorrectReviewQuestion> correctReviewQuestions(long userId, long afterSubmissionId);
    List<SubmissionEntity> pending(long userId, int limit);
    long pendingCount(long userId);
    List<ReviewProgressEntity> progress(long userId);
    Optional<ReviewProgressEntity> progress(ReviewObjectiveKey id);
    List<ReviewEvidenceEntity> evidence(long userId);
    Optional<ReviewEvidenceEntity> evidence(ReviewEvidenceKey id);
    Map<Long, ObjectiveEntity> objectives(Collection<Long> ids);
    ReviewProgressEntity save(ReviewProgressEntity value);
    ReviewEvidenceEntity save(ReviewEvidenceEntity value);
    void processed(long userId, long submissionId);
}
