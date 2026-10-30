package vn.edu.ptit.english.repository;

import java.util.*;
import vn.edu.ptit.english.entity.*;

public interface ContestRepository {
    Optional<ContestEntity> contest(long id,boolean lock);
    Optional<ContestParticipantEntity> participant(long contestId,long userId,boolean lock);
    Optional<ContestParticipantEntity> participantById(long id,boolean lock);
    Optional<ContestSubmissionEntity> submission(long id);
    List<ContestEntity> contests(String role,long user,int offset,int limit);
    long contestCount(String role,long user);
    List<Long> activeIds();
    List<ContestParticipantEntity> participants(long contestId);
    List<ContestEventEntity> events(long contestId,Long participantId,int offset,int limit);
    long eventCount(long contestId,Long participantId);
    ContestEntity save(ContestEntity value);
    ContestParticipantEntity save(ContestParticipantEntity value);
    ContestSubmissionEntity save(ContestSubmissionEntity value);
    void save(ContestEventEntity value);
}
