package vn.edu.ptit.english.repository;

import java.time.*;
import java.util.*;
import vn.edu.ptit.english.entity.*;

public interface GamificationRepository {
    <T extends GamificationRow> T save(T entity);
    Optional<GamificationProfileEntity> profile(long userId);
    Optional<GamificationDayEntity> day(long userId,LocalDate date);
    List<GamificationDayEntity> days(long userId);
    Optional<GamificationClaimEntity> claim(long userId,String type,String sourceKey);
    boolean claimedDay(long userId,long objectiveId,LocalDate date);
    List<UserBadgeEntity> badges(long userId);
    Optional<GamificationProcessedEntity> processed(long sourceId);
    Optional<GamificationFailureEntity> failure(long sourceId);
    void clearFailure(long sourceId);
    Optional<SubmissionEntity> source(long userId,long sourceId);
    List<SubmissionEntity> pending(long userId,Instant now,int limit);
    long pendingCount(long userId);
    long deferredCount(long userId,Instant now);
    List<Long> pendingUsers(long afterUserId,Instant now,int limit);
    List<GamificationProcessedEntity> activities(long userId,Long submissionId,int page,int size);
    long activityCount(long userId,Long submissionId);
    List<GamificationOutboxEntity> pendingEvents(int limit);
}
