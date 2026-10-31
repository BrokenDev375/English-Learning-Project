package vn.edu.ptit.english.repository.impl;

import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.repository.ContestPaperRepository;

@Repository
public class ContestPaperRepositoryImpl implements ContestPaperRepository {
    private final EntityManager em;
    public ContestPaperRepositoryImpl(EntityManager em){this.em=em;}
    public Optional<ContestPermissionEntity> permission(long user,boolean lock){return Optional.ofNullable(lock?em.find(ContestPermissionEntity.class,user,LockModeType.PESSIMISTIC_WRITE):em.find(ContestPermissionEntity.class,user));}
    private String pattern(String query){return "%"+query.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
    private String teacherWhere(String query,boolean proctorOnly){return " where u.role='TEACHER' and u.status='ACTIVE'"+(proctorOnly?" and p.can_proctor=true":"")+(query.isEmpty()?"":" and (lower(u.full_name) like :query escape '!' or lower(u.email) like :query escape '!')");}
    @SuppressWarnings("unchecked") public List<Object[]> teachers(String query,boolean proctorOnly,int offset,int size){var q=em.createNativeQuery("select u.id,u.full_name,u.email,coalesce(p.can_author,false),coalesce(p.can_proctor,false),coalesce(p.revision,0),p.updated_at from users u left join contest_teacher_permissions p on p.user_id=u.id"+teacherWhere(query,proctorOnly)+" order by u.full_name,u.id");if(!query.isEmpty())q.setParameter("query",pattern(query));return q.setFirstResult(offset).setMaxResults(size).getResultList();}
    public long teacherCount(String query,boolean proctorOnly){var q=em.createNativeQuery("select count(*) from users u left join contest_teacher_permissions p on p.user_id=u.id"+teacherWhere(query,proctorOnly));if(!query.isEmpty())q.setParameter("query",pattern(query));return ((Number)q.getSingleResult()).longValue();}
    public Optional<ContestPaperEntity> paper(long id,boolean lock){return Optional.ofNullable(lock?em.find(ContestPaperEntity.class,id,LockModeType.PESSIMISTIC_WRITE):em.find(ContestPaperEntity.class,id));}
    private String where(Long owner,String state,String query){return " where 1=1"+(owner==null?"":" and p.ownerId=:owner")+(state.equals("ALL")?"":" and p.state=:state")+(query.isEmpty()?"":" and (lower(p.title) like :query escape '!' or exists (select u.id from UserEntity u where u.id=p.ownerId and lower(u.fullName) like :query escape '!'))");}
    public List<ContestPaperEntity> papers(Long owner,String state,String query,int offset,int size){var q=em.createQuery("select p from ContestPaperEntity p"+where(owner,state,query)+" order by p.updatedAt desc,p.id desc",ContestPaperEntity.class);if(owner!=null)q.setParameter("owner",owner);if(!state.equals("ALL"))q.setParameter("state",state);if(!query.isEmpty())q.setParameter("query",pattern(query));return q.setFirstResult(offset).setMaxResults(size).getResultList();}
    public long paperCount(Long owner,String state,String query){var q=em.createQuery("select count(p) from ContestPaperEntity p"+where(owner,state,query),Long.class);if(owner!=null)q.setParameter("owner",owner);if(!state.equals("ALL"))q.setParameter("state",state);if(!query.isEmpty())q.setParameter("query",pattern(query));return q.getSingleResult();}
    public long usage(long paper){return em.createQuery("select count(c) from ContestEntity c where c.sourcePaperId=:id",Long.class).setParameter("id",paper).getSingleResult();}
    public ContestPermissionEntity save(ContestPermissionEntity value){if(!em.contains(value))em.persist(value);em.flush();return value;}
    public ContestPaperEntity save(ContestPaperEntity value){if(!em.contains(value))em.persist(value);em.flush();return value;}
}
