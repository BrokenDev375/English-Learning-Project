package vn.edu.ptit.english.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.dto.ContestPaperData.Command;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.*;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.*;
import vn.edu.ptit.english.service.RequestReceiptService.Result;
import vn.edu.ptit.english.socket.ContestPermissionsChanged;
import static vn.edu.ptit.english.dto.ContestData.map;
import static vn.edu.ptit.english.dto.MaterialRequests.invalid;

@Service
public class ContestPaperServiceImpl implements ContestPaperService {
    private final ContestPaperRepository repository;
    private final UserRepository users;
    private final AccountGuard accounts;
    private final RequestReceiptService receipts;
    private final QuestionValidator validator;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    public ContestPaperServiceImpl(ContestPaperRepository repository,UserRepository users,AccountGuard accounts,RequestReceiptService receipts,QuestionValidator validator,Clock clock,ApplicationEventPublisher events){this.repository=repository;this.users=users;this.accounts=accounts;this.receipts=receipts;this.validator=validator;this.clock=clock;this.events=events;}
    private ApiException missing(){return new ApiException(HttpStatus.NOT_FOUND,"CONTEST_PAPER_NOT_FOUND","Không tìm thấy bộ đề được phép truy cập.");}
    private ApiException conflict(String message){return new ApiException(HttpStatus.CONFLICT,"CONTEST_PAPER_CONFLICT",message);}
    private void admin(UserPrincipal actor){if(actor.role()!=Role.ADMIN)throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Chỉ admin được phân quyền và duyệt bộ đề.");}
    private void author(UserPrincipal actor,boolean lock){if(actor.role()==Role.ADMIN)return;if(actor.role()!=Role.TEACHER||!repository.permission(actor.id(),lock).map(ContestPermissionEntity::canAuthor).orElse(false))throw new ApiException(HttpStatus.FORBIDDEN,"CONTEST_AUTHOR_DENIED","Bạn chưa được cấp quyền ra đề cuộc thi.");}
    private void revision(long actual,Long expected){if(expected==null||actual!=expected)throw conflict("Bộ đề hoặc quyền đã thay đổi. Tải lại và đối chiếu trước khi lưu.");}
    private ContestPaperEntity paper(long id,boolean lock){AuthoringRequests.id(id);return repository.paper(id,lock).orElseThrow(this::missing);}
    private void own(ContestPaperEntity paper,UserPrincipal actor){if(actor.role()!=Role.ADMIN&&paper.ownerId()!=actor.id())throw missing();}
    private int offset(int page,int size){ContestData.page(page,size);long offset=(long)page*size;if(offset>Integer.MAX_VALUE)throw invalid("page","Trang quá lớn.");return(int)offset;}
    private Object page(List<?> rows,long total,int page,int size){return map("items",rows,"totalElements",total,"page",page,"size",size,"totalPages",(total+size-1)/size);}
    private void valid(JsonNode questions){if(questions.isEmpty())throw invalid("questions","Cần ít nhất một câu trước khi gửi duyệt.");for(var q:questions){var errors=validator.errors(ContestData.gradingQuestion(q));if(!errors.isEmpty())throw invalid("questions",errors.get(0).message());}}
    private Map<String,Object> view(ContestPaperEntity p,UserPrincipal actor,boolean detail){
        var value=map("id",p.id(),"ownerId",p.ownerId(),"ownerName",users.findById(p.ownerId()).map(UserRecord::fullName).orElse("Giáo viên"),"revision",p.revision(),"title",p.title(),"description",p.description(),"state",p.state(),"questionCount",p.questions().size(),"usageCount",repository.usage(p.id()),"reviewNote",p.reviewNote(),"reviewedBy",p.reviewedBy(),"reviewedAt",p.reviewedAt(),"updatedAt",p.updatedAt(),"canEdit",p.state().equals("DRAFT"),"canReview",actor.role()==Role.ADMIN&&p.state().equals("PENDING"));
        if(detail)value.put("questions",p.questions());return value;
    }
    @Override @Transactional(readOnly=true)
    public Object capabilities(UserPrincipal actor){accounts.requireCurrent(actor);boolean admin=actor.role()==Role.ADMIN;var p=actor.role()==Role.TEACHER?repository.permission(actor.id(),false).orElse(null):null;return map("canAuthor",admin||(p!=null&&p.canAuthor()),"canProctor",admin||(p!=null&&p.canProctor()),"canManage",admin);}
    @Override @Transactional(readOnly=true)
    public boolean canProctor(long user){return repository.permission(user,false).map(ContestPermissionEntity::canProctor).orElse(false);}
    @Override @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Object permissions(String query,boolean proctorOnly,int page,int size,UserPrincipal actor){
        accounts.requireCurrent(actor);admin(actor);int from=offset(page,size);String search=search(query);
        var rows=repository.teachers(search,proctorOnly,from,size).stream().map(r->map("userId",((Number)r[0]).longValue(),"fullName",r[1],"email",r[2],"canAuthor",bit(r[3]),"canProctor",bit(r[4]),"revision",((Number)r[5]).longValue())).toList();return page(rows,repository.teacherCount(search,proctorOnly),page,size);
    }
    private String search(String value){if(value==null||value.length()>160)throw invalid("q","Từ khóa tìm kiếm tối đa 160 ký tự.");return value.strip();}
    private boolean bit(Object value){return value instanceof Boolean flag?flag:((Number)value).intValue()!=0;}
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public Result grant(long user,Command input,UserPrincipal actor){
        return receipts.execute(actor,input.requestId(),"PUT /api/author/contest-permissions/"+user,input.payload(),200,()->{
            admin(actor);AuthoringRequests.id(user);var teacher=users.findById(user).orElseThrow(this::missing);if(teacher.role()!=Role.TEACHER||teacher.status()!=AccountStatus.ACTIVE)throw invalid("userId","Chỉ phân quyền cho giáo viên đang hoạt động.");
            var p=repository.permission(user,true).orElseGet(()->new ContestPermissionEntity(user));revision(p.revision(),input.baseRevision());p.update(input.canAuthor(),input.canProctor(),actor.id(),clock.instant());repository.save(p);events.publishEvent(new ContestPermissionsChanged(user,p.canProctor()));return map("userId",user,"fullName",teacher.fullName(),"email",teacher.email(),"canAuthor",p.canAuthor(),"canProctor",p.canProctor(),"revision",p.revision());
        });
    }
    @Override @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Object list(String state,String query,int page,int size,UserPrincipal actor){accounts.requireCurrent(actor);author(actor,false);if(!Set.of("ALL","DRAFT","PENDING","READY","ARCHIVED").contains(state))throw invalid("state","Trạng thái bộ đề không hợp lệ.");int from=offset(page,size);String search=search(query);Long owner=actor.role()==Role.ADMIN?null:actor.id();return page(repository.papers(owner,state,search,from,size).stream().map(p->view(p,actor,false)).toList(),repository.paperCount(owner,state,search),page,size);}
    @Override @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Object detail(long id,UserPrincipal actor){accounts.requireCurrent(actor);author(actor,false);var p=paper(id,false);own(p,actor);return view(p,actor,true);}
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public Result create(Command input,UserPrincipal actor){accounts.lockCurrent(actor);author(actor,true);return receipts.execute(actor,input.requestId(),"POST /api/author/contest-papers",input.payload(),201,()->{var p=repository.save(new ContestPaperEntity(actor.id(),input.title(),input.description(),input.questions(),clock.instant()));return view(p,actor,true);});}
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public Result change(long id,String action,Command input,UserPrincipal actor){
        accounts.lockCurrent(actor);if(Set.of("approve","return").contains(action))admin(actor);else author(actor,true);
        return receipts.execute(actor,input.requestId(),(action.equals("draft")?"PUT":"POST")+" /api/author/contest-papers/"+id+"/"+action,input.payload(),action.equals("clone")?201:200,()->{
            if(Set.of("approve","return").contains(action))admin(actor);else author(actor,true);var p=paper(id,true);own(p,actor);revision(p.revision(),input.baseRevision());var now=clock.instant();
            switch(action){
                case "draft"->{if(!p.state().equals("DRAFT"))throw conflict("Chỉ sửa bộ đề nháp. Hãy nhân bản để tạo phiên bản khác.");p.edit(input.title(),input.description(),input.questions(),now);}
                case "submit"->{if(!p.state().equals("DRAFT"))throw conflict("Bộ đề không còn ở trạng thái nháp.");valid(p.questions());p.submit(now);}
                case "approve","return"->{if(!p.state().equals("PENDING"))throw conflict("Bộ đề không còn chờ duyệt.");if(action.equals("approve"))valid(p.questions());p.review(action.equals("approve"),input.note()==null?"":input.note(),actor.id(),now);}
                case "archive"->{if(!Set.of("DRAFT","READY").contains(p.state()))throw conflict("Chỉ lưu trữ bộ đề nháp hoặc đã duyệt.");p.archive(now);}
                case "clone"->{var copy=p.questions();copy.forEach(q->((ObjectNode)q).put("id",UUID.randomUUID().toString()));String title=p.title();title=title.substring(0,Math.min(148,title.length()))+" (bản sao)";return view(repository.save(new ContestPaperEntity(actor.id(),title,p.description(),copy,now)),actor,true);}
                default->throw invalid("action","Thao tác bộ đề không hợp lệ.");
            }
            repository.save(p);return view(p,actor,true);
        });
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public ContestPaperEntity ready(long id,boolean lock){var p=paper(id,lock);if(!p.state().equals("READY"))throw conflict("Chọn một bộ đề đã được admin duyệt và chưa lưu trữ.");return p;}
}
