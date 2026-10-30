package vn.edu.ptit.english.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.ptit.english.dto.WritingData.*;
import vn.edu.ptit.english.dto.WritingData;
import vn.edu.ptit.english.dto.CatalogViews.CatalogPage;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.*;
import vn.edu.ptit.english.service.RequestReceiptService.Result;

@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class WritingServiceImpl implements WritingService {
    private final WritingRepository writing;private final CatalogRepository catalog;private final AccountGuard accounts;
    private final RequestReceiptService receipts;private final ContentGovernance governance;private final RoundJourneyReader journey;
    private final UserJpaRepository users;private final ObjectMapper json;
    public WritingServiceImpl(WritingRepository writing,CatalogRepository catalog,AccountGuard accounts,RequestReceiptService receipts,ContentGovernance governance,RoundJourneyReader journey,UserJpaRepository users,ObjectMapper json){this.writing=writing;this.catalog=catalog;this.accounts=accounts;this.receipts=receipts;this.governance=governance;this.journey=journey;this.users=users;this.json=json;}
    private static ApiException missing(){return new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","Không tìm thấy bài viết được phép truy cập.");}
    private static ApiException conflict(String code,String message){return new ApiException(HttpStatus.CONFLICT,code,message);}
    private void role(UserPrincipal actor,boolean student){if(student?actor.role()!=Role.STUDENT:actor.role()!=Role.TEACHER&&actor.role()!=Role.ADMIN)throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Vai trò không có quyền thực hiện thao tác này.");}
    private UnitEntity unit(long id,boolean lock){return catalog.unit(vn.edu.ptit.english.dto.AuthoringRequests.id(id),lock).orElseThrow(WritingServiceImpl::missing);}
    private UnitEntity teacher(long id,boolean lock,UserPrincipal actor){role(actor,false);var u=unit(id,lock);if(actor.role()!=Role.ADMIN&&!Objects.equals(u.getAssignedTeacherId(),actor.id()))throw missing();return u;}
    private void revision(long actual,long expected){if(actual!=expected)throw conflict("REVISION_CONFLICT","Bài viết đã thay đổi. Hãy tải bản máy chủ và so sánh trước khi tiếp tục.");}
    private Version version(long id){var v=writing.version(id).orElseThrow(WritingServiceImpl::missing);return new Version(v.getId(),json.convertValue(v.getDefinition(),Definition.class),v.getCreatedAt());}
    private Task taskView(long unit){var t=writing.task(unit).orElse(null);return new Task(unit,t==null?0:t.getRevision(),t==null||t.getDraft()==null?null:json.convertValue(t.getDraft(),Definition.class),t==null||t.getPublishedVersionId()==null?null:version(t.getPublishedVersionId()));}
    private Work workView(WritingWorkEntity w){return new Work(w.getId(),w.getVersionId(),w.getRevision(),w.getState(),w.getText(),w.getLatestSubmissionId(),w.getUpdatedAt());}
    private Grade gradeView(WritingGradeEntity g){return new Grade(g.getId(),g.getEarnedPoints(),g.getTotalPoints(),json.convertValue(g.getMarks(),new TypeReference<List<Mark>>(){}),g.getComment(),g.isAllowRevision(),g.getGradedAt());}
    private Submission submissionView(WritingSubmissionEntity s,Map<Long,WritingGradeEntity> grades){var g=grades.get(s.getId());return new Submission(s.getId(),s.getWorkId(),s.getVersionId(),s.getText(),s.getSubmittedAt(),g==null?null:gradeView(g));}
    private Map<Long,WritingGradeEntity> grades(List<WritingSubmissionEntity> rows){var result=new HashMap<Long,WritingGradeEntity>();writing.grades(rows.stream().map(WritingSubmissionEntity::getId).toList()).forEach(g->result.put(g.getSubmissionId(),g));return result;}
    @Override public Task task(long unit,UserPrincipal actor){accounts.requireCurrent(actor);teacher(unit,false,actor);return taskView(unit);}
    @Override @Transactional(isolation=Isolation.READ_COMMITTED) public Result configure(long unit,String operation,Command input,UserPrincipal actor){
        role(actor,false);
        return receipts.execute(actor,input.requestId(),"WRITING "+operation+" /units/"+unit,input,200,()->{
            governance.lock();teacher(unit,true,actor);var t=writing.task(unit).orElse(null);revision(t==null?0:t.getRevision(),input.baseRevision());
            if(t==null)t=writing.save(new WritingTaskEntity(unit));
            switch(operation){
                case "definition" -> t.draft(json.valueToTree(input.definition()));
                case "publish" -> {if(t.getDraft()==null)throw conflict("WRITING_DRAFT_REQUIRED","Hãy lưu đề và tiêu chí trước khi công bố.");var v=writing.save(new WritingVersionEntity(unit,t.getDraft(),actor.id()));t.publish(v.getId());}
                case "retire" -> t.retire();
                default -> throw new IllegalArgumentException("Unknown writing operation");
            }
            writing.save(t);return taskView(unit);
        });
    }
    @Override public Learner learner(long unit,UserPrincipal actor){accounts.requireCurrent(actor);role(actor,true);return learnerView(unit,actor);}
    private Learner learnerView(long unit,UserPrincipal actor){
        var w=writing.work(actor.id(),unit).orElse(null);var t=writing.task(unit).orElse(null);
        // Previously started work remains readable after a round is retired or relocked.
        vn.edu.ptit.english.dto.AttemptViews.RoundProgress r=w==null?journey.find(actor.id(),unit):null;
        boolean available=w!=null||(t!=null&&t.getPublishedVersionId()!=null);
        boolean unlocked=w!=null||(available&&!r.locked()&&r.completedLessons()==r.totalLessons());
        String reason=unlocked?"":!available?"Giáo viên chưa công bố bài viết cuối vòng.":r.locked()?"Hãy hoàn thành vòng trước.":"Hoàn thành tất cả bài học trong vòng để mở bài viết.";
        var rows=w==null?List.<WritingSubmissionEntity>of():writing.history(w.getId());var grades=grades(rows);
        return new Learner(unit,available,unlocked,reason,w!=null?version(w.getVersionId()):available?version(t.getPublishedVersionId()):null,w==null?null:workView(w),rows.stream().map(s->submissionView(s,grades)).toList());
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED) public Result mutate(long unit,String operation,Command input,UserPrincipal actor){
        role(actor,true);
        return receipts.execute(actor,input.requestId(),"WRITING "+operation+" /me/units/"+unit,input,()->{
            unit(unit,true);var w=writing.work(actor.id(),unit).orElse(null);int status=200;
            if(operation.equals("start")){
                if(w==null){var view=learnerView(unit,actor);if(!view.available())throw missing();if(!view.unlocked())throw conflict("WRITING_LOCKED",view.blockReason());w=writing.save(new WritingWorkEntity(unit,actor.id(),view.version().id()));status=201;}
            }else{
                if(w==null)throw missing();revision(w.getRevision(),input.baseRevision());
                if(!Set.of("DRAFT","RETURNED").contains(w.getState()))throw conflict("WRITING_READ_ONLY","Bài đã nộp; chỉ sửa được khi giáo viên cho phép.");
                if(operation.equals("draft"))w.draft(input.text());
                else if(operation.equals("submit")){var s=writing.save(new WritingSubmissionEntity(w,input.text()));w.submit(s.getId(),input.text());}
                else throw new IllegalArgumentException("Unknown writing mutation");
                writing.save(w);
            }
            return new RequestReceiptService.Reply(status,learnerView(unit,actor));
        });
    }
    @Override public CatalogPage<QueueItem> queue(long unit,String state,int page,int size,UserPrincipal actor){
        accounts.requireCurrent(actor);teacher(unit,false,actor);
        if(!Set.of("ALL","PENDING","GRADED").contains(state)||page<0||size<1||size>50)throw vn.edu.ptit.english.dto.MaterialRequests.invalid("page","Bộ lọc không hợp lệ.");
        var rows=writing.queue(unit,state,page,size);var names=new HashMap<Long,String>();users.findAllById(rows.stream().map(WritingSubmissionEntity::getStudentId).distinct().toList()).forEach(u->names.put(u.getId(),u.record().fullName()));
        var grades=grades(rows.getContent());var titles=new HashMap<Long,String>();rows.forEach(s->titles.computeIfAbsent(s.getVersionId(),id->version(id).definition().title()));
        return CatalogPage.from(rows.map(s->new QueueItem(s.getId(),unit,names.get(s.getStudentId()),titles.get(s.getVersionId()),s.getSubmittedAt(),grades.containsKey(s.getId()))));
    }
    @Override public Grading grading(long id,UserPrincipal actor){accounts.requireCurrent(actor);role(actor,false);var s=writing.submission(vn.edu.ptit.english.dto.AuthoringRequests.id(id)).orElseThrow(WritingServiceImpl::missing);teacher(s.getUnitId(),false,actor);return gradingView(s);}
    private Grading gradingView(WritingSubmissionEntity s){var w=writing.work(s.getWorkId()).orElseThrow(WritingServiceImpl::missing);var grades=grades(List.of(s));return new Grading(s.getUnitId(),users.findById(s.getStudentId()).orElseThrow(WritingServiceImpl::missing).record().fullName(),w.getRevision(),w.getState().equals("SUBMITTED")&&Objects.equals(w.getLatestSubmissionId(),s.getId())&&!grades.containsKey(s.getId()),version(s.getVersionId()),submissionView(s,grades));}
    @Override @Transactional(isolation=Isolation.READ_COMMITTED) public Result grade(long id,Command input,UserPrincipal actor){
        role(actor,false);
        return receipts.execute(actor,input.requestId(),"WRITING grade /submissions/"+id,input,200,()->{
            governance.lock();var s=writing.submission(vn.edu.ptit.english.dto.AuthoringRequests.id(id)).orElseThrow(WritingServiceImpl::missing);teacher(s.getUnitId(),true,actor);
            var w=writing.work(s.getWorkId()).orElseThrow(WritingServiceImpl::missing);revision(w.getRevision(),input.baseRevision());
            if(!w.getState().equals("SUBMITTED")||!Objects.equals(w.getLatestSubmissionId(),s.getId())||!writing.grades(List.of(id)).isEmpty())throw conflict("WRITING_ALREADY_GRADED","Bài này đã được chấm hoặc có bản nộp mới.");
            int score=WritingData.score(version(s.getVersionId()).definition(),input.marks());
            writing.save(new WritingGradeEntity(id,actor.id(),score,json.valueToTree(input.marks()),input.comment(),input.allowRevision()));w.grade(input.allowRevision());writing.save(w);return gradingView(s);
        });
    }
}
