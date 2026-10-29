package vn.edu.ptit.english.service.impl;

import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.security.UserPrincipal;
import static vn.edu.ptit.english.service.impl.AuthoringAccess.*;

/** Writes lock actor -> content_control -> Unit/dependency roots; reads never take this lock. */
@Component
public class ContentGovernance {
    private final ContentControlJpaRepository control;
    private final ContentReviewJpaRepository reviews;
    private final CatalogRepository catalog;
    private final ContentRepository content;
    private final EntityManager em;
    private final UserRepository users;
    public ContentGovernance(ContentControlJpaRepository control,ContentReviewJpaRepository reviews,CatalogRepository catalog,ContentRepository content,EntityManager em,UserRepository users){this.users=users;this.control=control;this.reviews=reviews;this.catalog=catalog;this.content=content;this.em=em;}
    public boolean submittedByAdmin(long id){return users.findById(id).map(u->u.role()==Role.ADMIN).orElse(false);}
    public Long assignedTeacher(LessonEntity lesson){return catalog.unit(lesson.getUnitId(),false).map(UnitEntity::getAssignedTeacherId).orElse(null);}
    private String name(Long id){return id==null?null:users.findById(id).map(vn.edu.ptit.english.dto.UserRecord::fullName).orElse("Tài khoản không còn");}
    public vn.edu.ptit.english.dto.ContentReviewViews.Review viewReview(ContentReviewEntity r){return new vn.edu.ptit.english.dto.ContentReviewViews.Review(r.getId(),r.getKind(),r.getResourceId(),r.getTitle(),r.getVersionId(),r.getRevision(),r.getState(),r.getSubmittedBy(),name(r.getSubmittedBy()),r.getSubmittedAt(),r.getReviewedBy(),name(r.getReviewedBy()),r.getReviewedAt(),r.getNote());}
    public Object reviewView(String resource,long id,Long version){var value=latest(resource,id,version);return value==null?null:viewReview(value);}
    public void lock(){control.lockContent();}
    public static void admin(UserPrincipal actor){if(actor.role()!=Role.ADMIN)throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Chỉ quản trị viên được quản lý lộ trình và công bố học liệu.");}
    public boolean manages(CatalogEntity root,UserPrincipal actor){
        if(actor.role()==Role.ADMIN)return true;
        if(actor.role()!=Role.TEACHER)return false;
        if(root instanceof UnitEntity u)return Long.valueOf(actor.id()).equals(u.getAssignedTeacherId());
        if(root instanceof LessonEntity l)return catalog.unit(l.getUnitId(),false).map(u->Long.valueOf(actor.id()).equals(u.getAssignedTeacherId())).orElse(false);
        return root.getOwnerId()==actor.id();
    }
    public void editable(CatalogEntity root,UserPrincipal actor){if(!manages(root,actor))throw missing();}
    public void draftFree(String resource,long id){if(reviews.pending(kind(resource),id)>0)throw conflict("REVIEW_PENDING","Nội dung đang chờ duyệt. Rút yêu cầu hoặc trả lại trước khi chỉnh sửa.");}
    public static String kind(String resource){if(resource.equals("questions"))return "QUESTION";if(resource.equals("lessons"))return "LESSON";throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Loại học liệu không hợp lệ.");}
    public ContentReviewEntity latest(String resource,long id,Long version){
        var rows=reviews.history(kind(resource),id,PageRequest.of(0,1));
        return rows.isEmpty()||version==null||rows.get(0).getVersionId()!=version?null:rows.get(0);
    }
    private static final String ASSIGNED=" from LessonEntity l left join l.draft d left join l.published p, UnitEntity u, LessonVersionEntity v join v.questions s, QuestionVersionEntity q"
        +" where l.unitId=u.id and u.assignedTeacherId=:actor and (v.id=d.id or v.id=p.id) and q.id=s.questionVersionId";
    public boolean knowsQuestion(long version,UserPrincipal actor){return actor.role()==Role.TEACHER&&em.createQuery("select count(l)"+ASSIGNED+" and q.id=:version",Long.class).setParameter("actor",actor.id()).setParameter("version",version).getSingleResult()>0;}
    public boolean knowsMaterial(long version,UserPrincipal actor){return actor.role()==Role.TEACHER&&em.createQuery("select count(l)"+ASSIGNED+" and q.materialVersionId=:version",Long.class).setParameter("actor",actor.id()).setParameter("version",version).getSingleResult()>0;}
    public boolean knowsAsset(long asset,UserPrincipal actor){return actor.role()==Role.TEACHER&&em.createQuery("select count(l)"+ASSIGNED+" and exists (select m.id from MaterialVersionEntity m where m.id=q.materialVersionId and m.assetId=:asset)",Long.class).setParameter("actor",actor.id()).setParameter("asset",asset).getSingleResult()>0;}
    public Object version(String resource,long id,UserPrincipal actor){
        if(resource.equals("questions")){
            var v=content.questionVersion(id).orElseThrow(AuthoringAccess::missing);var r=content.question(v.getQuestionId(),false).orElseThrow(AuthoringAccess::missing);
            if(!own(r,actor)&&!(v.getState().equals("PUBLISHED")&&r.getPublished()!=null&&r.getRetiredAt()==null)&&!knowsQuestion(id,actor))throw missing();return v.view();
        }
        if(resource.equals("materials")){
            var v=content.materialVersion(id).orElseThrow(AuthoringAccess::missing);var r=em.find(MaterialEntity.class,v.getMaterialId());
            if(r==null||!own(r,actor)&&!(v.getState().equals("PUBLISHED")&&r.getPublished()!=null&&r.getRetiredAt()==null)&&!knowsMaterial(id,actor))throw missing();return v.view();
        }
        throw missing();
    }
}
