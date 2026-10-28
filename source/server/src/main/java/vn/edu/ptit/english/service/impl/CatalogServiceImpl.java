package vn.edu.ptit.english.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.english.dto.CatalogRequests.*;
import vn.edu.ptit.english.dto.CatalogViews.*;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.CatalogRepository;
import vn.edu.ptit.english.repository.ContentRepository;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.*;
import vn.edu.ptit.english.service.RequestReceiptService.Result;

@Service @Transactional(readOnly = true)
public class CatalogServiceImpl implements CatalogService {
    private final CatalogRepository repository;
    private final RequestReceiptService receipts;
    private final AccountGuard accounts;
    private final ContentRepository content;
    private final ContentGovernance governance;
    private final vn.edu.ptit.english.repository.UserRepository users;
    private final vn.edu.ptit.english.repository.UserJpaRepository userEntities;
    public CatalogServiceImpl(CatalogRepository repository, RequestReceiptService receipts, AccountGuard accounts, ContentRepository content, ContentGovernance governance, vn.edu.ptit.english.repository.UserRepository users, vn.edu.ptit.english.repository.UserJpaRepository userEntities) {
        this.governance=governance;this.users=users;this.userEntities=userEntities;
        this.repository = repository; this.receipts = receipts; this.accounts = accounts; this.content = content;
    }
    private void author(UserPrincipal actor) {
        if (actor.role() != Role.TEACHER && actor.role() != Role.ADMIN)
            throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Chỉ người biên soạn và quản trị viên được quản lý học liệu.");
    }
    private Long owner(String scope, boolean unit, UserPrincipal actor) {
        if (scope.equals("OWN")) return actor.id();
        if (!scope.equals("ALL")) throw invalid("Phạm vi danh sách không hợp lệ.");
        if (unit && actor.role() != Role.ADMIN)
            throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Chỉ quản trị viên được xem Unit của mọi tác giả.");
        return null;
    }
    private void query(String q, int page, int size, Long topicId) {
        if (q.length() > 100 || page < 0 || size < 1 || size > 50) throw invalid("Từ khóa tối đa 100 ký tự; trang từ 0 và kích thước từ 1–50.");
        if (topicId != null) id(topicId);
    }
    private static void id(long id) {
        if (id < 1 || id > 9007199254740991L) throw invalid("Mã tài nguyên không hợp lệ.");
    }
    private void editable(CatalogEntity item, long revision, UserPrincipal actor) {
        if (actor.role() != Role.ADMIN && item.getOwnerId() != actor.id()) throw missing();
        if (item.getRevision() != revision)
            throw new ApiException(HttpStatus.CONFLICT,"REVISION_CONFLICT","Nội dung đã thay đổi ở nơi khác. Hãy tải lại trước khi sửa.");
    }
    private void topicExists(long id) { repository.topic(id,false).orElseThrow(CatalogServiceImpl::missing); }
    private static ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","Không tìm thấy nội dung được phép truy cập."); }
    private static ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED",message); }
    @Override public CatalogPage<Topic> topics(String q, String scope, int page, int size, UserPrincipal actor) {
        accounts.requireCurrent(actor); author(actor); query(q,page,size,null);
        return CatalogPage.from(repository.topics(owner(scope,false,actor),q.strip(),page,size).map(TopicEntity::view));
    }
    @Override public CatalogPage<Objective> objectives(String q, String scope, Long topicId, int page, int size, UserPrincipal actor) {
        accounts.requireCurrent(actor); author(actor); query(q,page,size,topicId);
        return CatalogPage.from(repository.objectives(owner(scope,false,actor),topicId,q.strip(),page,size).map(ObjectiveEntity::view));
    }
    @Override public CatalogPage<Unit> units(String q, String scope, Long topicId, int page, int size, UserPrincipal actor) {
        accounts.requireCurrent(actor); author(actor); query(q,page,size,topicId);
        return CatalogPage.from(repository.units(owner(scope,true,actor),topicId,q.strip(),page,size,actor.role()!=Role.ADMIN).map(unit -> unitView(unit,actor)));
    }
    private Unit unitView(UnitEntity unit,UserPrincipal actor) {
        var base=unit.view(); var ids=new java.util.TreeSet<Long>();
        for(var lesson:content.lessons(unit.getId())) {
            boolean own=governance.manages(lesson,actor);
            var version=own&&lesson.getDraft()!=null?lesson.getDraft():lesson.getPublished();
            if(version!=null&&(own||lesson.getRetiredAt()==null))ids.addAll(version.data().objectiveIds());
        }
        return new Unit(base.id(),base.ownerId(),base.topicId(),base.title(),base.description(),base.stage(),base.sortOrder(),base.roundNumber(),base.assignedTeacherId(),base.revision(),java.util.List.copyOf(ids));
    }
    @Override @Transactional public Result createTopic(TopicCreate input, UserPrincipal actor) {
        author(actor);
        return receipts.execute(actor,input.requestId(),"POST /api/author/topics",input,201,
            () -> {ContentGovernance.admin(actor);return repository.save(new TopicEntity(actor.id(),input.code(),input.name(),input.description())).view();});
    }
    @Override @Transactional public Result updateTopic(long id, TopicUpdate input, UserPrincipal actor) {
        author(actor); id(id);
        return receipts.execute(actor,input.requestId(),"PUT /api/author/topics/"+id,input,200,() -> {
            ContentGovernance.admin(actor);
            var item = repository.topic(id,true).orElseThrow(CatalogServiceImpl::missing);
            editable(item,input.baseRevision(),actor); item.update(input.name(),input.description());
            return repository.save(item).view();
        });
    }
    @Override @Transactional public Result createObjective(ObjectiveCreate input, UserPrincipal actor) {
        author(actor);
        return receipts.execute(actor,input.requestId(),"POST /api/author/objectives",input,201,() -> {
            ContentGovernance.admin(actor);topicExists(input.topicId());
            return repository.save(new ObjectiveEntity(actor.id(),input.topicId(),input.code(),input.name(),input.description())).view();
        });
    }
    @Override @Transactional public Result updateObjective(long id, ObjectiveUpdate input, UserPrincipal actor) {
        author(actor); id(id);
        return receipts.execute(actor,input.requestId(),"PUT /api/author/objectives/"+id,input,200,() -> {
            ContentGovernance.admin(actor);
            var item = repository.objective(id,true).orElseThrow(CatalogServiceImpl::missing);
            editable(item,input.baseRevision(),actor); item.update(input.name(),input.description());
            return repository.save(item).view();
        });
    }
    public CatalogPage<java.util.Map<String,Object>> teachers(int page,int size,UserPrincipal actor){
        accounts.requireCurrent(actor);ContentGovernance.admin(actor);query("",page,size,null);
        return CatalogPage.from(userEntities.search("%",Role.TEACHER,vn.edu.ptit.english.enums.AccountStatus.ACTIVE,org.springframework.data.domain.PageRequest.of(page,size,org.springframework.data.domain.Sort.by("fullName","id")))
            .map(u->java.util.Map.<String,Object>of("id",u.getId(),"fullName",u.getFullName())));
    }
    private Long assignee(Long teacher,Boolean clear,Long current){
        if(Boolean.TRUE.equals(clear)&&teacher!=null)throw invalid("Chọn giáo viên hoặc bỏ phân công, không gửi đồng thời cả hai.");
        if(Boolean.TRUE.equals(clear))return null;
        if(teacher==null)return current;
        var user=users.findById(teacher).orElseThrow(CatalogServiceImpl::missing);
        if(user.role()!=Role.TEACHER||user.status()!=vn.edu.ptit.english.enums.AccountStatus.ACTIVE)throw invalid("Cần chọn tài khoản giáo viên đang hoạt động.");
        return teacher;
    }
    private int number(Integer requested, long exceptId) {
        long number = requested == null ? (long) repository.maxRoundNumber()+1 : requested;
        if (number > Integer.MAX_VALUE) throw invalid("Đã hết số thứ tự vòng hợp lệ.");
        if (repository.roundNumberUsed((int)number,exceptId))
            throw new ApiException(HttpStatus.CONFLICT,"ROUND_NUMBER_CONFLICT","Số thứ tự vòng đã được sử dụng. Hãy chọn số khác.");
        return (int) number;
    }
    @Override @Transactional public Result createUnit(UnitCreate input, UserPrincipal actor) {
        author(actor);
        return receipts.execute(actor,input.requestId(),"POST /api/author/units",input,201,() -> {
            governance.lock();ContentGovernance.admin(actor);topicExists(input.topicId());
            Long teacher=assignee(input.assignedTeacherId(),input.clearAssignment(),null);
            return repository.save(new UnitEntity(actor.id(),input.topicId(),input.title(),input.description(),input.stage(),input.sortOrder(),number(input.roundNumber(),0),teacher)).view();
        });
    }
    @Override @Transactional public Result updateUnit(long id, UnitUpdate input, UserPrincipal actor) {
        author(actor); id(id);
        return receipts.execute(actor,input.requestId(),"PUT /api/author/units/"+id,input,200,() -> {
            governance.lock();ContentGovernance.admin(actor);
            var item = repository.unit(id,true).orElseThrow(CatalogServiceImpl::missing);
            editable(item,input.baseRevision(),actor); topicExists(input.topicId());
            if (item.getTopicId() != input.topicId() && repository.hasLessons(id))
                throw new ApiException(HttpStatus.CONFLICT,"CONTENT_IN_USE","Unit đã có bài học nên không thể đổi chủ đề.");
            item.update(input.topicId(),input.title(),input.description(),input.stage(),input.sortOrder(),input.roundNumber()==null?item.getRoundNumber():number(input.roundNumber(),id));
            item.assign(assignee(input.assignedTeacherId(),input.clearAssignment(),item.getAssignedTeacherId()));
            return unitView(repository.save(item),actor);
        });
    }
}
