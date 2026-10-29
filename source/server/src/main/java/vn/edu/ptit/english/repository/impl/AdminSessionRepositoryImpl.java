package vn.edu.ptit.english.repository.impl;

import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.english.dto.AdminSessionViews.User;
import vn.edu.ptit.english.enums.*;
import vn.edu.ptit.english.repository.AdminSessionRepository;

@Repository
public class AdminSessionRepositoryImpl implements AdminSessionRepository {
    private final EntityManager em;
    public AdminSessionRepositoryImpl(EntityManager em){this.em=em;}
    public List<User> users(Set<Long> ids) {
        if(ids.isEmpty())return List.of();
        return em.createQuery("select u.id,u.fullName,u.email,u.role,u.status from UserEntity u where u.id in :ids and u.status=:status",Object[].class)
            .setParameter("ids",ids).setParameter("status",AccountStatus.ACTIVE).getResultList().stream()
            .map(r->new User((Long)r[0],(String)r[1],(String)r[2],(Role)r[3],(AccountStatus)r[4])).toList();
    }
    public List<AttemptContext> attempts(Set<Long> ids) {
        if(ids.isEmpty())return List.of();
        // Scalar fields only. Never load answer/selection JSON or traverse lazy version collections.
        return em.createQuery("select a.id,a.userId,a.mode,a.state,v.title,u.roundNumber from AttemptEntity a "
            +"left join LessonVersionEntity v on v.id=a.lessonVersionId left join LessonEntity l on l.id=v.lessonId "
            +"left join UnitEntity u on u.id=l.unitId where a.id in :ids",Object[].class).setParameter("ids",ids).getResultList().stream()
            .map(r->new AttemptContext((Long)r[0],(Long)r[1],r[2].toString(),r[3].toString(),(String)r[4],(Integer)r[5])).toList();
    }
}
