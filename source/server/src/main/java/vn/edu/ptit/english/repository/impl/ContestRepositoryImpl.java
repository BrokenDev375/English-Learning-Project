package vn.edu.ptit.english.repository.impl;

import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.repository.ContestRepository;

@Repository
public class ContestRepositoryImpl implements ContestRepository {
    private final EntityManager em;
    public ContestRepositoryImpl(EntityManager em){this.em=em;}
    public Optional<ContestEntity> contest(long id,boolean lock){return Optional.ofNullable(lock?em.find(ContestEntity.class,id,LockModeType.PESSIMISTIC_WRITE):em.find(ContestEntity.class,id));}
    public Optional<ContestParticipantEntity> participant(long contest,long user,boolean lock){var q=em.createQuery("select p from ContestParticipantEntity p where p.contestId=:contest and p.userId=:user",ContestParticipantEntity.class).setParameter("contest",contest).setParameter("user",user);if(lock)q.setLockMode(LockModeType.PESSIMISTIC_WRITE);return q.getResultStream().findFirst();}
    public Optional<ContestParticipantEntity> participantById(long id,boolean lock){return Optional.ofNullable(lock?em.find(ContestParticipantEntity.class,id,LockModeType.PESSIMISTIC_WRITE):em.find(ContestParticipantEntity.class,id));}
    public Optional<ContestSubmissionEntity> submission(long id){return Optional.ofNullable(em.find(ContestSubmissionEntity.class,id));}
    private String visible(String role){return role.equals("ADMIN")?"1=1":role.equals("TEACHER")?"JSON_CONTAINS(proctor_ids_json,CAST(:user AS JSON))":"state<>'DRAFT'";}
    @SuppressWarnings("unchecked") public List<ContestEntity> contests(String role,long user,int offset,int limit){var q=em.createNativeQuery("select * from contests where "+visible(role)+" order by created_at desc,id desc",ContestEntity.class);if(role.equals("TEACHER"))q.setParameter("user",Long.toString(user));return q.setFirstResult(offset).setMaxResults(limit).getResultList();}
    public long contestCount(String role,long user){var q=em.createNativeQuery("select count(*) from contests where "+visible(role));if(role.equals("TEACHER"))q.setParameter("user",Long.toString(user));return ((Number)q.getSingleResult()).longValue();}
    public List<Long> activeIds(){return em.createQuery("select c.id from ContestEntity c where c.state in ('PUBLISHED','RUNNING','CLOSED') order by c.id",Long.class).getResultList();}
    public List<ContestParticipantEntity> participants(long contest){return em.createQuery("select p from ContestParticipantEntity p where p.contestId=:contest order by p.id",ContestParticipantEntity.class).setParameter("contest",contest).getResultList();}
    public List<ContestEventEntity> events(long contest,Long participant,int offset,int limit){String where=" where e.contestId=:contest"+(participant==null?"":" and e.participantId=:participant");var q=em.createQuery("select e from ContestEventEntity e"+where+" order by e.id desc",ContestEventEntity.class).setParameter("contest",contest);if(participant!=null)q.setParameter("participant",participant);return q.setFirstResult(offset).setMaxResults(limit).getResultList();}
    public long eventCount(long contest,Long participant){String where=" where e.contestId=:contest"+(participant==null?"":" and e.participantId=:participant");var q=em.createQuery("select count(e) from ContestEventEntity e"+where,Long.class).setParameter("contest",contest);if(participant!=null)q.setParameter("participant",participant);return q.getSingleResult();}
    public ContestEntity save(ContestEntity value){if(!em.contains(value))em.persist(value);em.flush();return value;}
    public ContestParticipantEntity save(ContestParticipantEntity value){if(!em.contains(value))em.persist(value);em.flush();return value;}
    public ContestSubmissionEntity save(ContestSubmissionEntity value){em.persist(value);em.flush();return value;}
    public void save(ContestEventEntity value){em.persist(value);}
}
