package vn.edu.ptit.english.repository.impl;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.repository.GamificationRepository;

@Repository
public class GamificationRepositoryImpl implements GamificationRepository {
    private final EntityManager em;
    public GamificationRepositoryImpl(EntityManager em) { this.em=em; }
    public <T extends GamificationRow> T save(T entity) { if(!em.contains(entity)) em.persist(entity); em.flush(); return entity; }
    public Optional<GamificationProfileEntity> profile(long userId) { return Optional.ofNullable(em.find(GamificationProfileEntity.class,userId)); }
    public Optional<GamificationDayEntity> day(long userId,LocalDate date) { return Optional.ofNullable(em.find(GamificationDayEntity.class,new GamificationDayEntity.Key(userId,date))); }
    public List<GamificationDayEntity> days(long userId) {
        return em.createQuery("select d from GamificationDayEntity d where d.userId=:u order by d.activityDate",GamificationDayEntity.class).setParameter("u",userId).getResultList();
    }
    public Optional<GamificationClaimEntity> claim(long userId,String type,String key) {
        return em.createQuery("select c from GamificationClaimEntity c where c.userId=:u and c.rewardType=:t and c.sourceKey=:k",GamificationClaimEntity.class)
            .setParameter("u",userId).setParameter("t",type).setParameter("k",key).getResultStream().findFirst();
    }
    public boolean claimedDay(long userId,long objectiveId,LocalDate date) {
        return em.createQuery("select count(c) from GamificationClaimEntity c where c.userId=:u and c.rewardType='DUE_REVIEW' and c.objectiveId=:o and c.activityDate=:d",Long.class)
            .setParameter("u",userId).setParameter("o",objectiveId).setParameter("d",date).getSingleResult()>0;
    }
    public List<UserBadgeEntity> badges(long userId) { return em.createQuery("select b from UserBadgeEntity b where b.userId=:u order by b.badgeCode",UserBadgeEntity.class).setParameter("u",userId).getResultList(); }
    public Optional<GamificationProcessedEntity> processed(long sourceId) { return Optional.ofNullable(em.find(GamificationProcessedEntity.class,sourceId)); }
    public Optional<GamificationFailureEntity> failure(long sourceId) { return Optional.ofNullable(em.find(GamificationFailureEntity.class,sourceId)); }
    public void clearFailure(long sourceId) { failure(sourceId).ifPresent(em::remove); }
    private static final String OWN=" from SubmissionEntity s, AttemptEntity a where s.attemptId=a.id and a.userId=:u";
    private static final String PENDING=OWN+" and not exists (select p.submissionId from GamificationProcessedEntity p where p.submissionId=s.id)";
    private static final String READY=" and not exists (select f.submissionId from GamificationFailureEntity f where f.submissionId=s.id and f.retryAt>:now)";
    public Optional<SubmissionEntity> source(long userId,long sourceId) { return em.createQuery("select s"+OWN+" and s.id=:s",SubmissionEntity.class).setParameter("u",userId).setParameter("s",sourceId).getResultStream().findFirst(); }
    public List<SubmissionEntity> pending(long userId,Instant now,int limit) { return em.createQuery("select s"+PENDING+READY+" order by s.id",SubmissionEntity.class).setParameter("u",userId).setParameter("now",now).setMaxResults(limit).getResultList(); }
    public long pendingCount(long userId) { return em.createQuery("select count(s)"+PENDING,Long.class).setParameter("u",userId).getSingleResult(); }
    public long deferredCount(long userId,Instant now) { return em.createQuery("select count(s)"+PENDING+" and exists (select f.submissionId from GamificationFailureEntity f where f.submissionId=s.id and f.retryAt>:now)",Long.class).setParameter("u",userId).setParameter("now",now).getSingleResult(); }
    public List<Long> pendingUsers(long after,Instant now,int limit) {
        String q="select distinct a.userId from SubmissionEntity s,AttemptEntity a where s.attemptId=a.id and a.userId>:after"
            +" and not exists (select p.submissionId from GamificationProcessedEntity p where p.submissionId=s.id)"+READY+" order by a.userId";
        return em.createQuery(q,Long.class).setParameter("after",after).setParameter("now",now).setMaxResults(limit).getResultList();
    }
    public List<GamificationProcessedEntity> activities(long userId,Long source,int page,int size) {
        String q="select p from GamificationProcessedEntity p,SubmissionEntity s,AttemptEntity a where p.userId=:u and p.submissionId=s.id and s.attemptId=a.id"
            +(source==null?"":" and p.submissionId=:source")+" order by a.submittedAt desc,s.id desc";
        var query=em.createQuery(q,GamificationProcessedEntity.class).setParameter("u",userId);
        if(source!=null) query.setParameter("source",source);
        long offset=(long)page*size; if(offset>Integer.MAX_VALUE) return List.of();
        return query.setFirstResult((int)offset).setMaxResults(size).getResultList();
    }
    public long activityCount(long userId,Long source) {
        var query=em.createQuery("select count(p) from GamificationProcessedEntity p where p.userId=:u"+(source==null?"":" and p.submissionId=:source"),Long.class).setParameter("u",userId);
        if(source!=null) query.setParameter("source",source); return query.getSingleResult();
    }
    public List<GamificationOutboxEntity> pendingEvents(int limit) {
        return em.createQuery("select e from GamificationOutboxEntity e where e.deliveredAt is null order by e.createdAt,e.eventId",GamificationOutboxEntity.class)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE).setMaxResults(limit).getResultList();
    }
}
