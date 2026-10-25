package vn.edu.ptit.english.repository.impl;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.dto.LeagueViews.Tier;
import vn.edu.ptit.english.repository.LeagueRepository;

@Repository
public class LeagueRepositoryImpl implements LeagueRepository {
    private final EntityManager em;
    public LeagueRepositoryImpl(EntityManager em){this.em=em;}
    public <T extends GamificationRow> T save(T row){if(!em.contains(row))em.persist(row);em.flush();return row;}
    public void lockPolicy(){em.createQuery("select p from GamificationPolicyEntity p",GamificationPolicyEntity.class)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE).getSingleResult();}
    public Optional<LeagueSeasonEntity> season(Instant start){return em.createQuery("select s from LeagueSeasonEntity s where s.startAt=:s",LeagueSeasonEntity.class)
        .setParameter("s",start).getResultStream().findFirst();}
    public LeagueSeasonEntity season(long id){return em.find(LeagueSeasonEntity.class,id);}
    public LeagueSeasonEntity lockSeason(long id){var row=season(id);em.refresh(row,LockModeType.PESSIMISTIC_WRITE);return row;}
    public void lockAllocator(long season,Tier tier){var row=em.find(LeagueAllocatorEntity.class,new LeagueAllocatorEntity.Key(season,tier));em.refresh(row,LockModeType.PESSIMISTIC_WRITE);}
    public LeagueGroupEntity group(long id){return em.find(LeagueGroupEntity.class,id);}
    public LeagueGroupEntity lockGroup(long id){var row=group(id);em.refresh(row,LockModeType.PESSIMISTIC_WRITE);return row;}
    public Optional<LeagueGroupEntity> available(long season,Tier tier){return em.createQuery("select g from LeagueGroupEntity g where g.seasonId=:s and g.tier=:t and g.memberCount<25 and g.finalizedAt is null order by g.groupNo",LeagueGroupEntity.class)
        .setParameter("s",season).setParameter("t",tier).setMaxResults(1).getResultStream().findFirst();}
    public int nextNumber(long season,Tier tier){return em.createQuery("select coalesce(max(g.groupNo),0)+1 from LeagueGroupEntity g where g.seasonId=:s and g.tier=:t",Integer.class).setParameter("s",season).setParameter("t",tier).getSingleResult();}
    public Optional<LeagueMemberEntity> member(long user,long season){return em.createQuery("select m from LeagueMemberEntity m where m.userId=:u and m.seasonId=:s",LeagueMemberEntity.class)
        .setParameter("u",user).setParameter("s",season).getResultStream().findFirst();}
    public List<LeagueMemberEntity> members(long group){return em.createQuery("select m from LeagueMemberEntity m where m.groupId=:g order by m.memberKey",LeagueMemberEntity.class).setParameter("g",group).getResultList();}
    public List<LeagueMemberEntity> previous(long user,Instant before){return em.createQuery("select m from LeagueMemberEntity m,LeagueSeasonEntity s where m.seasonId=s.id and m.userId=:u and s.startAt<:b order by s.startAt desc",LeagueMemberEntity.class)
        .setParameter("u",user).setParameter("b",before).getResultList();}
    public Map<Long,String> names(List<Long> users){var result=new HashMap<Long,String>();if(users.isEmpty())return result;
        em.createQuery("select u.id,u.fullName from UserEntity u where u.id in :ids",Object[].class).setParameter("ids",users).getResultList().forEach(r->result.put((Long)r[0],(String)r[1]));return result;}
    private static final String HISTORY=" from LeagueMemberEntity m,LeagueSeasonEntity s where m.seasonId=s.id and m.userId=:u and m.result is not null";
    public List<LeagueMemberEntity> history(long user,int page,int size){long offset=(long)page*size;if(offset>Integer.MAX_VALUE)return List.of();
        return em.createQuery("select m"+HISTORY+" order by s.startAt desc",LeagueMemberEntity.class).setParameter("u",user).setFirstResult((int)offset).setMaxResults(size).getResultList();}
    public long historyCount(long user){return em.createQuery("select count(m)"+HISTORY,Long.class).setParameter("u",user).getSingleResult();}
    public List<Long> dueSeasons(Instant at){return em.createQuery("select s.id from LeagueSeasonEntity s where s.status<>'FINALIZED' and s.endAt<=:at order by s.startAt",Long.class).setParameter("at",at).setMaxResults(100).getResultList();}
    public List<Long> unfinishedGroups(long season){return em.createQuery("select g.id from LeagueGroupEntity g where g.seasonId=:s and g.finalizedAt is null order by g.id",Long.class).setParameter("s",season).setMaxResults(20).getResultList();}
    public List<XpLedgerEntity> ledger(long submission){return em.createQuery("select x from XpLedgerEntity x where x.submissionId=:s order by x.claimId",XpLedgerEntity.class).setParameter("s",submission).getResultList();}
    public List<LeagueOutboxEntity> events(int limit){return em.createQuery("select e from LeagueOutboxEntity e where e.deliveredAt is null order by e.createdAt,e.eventId",LeagueOutboxEntity.class)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE).setMaxResults(limit).getResultList();}
}
