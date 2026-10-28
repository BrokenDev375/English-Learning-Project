package vn.edu.ptit.english.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.dto.ContentReviewRequests.Decision;
import vn.edu.ptit.english.dto.ContentReviewViews.*;
import vn.edu.ptit.english.dto.MaterialRequests.Command;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.*;
import vn.edu.ptit.english.service.RequestReceiptService.Result;
import static vn.edu.ptit.english.service.impl.AuthoringAccess.*;

@Service @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
public class ContentReviewService {
    private final ContentGovernance governance;
    private final ContentReviewJpaRepository reviews;
    private final ContentRepository content;
    private final AuthoringService authoring;
    private final AuthoringPublication publication;
    private final RequestReceiptService receipts;
    private final AccountGuard accounts;
    private final ObjectMapper json;
    public ContentReviewService(ContentGovernance governance,ContentReviewJpaRepository reviews,ContentRepository content,AuthoringService authoring,AuthoringPublication publication,RequestReceiptService receipts,AccountGuard accounts,ObjectMapper json){this.governance=governance;this.reviews=reviews;this.content=content;this.authoring=authoring;this.publication=publication;this.receipts=receipts;this.accounts=accounts;this.json=json;}
    private void author(UserPrincipal actor){accounts.requireCurrent(actor);AssetServiceImpl.author(actor);}
    private String resource(ContentReviewEntity r){return r.getKind().equals("LESSON")?"lessons":"questions";}
    private CatalogEntity root(String resource,long id){return resource.equals("lessons")?content.lesson(id,false).orElseThrow(AuthoringAccess::missing):content.question(id,false).orElseThrow(AuthoringAccess::missing);}
    private ContentReviewEntity review(long id,UserPrincipal actor){var r=reviews.findById(AuthoringRequests.id(id)).orElseThrow(AuthoringAccess::missing);governance.editable(root(resource(r),r.getResourceId()),actor);return r;}
    private void pending(ContentReviewEntity r,long revision){revision(r.getRevision(),revision);if(!r.getState().equals("PENDING"))throw conflict("REVIEW_FINISHED","Yêu cầu này đã được xử lý. Hãy tải lại danh sách.");}
    public CatalogViews.CatalogPage<Review> list(String state,int page,int size,UserPrincipal actor){
        author(actor);if(page<0||size<1||size>50||!Set.of("ALL","PENDING","RETURNED","APPROVED","WITHDRAWN").contains(state))throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Bộ lọc duyệt học liệu không hợp lệ.");
        org.springframework.data.jpa.domain.Specification<ContentReviewEntity> spec=(r,q,cb)->{
            var p=new ArrayList<Predicate>();if(!state.equals("ALL"))p.add(cb.equal(r.get("state"),state));
            if(actor.role()!=Role.ADMIN){
                var lessons=q.subquery(Long.class);var l=lessons.from(LessonEntity.class);var u=lessons.from(UnitEntity.class);
                lessons.select(l.get("id")).where(cb.equal(l.get("unitId"),u.get("id")),cb.equal(u.get("assignedTeacherId"),actor.id()));
                var questions=q.subquery(Long.class);var a=questions.from(QuestionEntity.class);questions.select(a.get("id")).where(cb.equal(a.get("ownerId"),actor.id()));
                p.add(cb.or(r.get("lessonId").in(lessons),r.get("questionId").in(questions)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return CatalogViews.CatalogPage.from(reviews.findAll(spec,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id"))).map(governance::viewReview));
    }
    public Detail detail(long id,UserPrincipal actor){
        author(actor);var r=review(id,actor);boolean stale=false;
        if(r.getState().equals("PENDING")){
            var entity=root(resource(r),r.getResourceId());
            if(entity instanceof LessonEntity l&&!governance.submittedByAdmin(r.getSubmittedBy()))stale=!Objects.equals(governance.assignedTeacher(l),r.getSubmittedBy());
            try{var current=authoring.preview(resource(r),r.getResourceId(),actor);stale=stale||!current.errors().isEmpty()||current.draftVersionId()!=r.getVersionId()||!current.previewToken().equals(r.getPreviewToken());}
            catch(ApiException ex){if(ex.status().is5xxServerError())throw ex;stale=true;}
        }
        return new Detail(governance.viewReview(r),r.getSnapshot(),stale,r.getState().equals("PENDING"));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Result submit(String resource,long id,Command input,UserPrincipal actor){
        AssetServiceImpl.author(actor);ContentGovernance.kind(resource);AuthoringRequests.id(id);
        return receipts.execute(actor,input.requestId(),"POST /api/author/"+resource+"/"+id+"/submit-review",input,201,()->{
            governance.lock();governance.editable(root(resource,id),actor);governance.draftFree(resource,id);
            var preview=authoring.preview(resource,id,actor);
            if(!Objects.equals(input.draftVersionId(),preview.draftVersionId())||input.baseRevision()!=preview.baseRevision()||!Objects.equals(input.previewToken(),preview.previewToken()))throw conflict("PREVIEW_STALE","Nội dung đã đổi sau lần xem trước. Hãy xem trước lại trước khi gửi duyệt.");
            if(!preview.errors().isEmpty())throw new ApiException(HttpStatus.BAD_REQUEST,"PUBLISH_VALIDATION_FAILED","Nội dung chưa đủ điều kiện gửi duyệt.");
            var tree=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(preview);
            tree.set("materials",json.valueToTree(publication.previewMaterials(resource,id,actor)));
            var data=tree.path("data");String title=resource.equals("lessons")?data.path("lesson").path("title").asText():data.path("prompt").asText();
            var r=new ContentReviewEntity(ContentGovernance.kind(resource),id,preview.draftVersionId(),preview.baseRevision(),preview.previewToken(),title.substring(0,Math.min(200,title.length())),tree,actor.id());
            reviews.saveAndFlush(r);return Map.of("review",governance.viewReview(r),"content",authoring.get(resource,id,actor));
        });
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Result decide(long id,String action,Decision input,UserPrincipal actor){
        AssetServiceImpl.author(actor);AuthoringRequests.id(id);
        return receipts.execute(actor,input.requestId(),"POST /api/author/reviews/"+id+"/"+action,input,200,()->{
            governance.lock();if(!action.equals("withdraw"))ContentGovernance.admin(actor);
            var r=review(id,actor);pending(r,input.baseRevision());
            if(action.equals("return")&&input.note().isBlank())throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Cần ghi lý do trả lại để giáo viên chỉnh sửa.");
            if(action.equals("approve")){
                var entity=root(resource(r),r.getResourceId());
                if(entity instanceof LessonEntity&&!governance.submittedByAdmin(r.getSubmittedBy())){
                    var current=governance.assignedTeacher((LessonEntity)entity);
                    if(!Objects.equals(current,r.getSubmittedBy()))throw conflict("ASSIGNMENT_CHANGED","Giáo viên phụ trách đã thay đổi. Hãy trả lại để người phụ trách hiện tại kiểm tra và gửi duyệt.");
                }
                publication.publish(resource(r),r.getResourceId(),new Command(input.requestId(),r.getVersionRevision(),r.getVersionId(),r.getPreviewToken()),actor);
                r.decide("APPROVED",actor.id(),input.note());reviews.saveAndFlush(r);
                // Approval of a lesson also reviews its exact question versions in the stored closure.
                for(var dependency:reviews.findByKindAndState("QUESTION","PENDING")){
                    var q=content.question(dependency.getResourceId(),false).orElseThrow(AuthoringAccess::missing);
                    if(q.getPublished()!=null&&q.getPublished().getId()==dependency.getVersionId()){
                        dependency.decide("APPROVED",actor.id(),"Được duyệt cùng yêu cầu bài học #"+id+".");reviews.saveAndFlush(dependency);
                    }
                }
            }else if(action.equals("return")||action.equals("withdraw")){
                r.decide(action.equals("return")?"RETURNED":"WITHDRAWN",actor.id(),input.note());reviews.saveAndFlush(r);
            }else throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Thao tác duyệt không hợp lệ.");
            return governance.viewReview(r);
        });
    }
}
