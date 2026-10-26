package vn.edu.ptit.english.repository;

import java.util.*;
import org.springframework.data.domain.Page;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.AttemptEnums.*;

/** All writes must run in the caller's transaction after AccountGuard's actor lock. */
public interface AttemptRepository {
    Optional<AttemptEntity> attempt(long id,long userId,boolean lock);
    List<AttemptEntity> active(long userId,Mode mode);
    Page<AttemptEntity> attempts(long userId,Mode mode,State state,int page,int size);
    List<AttemptQuestionEntity> items(long attemptId);
    Optional<AttemptQuestionEntity> item(long itemId,long attemptId);
    List<AttemptAnswerEntity> answers(Collection<Long> itemIds);
    List<AnswerTrialEntity> trials(long itemId);
    Optional<SubmissionEntity> submission(long attemptId);
    Optional<Long> lessonSlot(long lessonVersionId,long questionVersionId);
    boolean assetReadable(long userId,long assetId);
    List<QuestionExposureEntity> exposures(long userId,Collection<String> origins);
    Optional<QuestionExposureEntity> exposure(QuestionExposureKey id,boolean lock);
    Optional<LessonProgressEntity> progress(LessonProgressKey id,boolean lock);
    Page<LessonProgressEntity> progress(long userId,Long unitId,int page,int size);
    Page<LessonEntity> progressLessons(long userId,Long unitId,int page,int size);
    List<LessonEntity> publishedRoundLessons(Collection<Long> unitIds);
    List<LessonProgressEntity> roundProgress(long userId,Collection<Long> unitIds);
    AttemptEntity save(AttemptEntity value);
    AttemptQuestionEntity save(AttemptQuestionEntity value);
    AttemptAnswerEntity save(AttemptAnswerEntity value);
    AnswerTrialEntity save(AnswerTrialEntity value);
    SubmissionEntity save(SubmissionEntity value);
    QuestionExposureEntity save(QuestionExposureEntity value);
    LessonProgressEntity save(LessonProgressEntity value);
}
