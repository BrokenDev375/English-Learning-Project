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
    public CatalogServiceImpl(CatalogRepository repository, RequestReceiptService receipts, AccountGuard accounts, ContentRepository content) {
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
        return CatalogPage.from(repository.units(owner(scope,true,actor),topicId,q.strip(),page,size).map(unit -> unitView(unit,actor)));
    }
    private Unit unitView(UnitEntity unit,UserPrincipal actor) {
        var base=unit.view(); var ids=new java.util.TreeSet<Long>();
        for(var lesson:content.lessons(unit.getId())) {
            boolean own=lesson.getOwnerId()==actor.id()||actor.role()==Role.ADMIN;
            var version=own&&lesson.getDraft()!=null?lesson.getDraft():lesson.getPublished();
            if(version!=null&&(own||lesson.getRetiredAt()==null))ids.addAll(version.data().objectiveIds());
        }
        return new Unit(base.id(),base.ownerId(),base.topicId(),base.title(),base.description(),base.stage(),base.sortOrder(),base.revision(),java.util.List.copyOf(ids));
    }
    @Override @Transactional public Result createTopic(TopicCreate input, UserPrincipal actor) {
        author(actor);
        return receipts.execute(actor,input.requestId(),"POST /api/author/topics",input,201,
            () -> repository.save(new TopicEntity(actor.id(),input.code(),input.name(),input.description())).view());
    }
    @Override @Transactional public Result updateTopic(long id, TopicUpdate input, UserPrincipal actor) {
        author(actor); id(id);
        return receipts.execute(actor,input.requestId(),"PUT /api/author/topics/"+id,input,200,() -> {
            var item = repository.topic(id,true).orElseThrow(CatalogServiceImpl::missing);
            editable(item,input.baseRevision(),actor); item.update(input.name(),input.description());
            return repository.save(item).view();
        });
    }
    @Override @Transactional public Result createObjective(ObjectiveCreate input, UserPrincipal actor) {
        author(actor);
        return receipts.execute(actor,input.requestId(),"POST /api/author/objectives",input,201,() -> {
            topicExists(input.topicId());
            return repository.save(new ObjectiveEntity(actor.id(),input.topicId(),input.code(),input.name(),input.description())).view();
        });
    }
    @Override @Transactional public Result updateObjective(long id, ObjectiveUpdate input, UserPrincipal actor) {
        author(actor); id(id);
        return receipts.execute(actor,input.requestId(),"PUT /api/author/objectives/"+id,input,200,() -> {
            var item = repository.objective(id,true).orElseThrow(CatalogServiceImpl::missing);
            editable(item,input.baseRevision(),actor); item.update(input.name(),input.description());
            return repository.save(item).view();
        });
    }
    @Override @Transactional public Result createUnit(UnitCreate input, UserPrincipal actor) {
        author(actor);
        return receipts.execute(actor,input.requestId(),"POST /api/author/units",input,201,() -> {
            topicExists(input.topicId());
            return repository.save(new UnitEntity(actor.id(),input.topicId(),input.title(),input.description(),input.stage(),input.sortOrder())).view();
        });
    }
    @Override @Transactional public Result updateUnit(long id, UnitUpdate input, UserPrincipal actor) {
        author(actor); id(id);
        return receipts.execute(actor,input.requestId(),"PUT /api/author/units/"+id,input,200,() -> {
            var item = repository.unit(id,true).orElseThrow(CatalogServiceImpl::missing);
            editable(item,input.baseRevision(),actor); topicExists(input.topicId());
            if (item.getTopicId() != input.topicId() && repository.hasLessons(id))
                throw new ApiException(HttpStatus.CONFLICT,"CONTENT_IN_USE","Unit đã có bài học nên không thể đổi chủ đề.");
            item.update(input.topicId(),input.title(),input.description(),input.stage(),input.sortOrder());
            return unitView(repository.save(item),actor);
        });
    }
}
