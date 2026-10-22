package vn.edu.ptit.english.service;

import java.time.Instant;
import java.util.Collection;

/** Called only for newly created REVIEW attempts, inside M03's actor-locked transaction. */
public interface ReviewRewardContextRecorder {
    void capture(long attemptId, long userId, Collection<Long> objectiveIds, Instant selectedAt);
}
