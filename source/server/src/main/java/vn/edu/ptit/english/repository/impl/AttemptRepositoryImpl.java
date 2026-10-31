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
    private final org.springframework.context.ApplicationEventPublisher events;
    public AttemptRepositoryImpl(AttemptJpaRepository attempts,AttemptQuestionJpaRepository items,AttemptAnswerJpaRepository answers,
        AnswerTrialJpaRepository trials,SubmissionJpaRepository submissions,QuestionExposureJpaRepository exposures,
        LessonProgressJpaRepository progress,EntityManager em,LessonQuestionJpaRepository slots,LessonJpaRepository lessons,org.springframework.context.ApplicationEventPublisher events) {
        this.attempts=attempts; this.items=items; this.answers=answers; this.trials=trials; this.submissions=submissions;
        this.exposures=exposures; this.progress=progress; this.em=em;
        this.slots=slots;
        this.lessons=lessons;
        this.events=events;
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
    public Map<Long,Long> slotLessonVersions(Collection<Long> ids){var result=new HashMap<Long,Long>();if(!ids.isEmpty())slots.findAllById(ids).forEach(s->result.put(s.getId(),s.getLessonVersionId()));return result;}
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
    public List<LessonEntity> publishedRoundLessons(Collection<Long> unitIds) {
        return unitIds.isEmpty()?List.of():em.createQuery("select l from LessonEntity l join fetch l.published where l.unitId in :units and l.retiredAt is null order by l.sortOrder,l.id",LessonEntity.class).setParameter("units",unitIds).getResultList();
    }
    public List<LessonProgressEntity> roundProgress(long userId,Collection<Long> unitIds) {
        return unitIds.isEmpty()?List.of():em.createQuery("select p from LessonProgressEntity p join LessonEntity l on l.id=p.id.lessonId where p.id.userId=:user and l.unitId in :units",LessonProgressEntity.class).setParameter("user",userId).setParameter("units",unitIds).getResultList();
    }
    public AttemptEntity save(AttemptEntity v){
        var saved=attempts.saveAndFlush(v);
        if(saved.getRevision()>0) events.publishEvent(new vn.edu.ptit.english.socket.AttemptChanged(saved.getUserId(),saved.getId(),saved.getRevision()));
        return saved;
    }
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
