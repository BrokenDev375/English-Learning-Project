package vn.edu.ptit.english.repository.impl;

import jakarta.persistence.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.AttemptEnums.*;
import vn.edu.ptit.english.repository.*;

@Repository
public class AttemptRepositoryImpl implements AttemptRepository {
    private final AttemptJpaRepository attempts;
    private final AttemptQuestionJpaRepository items;
    private final AttemptAnswerJpaRepository answers;
    private final AnswerTrialJpaRepository trials;
    private final SubmissionJpaRepository submissions;
    private final QuestionExposureJpaRepository exposures;
    private final LessonProgressJpaRepository progress;
    private final EntityManager em;
    private final LessonQuestionJpaRepository slots;
    private final LessonJpaRepository lessons;
    public AttemptRepositoryImpl(AttemptJpaRepository attempts,AttemptQuestionJpaRepository items,AttemptAnswerJpaRepository answers,
        AnswerTrialJpaRepository trials,SubmissionJpaRepository submissions,QuestionExposureJpaRepository exposures,
        LessonProgressJpaRepository progress,EntityManager em,LessonQuestionJpaRepository slots,LessonJpaRepository lessons) {
        this.attempts=attempts; this.items=items; this.answers=answers; this.trials=trials; this.submissions=submissions;
        this.exposures=exposures; this.progress=progress; this.em=em;
        this.slots=slots;
        this.lessons=lessons;
    }
    public Optional<AttemptEntity> attempt(long id,long userId,boolean lock) {
        var result=lock?attempts.lockOwned(id,userId):attempts.findByIdAndUserId(id,userId);
        if(lock)result.ifPresent(v -> em.refresh(v,LockModeType.PESSIMISTIC_WRITE));
        return result;
    }
    public List<AttemptEntity> active(long userId,Mode mode){return attempts.findByUserIdAndModeAndStateOrderByIdAsc(userId,mode,State.IN_PROGRESS);}
    public Page<AttemptEntity> attempts(long userId,Mode mode,State state,int page,int size) {
        return attempts.findAll((root,query,cb) -> {
            var filters=new ArrayList<jakarta.persistence.criteria.Predicate>(); filters.add(cb.equal(root.get("userId"),userId));
            if(mode!=null)filters.add(cb.equal(root.get("mode"),mode));
            if(state!=null)filters.add(cb.equal(root.get("state"),state));
            return cb.and(filters.toArray(jakarta.persistence.criteria.Predicate[]::new));
        },PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"updatedAt","id")));
    }
    public List<AttemptQuestionEntity> items(long attemptId){return items.findByAttemptIdOrderByPositionAsc(attemptId);}
    public Optional<AttemptQuestionEntity> item(long id,long attemptId){return items.findByIdAndAttemptId(id,attemptId);}
    public List<AttemptAnswerEntity> answers(Collection<Long> ids){return ids.isEmpty()?List.of():answers.findAllById(ids);}
    public List<AnswerTrialEntity> trials(long item){return trials.findByAttemptQuestionIdOrderByTrialNoAsc(item);}
    public Optional<SubmissionEntity> submission(long attemptId){return submissions.findByAttemptId(attemptId);}
    public Optional<Long> lessonSlot(long lessonVersionId,long questionVersionId){return slots.slot(lessonVersionId,questionVersionId);}
    public boolean assetReadable(long userId,long assetId){return attempts.assetReadable(userId,assetId);}
    public List<QuestionExposureEntity> exposures(long userId,Collection<String> origins){return origins.isEmpty()?List.of():exposures.findByIdUserIdAndIdOriginKeyIn(userId,origins);}
    public Optional<QuestionExposureEntity> exposure(QuestionExposureKey id,boolean lock){return find(QuestionExposureEntity.class,id,lock);}
    public Optional<LessonProgressEntity> progress(LessonProgressKey id,boolean lock){return find(LessonProgressEntity.class,id,lock);}
    private <T> Optional<T> find(Class<T> type,Object id,boolean lock) {
        T value=lock?em.find(type,id,LockModeType.PESSIMISTIC_WRITE):em.find(type,id);
        if(value!=null&&lock)em.refresh(value,LockModeType.PESSIMISTIC_WRITE);
        return Optional.ofNullable(value);
    }
    public Page<LessonProgressEntity> progress(long userId,Long unitId,int page,int size){return progress.pageOwned(userId,unitId,PageRequest.of(page,size));}
    public Page<LessonEntity> progressLessons(long userId,Long unitId,int page,int size){return lessons.progressLessons(userId,unitId,PageRequest.of(page,size));}
    public AttemptEntity save(AttemptEntity v){return attempts.saveAndFlush(v);}
    public AttemptQuestionEntity save(AttemptQuestionEntity v){return items.saveAndFlush(v);}
    public AttemptAnswerEntity save(AttemptAnswerEntity v){return answers.saveAndFlush(v);}
    public AnswerTrialEntity save(AnswerTrialEntity v){return trials.saveAndFlush(v);}
    @Transactional(propagation=Propagation.MANDATORY)
    public SubmissionEntity save(SubmissionEntity v){
        if(v.getId()!=null)throw new IllegalStateException("A submission is immutable");
        var saved=submissions.saveAndFlush(v); saved.bindGeneratedId(); submissions.flush(); return saved;
    }
    public QuestionExposureEntity save(QuestionExposureEntity v){return exposures.saveAndFlush(v);}
    public LessonProgressEntity save(LessonProgressEntity v){return progress.saveAndFlush(v);}
}
