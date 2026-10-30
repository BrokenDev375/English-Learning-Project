package vn.edu.ptit.english.service.impl;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.time.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.dto.ContestData.Command;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.*;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.*;
import vn.edu.ptit.english.service.RequestReceiptService.*;
import vn.edu.ptit.english.socket.ContestChanged;
import static vn.edu.ptit.english.dto.ContestData.map;
import static vn.edu.ptit.english.dto.MaterialRequests.invalid;

@Service
public class ContestServiceImpl implements ContestService {
    private final ContestRepository repository;
    private final ContestPaperService papers;
    private final UserRepository users;
    private final AccountGuard accounts;
    private final RequestReceiptService receipts;
    private final QuestionValidator questionValidator;
    private final AnswerValidator answerValidator;
    private final AnswerGrader grader;
    private final ObjectMapper json;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    public ContestServiceImpl(ContestRepository repository,UserRepository users,AccountGuard accounts,RequestReceiptService receipts,
        QuestionValidator questionValidator,AnswerValidator answerValidator,AnswerGrader grader,ObjectMapper json,Clock clock,ApplicationEventPublisher events,ContestPaperService papers){
        this.repository=repository;this.users=users;this.accounts=accounts;this.receipts=receipts;this.questionValidator=questionValidator;
        this.answerValidator=answerValidator;this.grader=grader;this.json=json;this.clock=clock;this.events=events;this.papers=papers;
    }
    private ApiException missing(){return new ApiException(HttpStatus.NOT_FOUND,"CONTEST_NOT_FOUND","Không tìm thấy cuộc thi hoặc nội dung được phép truy cập.");}
    private ApiException conflict(String code,String message){return new ApiException(HttpStatus.CONFLICT,code,message);}
    private void admin(UserPrincipal actor){if(actor.role()!=Role.ADMIN)throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Chỉ quản trị viên được thực hiện thao tác này.");}
    private void student(UserPrincipal actor){if(actor.role()!=Role.STUDENT)throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Chỉ học viên được tham gia thi.");}
    private ContestEntity contest(long id,boolean lock){AuthoringRequests.id(id);return repository.contest(id,lock).orElseThrow(this::missing);}
    private boolean proctor(ContestEntity c,UserPrincipal actor){if(actor.role()==Role.ADMIN)return true;if(actor.role()!=Role.TEACHER||!papers.canProctor(actor.id()))return false;for(var id:c.proctorIds())if(id.asLong()==actor.id())return true;return false;}
    private void authorAccess(ContestEntity c,UserPrincipal actor){if(!proctor(c,actor))throw missing();}
    private void revision(long actual,Long expected){if(expected==null||expected!=actual)throw conflict("CONTEST_REVISION_CONFLICT","Dữ liệu đã thay đổi. Hãy tải lại và đối chiếu trước khi gửi tiếp.");}
    private String phase(ContestEntity c,Instant now){if(c.state().equals("CLOSED"))return "CLOSING";if(Set.of("PUBLISHED","RUNNING").contains(c.state())){if(!now.isBefore(c.endsAt()))return "CLOSING";return now.isBefore(c.startsAt())?"PUBLISHED":"RUNNING";}return c.state();}
    private boolean finished(ContestEntity c){return Set.of("FINALIZED","ARCHIVED").contains(c.state())&&c.results()!=null;}
    private Instant deadline(ContestEntity c,ContestParticipantEntity p){var at=p.effectiveDeadline()==null?c.endsAt():p.effectiveDeadline();return c.closedAt()!=null&&c.closedAt().isBefore(at)?c.closedAt():at;}
    private boolean open(ContestEntity c,ContestParticipantEntity p,Instant now){return p.status().equals("APPROVED")&&p.checkedInAt()!=null&&p.submissionId()==null&&phase(c,now).equals("RUNNING")&&now.isBefore(deadline(c,p));}
    private void signal(ContestEntity c){events.publishEvent(new ContestChanged(c.id()));}
    private void signal(ContestEntity c,ContestParticipantEntity p){events.publishEvent(new ContestChanged(c.id(),p.userId()));}
    private void audit(ContestEntity c,ContestParticipantEntity p,Long actor,String type,String reason,Object details){repository.save(new ContestEventEntity(c.id(),p==null?null:p.id(),actor,type,reason,json.valueToTree(details),clock.instant()));}
    private Map<String,Object> paged(List<?> items,long total,int page,int size){return map("items",items,"totalElements",total,"page",page,"size",size,"totalPages",(total+size-1)/size);}
    private int offset(int page,int size){ContestData.page(page,size);long value=(long)page*size;if(value>Integer.MAX_VALUE)throw invalid("page","Trang quá lớn.");return (int)value;}
    private void validateProctors(List<Long> ids){for(long id:ids){var user=users.findById(id).orElseThrow(this::missing);if(user.role()!=Role.TEACHER||user.status()!=AccountStatus.ACTIVE||!papers.canProctor(id))throw invalid("proctorIds","Giám thị phải là giáo viên đang hoạt động và đã được cấp quyền giám sát.");}}
    private Map<String,Object> summary(ContestEntity c,UserPrincipal actor){var now=clock.instant();var p=actor.role()==Role.STUDENT?repository.participant(c.id(),actor.id(),false).orElse(null):null;return map("id",c.id(),"title",c.title(),"description",c.description(),"state",phase(c,now),"revision",c.revision(),"registrationClosesAt",c.registrationClosesAt(),"startsAt",c.startsAt(),"endsAt",c.endsAt(),"penaltyMinutes",c.penaltyMinutes(),"questionCount",(c.paper()==null?c.draft():c.paper()).size(),"participationStatus",p==null?null:p.status(),"serverTime",now);}

    @Override @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Object list(int page,int size,UserPrincipal actor){accounts.requireCurrent(actor);int from=offset(page,size);if(actor.role()==Role.TEACHER&&!papers.canProctor(actor.id()))return paged(List.of(),0,page,size);var rows=repository.contests(actor.role().name(),actor.id(),from,size);return paged(rows.stream().map(c->summary(c,actor)).toList(),repository.contestCount(actor.role().name(),actor.id()),page,size);}
    private Map<String,Object> authorView(ContestEntity c,UserPrincipal actor){var m=summary(c,actor);m.putAll(map("canManage",actor.role()==Role.ADMIN,"canEdit",actor.role()==Role.ADMIN&&c.state().equals("DRAFT"),"ownerId",c.ownerId(),"shuffleQuestions",c.shuffleQuestions(),"proctorIds",c.proctorIds(),"questions",actor.role()==Role.ADMIN?(c.paper()==null?c.draft():c.paper()):json.createArrayNode(),"paperId",c.sourcePaperId(),"paperTitle",c.sourcePaperTitle(),"paperRevision",c.sourcePaperRevision(),"closedAt",c.closedAt(),"finalizedAt",c.finalizedAt()));if(actor.role()==Role.ADMIN){var selected=new ArrayList<Map<String,Object>>();for(var id:c.proctorIds()){var u=users.findById(id.asLong()).orElse(null);selected.add(map("userId",id.asLong(),"fullName",u==null?"Giáo viên #"+id.asLong():u.fullName(),"email",u==null?"":u.email(),"eligible",u!=null&&u.role()==Role.TEACHER&&u.status()==AccountStatus.ACTIVE&&papers.canProctor(u.id())));}m.put("proctors",selected);}else m.put("proctors",List.of());return m;}
    @Override @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Object author(long id,UserPrincipal actor){accounts.requireCurrent(actor);var c=contest(id,false);authorAccess(c,actor);return authorView(c,actor);}
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public Result create(Command input,UserPrincipal actor){
        return receipts.execute(actor,input.requestId(),"POST /api/author/contests",input.payload(),201,()->{
            admin(actor);validateProctors(input.settings().proctorIds());var c=repository.save(new ContestEntity(actor.id(),input.settings(),json.valueToTree(input.settings().proctorIds()),clock.instant()));
            c.selectPaper(input.settings().paperId()==null?null:papers.ready(input.settings().paperId(),true));repository.save(c);
            audit(c,null,actor.id(),"CREATED","Tạo cuộc thi",Map.of());signal(c);return authorView(c,actor);
        });
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public Result configure(long id,String action,Command input,UserPrincipal actor){
        return receipts.execute(actor,input.requestId(),(Set.of("draft","paper").contains(action)?"PUT":"POST")+" /api/author/contests/"+id+"/"+action,input.payload(),200,()->{
            var c=contest(id,true);authorAccess(c,actor);revision(c.revision(),input.baseRevision());var now=clock.instant();
            switch(action){
                case "draft" -> {admin(actor);if(!c.state().equals("DRAFT"))throw conflict("CONTEST_PAPER_FROZEN","Đề và thiết lập đã được chốt khi công bố.");validateProctors(input.settings().proctorIds());c.configure(input.settings(),json.valueToTree(input.settings().proctorIds()));c.selectPaper(input.settings().paperId()==null?null:papers.ready(input.settings().paperId(),true));}
                case "paper" -> {admin(actor);throw conflict("CONTEST_PAPER_FROZEN","Soạn đề trong Kho bộ đề, sau đó chọn một bộ đề đã duyệt cho cuộc thi.");}
                case "publish" -> {
                    admin(actor);if(!c.state().equals("DRAFT"))throw conflict("CONTEST_PAPER_FROZEN","Cuộc thi đã được công bố.");
                    if(!now.isBefore(c.registrationClosesAt())||!now.isBefore(c.startsAt()))throw invalid("startsAt","Cần dành thời gian đăng ký và duyệt trước giờ thi.");
                    if(c.sourcePaperId()==null)throw invalid("paperId","Cần chọn một bộ đề đã duyệt trước khi công bố.");
                    c.selectPaper(papers.ready(c.sourcePaperId(),true));
                    if(c.draft().isEmpty())throw invalid("paperId","Bộ đề cần ít nhất một câu hỏi.");
                    for(var q:c.draft()){var errors=questionValidator.errors(ContestData.gradingQuestion(q));if(!errors.isEmpty())throw invalid("questions",errors.get(0).message());}
                    var ids=new ArrayList<Long>();c.proctorIds().forEach(value->ids.add(value.asLong()));validateProctors(ids);c.publish();
                }
                case "close" -> {admin(actor);if(!phase(c,now).equals("RUNNING"))throw conflict("CONTEST_NOT_RUNNING","Chỉ đóng nhận bài khi cuộc thi đang diễn ra.");c.close(now);audit(c,null,actor.id(),"CLOSED",input.reason(),Map.of());settle(c,now);}
                case "cancel" -> {admin(actor);if(!Set.of("DRAFT","PUBLISHED").contains(c.state())||!now.isBefore(c.startsAt()))throw conflict("CONTEST_CANNOT_CANCEL","Chỉ hủy cuộc thi chưa bắt đầu.");c.cancel(now);}
                case "archive" -> {admin(actor);if(!Set.of("FINALIZED","CANCELLED").contains(c.state()))throw conflict("CONTEST_NOT_FINALIZED","Cần chốt hoặc hủy cuộc thi trước khi lưu trữ.");c.archive();}
                default -> throw invalid("action","Thao tác không hợp lệ.");
            }
            repository.save(c);audit(c,null,actor.id(),"CONTEST_"+action.toUpperCase(Locale.ROOT),input.reason()==null?"Cập nhật cuộc thi":input.reason(),Map.of());signal(c);return authorView(c,actor);
        });
    }
    private Map<String,Object> participantView(ContestEntity c,ContestParticipantEntity p){var now=clock.instant();return map("id",p.id(),"userId",p.userId(),"displayName",p.displayName(),"status",p.status(),"revision",p.revision(),"controlRevision",p.controlRevision(),"requestedAt",p.requestedAt(),"checkedInAt",p.checkedInAt(),"lastSeenAt",p.lastSeenAt(),"lastSavedAt",p.lastSavedAt(),"online",p.online()&&p.lastSeenAt()!=null&&p.lastSeenAt().isAfter(now.minusSeconds(45)),"deadline",deadline(c,p),"tabHidden",p.tabHidden(),"tabLeaves",p.tabLeaves(),"flagged",p.flagged(),"penaltySeconds",p.penaltyApplied()?c.penaltyMinutes()*60:0,"answeredCount",answered(p.answers()),"questionCount",c.paper()==null?0:c.paper().size(),"submitted",p.submissionId()!=null);}
    private int answered(JsonNode answers){int count=0;var values=answers.elements();while(values.hasNext())if(AttemptDraftValidator.answered(AttemptRequests.answer(values.next(),true,"answers")))count++;return count;}
    @Override @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Object monitor(long id,String filter,int page,int size,UserPrincipal actor){
        accounts.requireCurrent(actor);var c=contest(id,false);authorAccess(c,actor);int from=offset(page,size);
        if(!Set.of("ALL","REQUESTED","ALERTS","APPROVED","REVOKED","SUBMITTED").contains(filter))throw invalid("filter","Bộ lọc không hợp lệ.");
        var all=repository.participants(id);var filtered=all.stream().filter(p->switch(filter){case "ALERTS"->p.flagged()&&p.status().equals("APPROVED");case "SUBMITTED"->p.submissionId()!=null;case "ALL"->true;default->p.status().equals(filter);}).sorted(Comparator.comparing((ContestParticipantEntity p)->!p.flagged()).thenComparing(ContestParticipantEntity::requestedAt).thenComparingLong(ContestParticipantEntity::id)).toList();
        var rows=from>=filtered.size()?List.of():filtered.subList(from,Math.min(from+size,filtered.size())).stream().map(p->participantView(c,p)).toList();
        var stats=map("total",all.size(),"requested",all.stream().filter(p->p.status().equals("REQUESTED")).count(),"approved",all.stream().filter(p->p.status().equals("APPROVED")).count(),"online",all.stream().filter(p->p.online()&&p.lastSeenAt()!=null&&p.lastSeenAt().isAfter(clock.instant().minusSeconds(45))).count(),"submitted",all.stream().filter(p->p.submissionId()!=null).count(),"alerts",all.stream().filter(p->p.flagged()&&p.status().equals("APPROVED")).count(),"revoked",all.stream().filter(p->p.status().equals("REVOKED")).count());
        return map("contest",summary(c,actor),"canManage",actor.role()==Role.ADMIN,"statistics",stats,"participants",paged(rows,filtered.size(),page,size),"serverTime",clock.instant());
    }
    @Override @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Object events(long id,Long participantId,int page,int size,UserPrincipal actor){accounts.requireCurrent(actor);var c=contest(id,false);authorAccess(c,actor);int from=offset(page,size);if(participantId!=null&&repository.participantById(participantId,false).filter(p->p.contestId()==id).isEmpty())throw missing();return paged(repository.events(id,participantId,from,size).stream().map(ContestEventEntity::view).toList(),repository.eventCount(id,participantId),page,size);}
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public Result decide(long id,long participantId,String action,Command input,UserPrincipal actor){
        return receipts.execute(actor,input.requestId(),"POST /api/author/contests/"+id+"/participants/"+participantId+"/"+action,input.payload(),200,()->{
            var c=contest(id,true);authorAccess(c,actor);if(!action.equals("acknowledge"))admin(actor);
            var p=repository.participantById(participantId,true).filter(value->value.contestId()==id).orElseThrow(this::missing);revision(p.controlRevision(),input.baseRevision());var now=clock.instant();
            if(action.equals("approve")||action.equals("reject")){
                if(!c.state().equals("PUBLISHED")||!now.isBefore(c.startsAt())||!p.status().equals("REQUESTED"))throw conflict("CONTEST_REGISTRATION_CLOSED","Không còn được xử lý đăng ký này.");
                if(action.equals("approve")){var user=users.findById(p.userId()).orElseThrow(this::missing);if(user.role()!=Role.STUDENT||user.status()!=AccountStatus.ACTIVE)throw conflict("CONTEST_INELIGIBLE","Tài khoản không còn đủ điều kiện dự thi.");p.approve(now,c.endsAt());}else p.reject(now);
            }else {
                if(!phase(c,now).equals("RUNNING")||!p.status().equals("APPROVED"))throw conflict("CONTEST_NOT_RUNNING","Thí sinh không còn ở trạng thái được xử lý trong cuộc thi đang diễn ra.");
                if(action.equals("revoke")){p.revoke(now);if(p.checkedInAt()!=null)finish(c,p,"REVOKED",now,now);}
                else if(action.equals("acknowledge"))p.acknowledge();else throw invalid("action","Thao tác không hợp lệ.");
            }
            repository.save(p);audit(c,p,actor.id(),"PARTICIPANT_"+action.toUpperCase(Locale.ROOT),input.reason()==null?"Duyệt đăng ký dự thi":input.reason(),map("tabLeaves",p.tabLeaves()));signal(c,p);return participantView(c,p);
        });
    }

    private Map<String,Object> learnerView(ContestEntity c,ContestParticipantEntity p,UserPrincipal actor){
        var now=clock.instant();var m=map("contest",summary(c,actor),"serverTime",now,"participation",p==null?null:participantView(c,p),"canRegister",p==null&&c.state().equals("PUBLISHED")&&now.isBefore(c.registrationClosesAt()),"paper",null,"answers",null,"result",null);
        if(p!=null){
            boolean available=open(c,p,now);
            m.put("canCheckIn",p.status().equals("APPROVED")&&c.state().equals("PUBLISHED")&&now.isBefore(c.startsAt()));
            m.put("canResume",p.status().equals("APPROVED")&&p.checkedInAt()!=null&&p.submissionId()==null&&phase(c,now).equals("RUNNING")&&now.isBefore(deadline(c,p)));
            if(available){m.put("paper",publicPaper(c,p));m.put("answers",p.answers());}
            if(finished(c)&&p.status().equals("APPROVED")&&p.submissionId()!=null)m.put("result",repository.submission(p.submissionId()).orElseThrow(this::missing).result());
        }
        return m;
    }
    @Override @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Object learner(long id,UserPrincipal actor){student(actor);accounts.requireCurrent(actor);var c=contest(id,false);if(c.state().equals("DRAFT"))throw missing();return learnerView(c,repository.participant(id,actor.id(),false).orElse(null),actor);}
    private void writer(ContestParticipantEntity p,String client){if(!Objects.equals(client,p.writerId()))throw conflict("CONTEST_OTHER_TAB","Lượt thi đang thuộc một tab khác. Hãy trở lại tab đó hoặc chờ kết nối cũ hết hạn.");}
    private void claimable(ContestParticipantEntity p,String client,Instant now){if(p.writerId()!=null&&!p.writerId().equals(client)&&p.leaseUntil()!=null&&now.isBefore(p.leaseUntil()))throw conflict("CONTEST_OTHER_TAB","Lượt thi đang mở ở một tab khác.");}
    private JsonNode layout(ContestEntity c){
        var result=json.createObjectNode();var order=result.putArray("order");var choices=result.putObject("choices");var ids=new ArrayList<String>();
        for(var q:c.paper()){
            String id=q.path("id").asText();ids.add(id);String type=q.path("type").asText();String field=type.equals("MATCHING")?"rightItems":type.equals("ORDERING")?"tokens":"options";
            if(q.path("content").has(field)){var values=new ArrayList<String>();q.path("content").path(field).forEach(v->values.add(v.path("id").asText()));Collections.shuffle(values);choices.set(id,json.valueToTree(values));}
        }
        if(c.shuffleQuestions())Collections.shuffle(ids);ids.forEach(order::add);return result;
    }
    private JsonNode publicPaper(ContestEntity c,ContestParticipantEntity p){
        var result=json.createArrayNode();var byId=new HashMap<String,JsonNode>();c.paper().forEach(q->byId.put(q.path("id").asText(),q));var display=p.display();
        for(var value:display.path("order")){
            var q=byId.get(value.asText());var safe=json.createObjectNode();for(String field:List.of("id","type","prompt","context","difficulty","content"))safe.set(field,q.get(field).deepCopy());
            String field=q.path("type").asText().equals("MATCHING")?"rightItems":q.path("type").asText().equals("ORDERING")?"tokens":"options";
            if(safe.path("content").has(field)){var items=new HashMap<String,JsonNode>();safe.path("content").path(field).forEach(item->items.put(item.path("id").asText(),item));var shuffled=json.createArrayNode();display.path("choices").path(q.path("id").asText()).forEach(id->shuffled.add(items.get(id.asText())));((ObjectNode)safe.path("content")).set(field,shuffled);}
            result.add(safe);
        }
        return result;
    }
    private void validateAnswers(ContestEntity c,JsonNode answers){var questions=new HashMap<String,JsonNode>();c.paper().forEach(q->questions.put(q.path("id").asText(),q));var fields=answers.fields();while(fields.hasNext()){var entry=fields.next();var q=questions.get(entry.getKey());if(q==null)throw invalid("answers","Đáp án không thuộc đề thi đã chốt.");answerValidator.validate(ContestData.gradingQuestion(q),AttemptRequests.answer(entry.getValue(),true,"answers."+entry.getKey()));}}
    private Reply expired(ContestEntity c,ContestParticipantEntity p,Instant now){
        finish(c,p,"EXPIRED",deadline(c,p),now);repository.save(p);if(!now.isBefore(c.endsAt())&&Set.of("PUBLISHED","RUNNING").contains(c.state()))c.close(c.endsAt());if(c.state().equals("CLOSED"))settle(c,now);repository.save(c);if(finished(c))signal(c);else signal(c,p);
        return new Reply(409,map("code","CONTEST_EXPIRED","message","Đã hết thời gian. Hệ thống chốt các đáp án đã lưu trên máy chủ; thay đổi gửi sau hạn không được nhận."));
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public Result participate(long id,String action,Command input,UserPrincipal actor){
        return receipts.execute(actor,input.requestId(),"POST /api/me/contests/"+id+"/"+action,input.payload(),()->{
            student(actor);var c=contest(id,true);if(c.state().equals("DRAFT"))throw missing();var now=clock.instant();var p=repository.participant(id,actor.id(),true).orElse(null);
            if(action.equals("register")){
                if(!c.state().equals("PUBLISHED")||!now.isBefore(c.registrationClosesAt()))throw conflict("CONTEST_REGISTRATION_CLOSED","Đã hết thời gian đăng ký.");
                if(p==null){var user=users.findById(actor.id()).orElseThrow(this::missing);p=repository.save(new ContestParticipantEntity(id,actor.id(),user.fullName(),now));audit(c,p,actor.id(),"REGISTERED","Đăng ký dự thi",Map.of());signal(c,p);return new Reply(201,learnerView(c,p,actor));}
                return new Reply(200,learnerView(c,p,actor));
            }
            if(p==null)throw missing();
            if(!p.status().equals("APPROVED"))throw new ApiException(HttpStatus.FORBIDDEN,"CONTEST_NOT_APPROVED","Bạn chưa được duyệt hoặc đã bị thu hồi quyền dự thi.");
            if(action.equals("check-in")||action.equals("claim")){
                if(p.submissionId()!=null)throw conflict("CONTEST_ALREADY_SUBMITTED","Bài thi đã được chốt.");
                boolean before=now.isBefore(c.startsAt());
                if(!Set.of("PUBLISHED","RUNNING").contains(c.state()))throw conflict("CONTEST_CLOSED","Cuộc thi đã đóng.");
                if(!before&&p.checkedInAt()==null)throw conflict("CONTEST_CHECKIN_CLOSED","Cần có mặt tại phòng chờ trước giờ thi; không nhận lượt bắt đầu muộn.");
                if(!now.isBefore(deadline(c,p)))return expired(c,p,now);
                claimable(p,input.clientId(),now);
                if(before)p.checkIn(input.clientId(),p.display()==null?layout(c):p.display(),now);else p.claim(input.clientId(),now);
                repository.save(p);audit(c,p,actor.id(),before?"CHECKED_IN":"RECONNECTED",before?"Vào phòng chờ":"Kết nối lại lượt thi",Map.of());signal(c,p);return new Reply(200,learnerView(c,p,actor));
            }
            if(p.checkedInAt()==null)throw conflict("CONTEST_NOT_CHECKED_IN","Bạn chưa vào phòng chờ trước giờ thi.");
            writer(p,input.clientId());
            if(p.submissionId()!=null)throw conflict("CONTEST_ALREADY_SUBMITTED","Bài thi đã được chốt.");
            if(Set.of("FINALIZED","ARCHIVED","CANCELLED").contains(c.state()))throw conflict("CONTEST_CLOSED","Cuộc thi đã đóng.");
            if(!now.isBefore(deadline(c,p))||c.state().equals("CLOSED"))return expired(c,p,now);
            if(now.isBefore(c.startsAt()))throw conflict("CONTEST_NOT_STARTED","Cuộc thi chưa bắt đầu.");
            if(action.equals("visibility")){
                int previous=p.tabLeaves();boolean wasHidden=p.tabHidden();p.visibility(input.hidden(),c.penaltyMinutes(),now);
                if(p.tabLeaves()!=previous){audit(c,p,actor.id(),"TAB_LEFT","Rời màn thi",map("count",p.tabLeaves(),"deadline",deadline(c,p)));if(p.tabLeaves()==2)audit(c,p,null,"TIME_PENALTY","Giảm thời gian do rời màn thi lần hai",map("minutes",c.penaltyMinutes(),"deadline",deadline(c,p)));}
                else if(!input.hidden()&&wasHidden)audit(c,p,actor.id(),"TAB_RETURNED","Trở lại màn thi",Map.of());
                if(!now.isBefore(deadline(c,p)))finish(c,p,"EXPIRED",deadline(c,p),now);
            }else if(action.equals("save")||action.equals("submit")){
                revision(p.revision(),input.baseRevision());validateAnswers(c,input.answers());p.saveAnswers(input.answers(),now);
                if(action.equals("submit"))finish(c,p,"MANUAL",now,now);
            }else throw invalid("action","Thao tác không hợp lệ.");
            p.heartbeat(now);if(p.submissionId()!=null)p.offline();repository.save(p);signal(c,p);return new Reply(200,learnerView(c,p,actor));
        });
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public Object heartbeat(long id,JsonNode input,UserPrincipal actor){
        student(actor);accounts.lockCurrent(actor);AuthoringRequests.strict(input,"clientId");String client=ContestData.clientId(input);var c=contest(id,true);var p=repository.participant(id,actor.id(),true).orElseThrow(this::missing);var now=clock.instant();
        if(!p.status().equals("APPROVED")||p.checkedInAt()==null)throw new ApiException(HttpStatus.FORBIDDEN,"CONTEST_NOT_APPROVED","Bạn không có quyền tiếp tục lượt thi.");writer(p,client);
        if(p.submissionId()==null&&(!now.isBefore(deadline(c,p))||c.state().equals("CLOSED")))expired(c,p,now);
        if(p.submissionId()==null&&Set.of("PUBLISHED","RUNNING").contains(c.state())){boolean wasOnline=p.online();p.heartbeat(now);repository.save(p);if(!wasOnline)signal(c,p);}
        return learnerView(c,p,actor);
    }
    private void finish(ContestEntity c,ContestParticipantEntity p,String source,Instant effective,Instant now){
        if(p.submissionId()!=null)return;
        if(effective.isBefore(c.startsAt()))effective=c.startsAt();if(effective.isAfter(deadline(c,p)))effective=deadline(c,p).isBefore(c.startsAt())?c.startsAt():deadline(c,p);
        var items=json.createArrayNode();int correct=0;var answers=p.answers();
        for(var question:c.paper()){
            String id=question.path("id").asText();JsonNode response=answers.has(id)?answers.get(id):NullNode.instance;
            var grade=grader.grade(ContestData.gradingQuestion(question),AttemptRequests.answer(response,true,"answers."+id));if(grade.outcome()==vn.edu.ptit.english.enums.AttemptEnums.Outcome.CORRECT)correct++;
            var item=question.deepCopy();((ObjectNode)item).set("response",response.deepCopy());((ObjectNode)item).put("outcome",grade.outcome().name());((ObjectNode)item).put("responseComplete",grade.responseComplete());items.add(item);
        }
        long elapsed=Math.max(0,Duration.between(c.startsAt(),effective).toMillis());int total=c.paper().size();
        var result=map("correctCount",correct,"totalCount",total,"scorePercent",Math.round(correct*10000.0/total)/100.0,"elapsedMillis",elapsed,"submittedAt",effective,"processedAt",now,"source",source,"penaltySeconds",p.penaltyApplied()?c.penaltyMinutes()*60:0,"items",items);
        var submission=repository.save(new ContestSubmissionEntity(p,source,effective,now,json.valueToTree(result)));p.submitted(submission.id());repository.save(p);
        audit(c,p,null,"SUBMITTED","Chốt bài thi",map("source",source,"submittedAt",effective,"processedAt",now));
    }
    private void settle(ContestEntity c,Instant now){
        var all=repository.participants(c.id());
        for(var p:all)if(p.status().equals("APPROVED")&&p.checkedInAt()!=null&&p.submissionId()==null)finish(c,p,c.closedAt()!=null&&c.closedAt().isBefore(c.endsAt())?"CLOSED":"EXPIRED",deadline(c,p),now);
        var results=new ArrayList<Map<String,Object>>();
        for(var p:all)if(p.status().equals("APPROVED")&&p.submissionId()!=null){
            var user=users.findById(p.userId());if(user.isEmpty()||user.get().role()!=Role.STUDENT||user.get().status()!=AccountStatus.ACTIVE){audit(c,p,null,"RESULT_INELIGIBLE","Tài khoản không còn đủ điều kiện xếp hạng",Map.of());continue;}
            var s=repository.submission(p.submissionId()).orElseThrow(this::missing);var r=s.result();results.add(map("participantId",p.id(),"userId",p.userId(),"displayName",p.displayName(),"correctCount",r.path("correctCount").asInt(),"totalCount",r.path("totalCount").asInt(),"scorePercent",r.path("scorePercent").asDouble(),"elapsedMillis",r.path("elapsedMillis").asLong(),"penaltySeconds",r.path("penaltySeconds").asInt(),"submittedAt",s.submittedAt()));
        }
        results.sort(Comparator.<Map<String,Object>>comparingInt(row->(Integer)row.get("correctCount")).reversed().thenComparingLong(row->(Long)row.get("elapsedMillis")).thenComparingLong(row->(Long)row.get("participantId")));
        int rank=0;Integer previousScore=null;Long previousTime=null;
        for(int i=0;i<results.size();i++){var row=results.get(i);int score=(Integer)row.get("correctCount");long time=(Long)row.get("elapsedMillis");if(previousScore==null||previousScore!=score||previousTime!=time)rank=i+1;row.put("rank",rank);row.put("position",i+1);previousScore=score;previousTime=time;}
        c.finalizeResults(json.valueToTree(results),now);repository.save(c);audit(c,null,null,"FINALIZED","Chốt bảng xếp hạng",map("rankedParticipants",results.size()));
    }
    @Override @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Object board(long id,UserPrincipal actor){
        accounts.requireCurrent(actor);var c=contest(id,false);if(!proctor(c,actor)){student(actor);if(c.state().equals("DRAFT")||repository.participant(id,actor.id(),false).isEmpty())throw missing();}
        var rows=new ArrayList<Map<String,Object>>();if(finished(c))for(var row:c.results()){var value=new LinkedHashMap<String,Object>();row.fields().forEachRemaining(entry->{if(!Set.of("userId","participantId").contains(entry.getKey()))value.put(entry.getKey(),entry.getValue());});value.put("isSelf",row.path("userId").asLong()==actor.id());rows.add(value);}
        return map("contest",summary(c,actor),"finalized",finished(c),"items",rows,"finalizedAt",c.finalizedAt(),"serverTime",clock.instant());
    }
    @Override @Transactional(readOnly=true)
    public boolean canSubscribe(long id,UserPrincipal actor){accounts.requireCurrent(actor);return repository.contest(id,false).map(c->proctor(c,actor)||(actor.role()==Role.STUDENT&&!c.state().equals("DRAFT"))).orElse(false);}
    @Override @Transactional(readOnly=true) public List<Long> activeIds(){return repository.activeIds();}
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public void tick(long id){
        var c=repository.contest(id,true).orElse(null);if(c==null)return;var now=clock.instant();boolean changed=false;var participantChanges=new HashSet<Long>();
        if(c.state().equals("PUBLISHED")&&!now.isBefore(c.startsAt())){c.start();changed=true;audit(c,null,null,"STARTED","Đến giờ bắt đầu thi",Map.of());}
        if(c.state().equals("RUNNING")&&!now.isBefore(c.endsAt())){c.close(c.endsAt());changed=true;audit(c,null,null,"CLOSED","Hết giờ thi",Map.of());}
        if(c.state().equals("CLOSED")){settle(c,now);changed=true;}
        else if(Set.of("PUBLISHED","RUNNING").contains(c.state()))for(var p:repository.participants(id)){
            if(p.online()&&p.lastSeenAt()!=null&&!p.lastSeenAt().isAfter(now.minusSeconds(45))){p.offline();repository.save(p);participantChanges.add(p.userId());}
            if(p.status().equals("APPROVED")&&p.checkedInAt()!=null&&p.submissionId()==null&&!now.isBefore(deadline(c,p))){finish(c,p,"EXPIRED",deadline(c,p),now);participantChanges.add(p.userId());}
        }
        if(changed){repository.save(c);signal(c);}else for(long user:participantChanges)events.publishEvent(new ContestChanged(c.id(),user));
    }
}
