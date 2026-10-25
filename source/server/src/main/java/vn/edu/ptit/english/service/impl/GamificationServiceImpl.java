package vn.edu.ptit.english.service.impl;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.ptit.english.dto.AttemptViews.Result;
import vn.edu.ptit.english.dto.AttemptViews.ResultItem;
import vn.edu.ptit.english.dto.GamificationViews.*;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.enums.AttemptEnums.Mode;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.*;
import static vn.edu.ptit.english.service.impl.GamificationRules.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class GamificationServiceImpl implements GamificationService {
    private final GamificationRepository repository;
    private final GamificationContextRepository contexts;
    private final AccountGuard accounts;
    private final RequestReceiptService receipts;
    private final Clock clock;
    private final LeagueService leagues;
    public GamificationServiceImpl(GamificationRepository repository,GamificationContextRepository contexts,
        AccountGuard accounts,RequestReceiptService receipts,Clock clock,LeagueService leagues) {
        this.repository=repository; this.contexts=contexts; this.accounts=accounts; this.receipts=receipts; this.clock=clock;this.leagues=leagues;
    }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private void student(UserPrincipal actor) {
        if(actor.role()!=Role.STUDENT) throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Chỉ học viên có phần thưởng học tập.");
    }
    private GamificationPolicyEntity policy() {
        var policy=contexts.policy(); var json=policy.getPolicy();
        if(!VERSION.equals(policy.getVersion())||json.path("freshQuestionXp").asInt()!=REWARD||json.path("dueObjectiveXp").asInt()!=REWARD
            ||json.path("dailyXpCap").asInt()!=CAP||json.path("xpPerActivityLevel").asInt()!=100||!ZONE.getId().equals(json.path("zone").asText()))
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"GAMIFICATION_SCHEMA_UNSUPPORTED","Bộ luật phần thưởng chưa được hỗ trợ.");
        return policy;
    }
    private Streak streak(long userId,LocalDate today) {
        return GamificationRules.streak(repository.days(userId).stream().filter(d->d.getQualifiedSubmissions()>0).map(GamificationDayEntity::getActivityDate).toList(),today);
    }
    @Override public Summary summary(UserPrincipal actor) {
        student(actor); accounts.requireCurrent(actor); var p=policy(); var at=now(); var today=at.atZone(ZONE).toLocalDate();
        var profile=repository.profile(actor.id()).orElse(null); var day=repository.day(actor.id(),today).orElse(null);
        long total=profile==null?0:profile.getTotalXp(),revision=profile==null?0:profile.getRevision();
        long deferred=repository.deferredCount(actor.id(),at),pending=repository.pendingCount(actor.id())-deferred;
        return new Summary(at,ZONE.getId(),p.getVersion(),p.getActivatedAt(),revision,total,total/100+1,total/100*100,
            Math.min(9007199254740991L,total/100*100+100),new Day(today,day==null?0:day.getAwardedXp(),CAP,
            day==null?0:day.getQualifiedSubmissions(),day!=null&&day.getQualifiedSubmissions()>0),streak(actor.id(),today),
            new BadgeCount(repository.badges(actor.id()).size(),3),new Backlog(pending,deferred),pending>0?"PENDING":deferred>0?"DEFERRED":"IDLE");
    }
    @Override public Badges badges(UserPrincipal actor) {
        student(actor); accounts.requireCurrent(actor); var at=now(); var earned=new HashMap<String,UserBadgeEntity>();
        repository.badges(actor.id()).forEach(b->earned.put(b.getBadgeCode(),b));
        int best=streak(actor.id(),at.atZone(ZONE).toLocalDate()).best();
        var items=new ArrayList<Badge>();
        String[][] specs={{"FIRST_LESSON","Bài học đầu tiên","Hoàn thành một bài học với hoạt động tự làm hợp lệ."},
            {"DUE_REVIEW","Ôn đến hạn","Tự làm đúng toàn bộ phần ôn của một mục tiêu đã đến hạn."},
            {"SEVEN_DAY_STREAK","Bảy ngày liên tiếp","Duy trì hoạt động học tập hợp lệ trong bảy ngày liên tiếp."}};
        for(var spec:specs) {
            var b=earned.get(spec[0]); int target=spec[0].equals("SEVEN_DAY_STREAK")?7:1;
            int progress=target==7?Math.min(best,7):b==null?0:1;
            items.add(new Badge(spec[0],spec[1],spec[2],b==null?null:b.getEarnedAt(),b==null?null:b.getEvidenceSourceAt(),progress,target));
        }
        return new Badges(at,items);
    }
    @Override public ActivitiesPage activities(Long sourceId,int page,int size,UserPrincipal actor) {
        student(actor); accounts.requireCurrent(actor);
        if(page<0||size<1||size>50||sourceId!=null&&(sourceId<1||sourceId>9007199254740991L)) throw invalid();
        RequestedSubmission requested=null;
        if(sourceId!=null) {
            repository.source(actor.id(),sourceId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","Không tìm thấy bài nộp."));
            var processed=repository.processed(sourceId); var failure=repository.failure(sourceId);
            String state=processed.isPresent()?processed.get().getDisposition():failure.isPresent()&&failure.get().getRetryAt().isAfter(now())?"DEFERRED":"PENDING";
            requested=new RequestedSubmission(sourceId,state);
        }
        long total=repository.activityCount(actor.id(),sourceId);
        return new ActivitiesPage(repository.activities(actor.id(),sourceId,page,size).stream().map(GamificationProcessedEntity::getResult).toList(),
            total,page,size,(int)Math.min(Integer.MAX_VALUE,(total+size-1)/size),requested);
    }
    public static ApiException invalid() { return new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Thông tin yêu cầu phần thưởng không hợp lệ."); }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public RequestReceiptService.Result synchronize(String requestId,UserPrincipal actor) {
        student(actor);
        if(requestId==null||!requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw invalid();
        return receipts.execute(actor,requestId,"POST /api/me/gamification/sync",Map.of("requestId",requestId),200,()-> {
            int processed=processLocked(actor.id()); var at=now(); long deferred=repository.deferredCount(actor.id(),at);
            return new Sync(requestId,processed,repository.pendingCount(actor.id())-deferred,deferred,
                repository.profile(actor.id()).map(GamificationProfileEntity::getRevision).orElse(0L),at);
        });
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public void processUser(long userId) {
        if(accounts.lockStudentForWorker(userId).isEmpty()) {
            var pending=repository.pending(userId,now(),1);
            if(!pending.isEmpty()) throw new GamificationSourceFailure(userId,pending.get(0).getId(),"ACCOUNT_UNAVAILABLE");
            return;
        }
        processLocked(userId);
    }
    @Override public List<Long> pendingUsers(long afterUserId) { return repository.pendingUsers(afterUserId,now(),20); }
    private int processLocked(long userId) {
        var policy=policy(); var pending=repository.pending(userId,now(),100);
        leagues.lockSources(pending.stream().map(SubmissionEntity::getResult).toList());
        for(var source:pending) {
            var result=source.getResult();
            if(result.factsSchemaVersion()!=1) throw new GamificationSourceFailure(userId,source.getId(),"GAMIFICATION_SCHEMA_UNSUPPORTED");
            process(userId,result,policy);
        }
        return pending.size();
    }
    private void process(long userId,Result result,GamificationPolicyEntity policy) {
        var at=now(); var date=result.submittedAt().atZone(ZONE).toLocalDate();
        var profile=repository.profile(userId).orElseGet(()->repository.save(new GamificationProfileEntity(userId)));
        var decisions=new ArrayList<Decision>(); int paid=0,requested=0; boolean qualifies=false,validDue=false;
        boolean before=result.submittedAt().isBefore(policy.getActivatedAt());
        if(!before) {
            var day=repository.day(userId,date).orElseGet(()->repository.save(new GamificationDayEntity(userId,date,CAP)));
            for(var item:result.items()) {
                String reason=newQuestionReason(item); int wanted=reason==null?REWARD:0,awarded=0;
                if(reason==null) {
                    if(repository.claim(userId,"NEW_QUESTION",item.originKey()).isPresent()) reason="ALREADY_CLAIMED";
                    else { awarded=claim(userId,result,day,"NEW_QUESTION",item.originKey(),null,at); reason=awarded==REWARD?"AWARDED":"DAILY_CAP_REACHED"; }
                }
                decisions.add(new Decision("NEW_QUESTION",item.attemptQuestionId(),item.objectiveId(),wanted,awarded,reason));
                requested+=wanted; paid+=awarded;
            }
            if(result.mode()==Mode.REVIEW) {
                var evidence=new HashMap<Long,ReviewRewardContextEntity>(); contexts.contexts(result.attemptId(),userId).forEach(c->evidence.put(c.getId().getObjectiveId(),c));
                var groups=new TreeMap<Long,List<ResultItem>>(); result.items().forEach(i->groups.computeIfAbsent(i.objectiveId(),k->new ArrayList<>()).add(i));
                for(var entry:groups.entrySet()) {
                    var context=evidence.get(entry.getKey()); String reason; int wanted=0,awarded=0;
                    if(context==null) reason="NO_REVIEW_CONTEXT";
                    else if(!context.isEligible()) reason="REVIEW_NOT_DUE";
                    else if(!objectivePassed(entry.getValue())) reason="REVIEW_OBJECTIVE_NOT_PASSED";
                    else {
                        wanted=REWARD;
                        if(repository.claim(userId,"DUE_REVIEW",context.getCycleKey()).isPresent()) reason="ALREADY_CLAIMED";
                        else if(repository.claimedDay(userId,entry.getKey(),date)) reason="ALREADY_REWARDED_TODAY";
                        else { awarded=claim(userId,result,day,"DUE_REVIEW",context.getCycleKey(),entry.getKey(),at); validDue=true; reason=awarded==REWARD?"AWARDED":"DAILY_CAP_REACHED"; }
                    }
                    decisions.add(new Decision("DUE_REVIEW",null,entry.getKey(),wanted,awarded,reason)); requested+=wanted; paid+=awarded;
                }
            }
            qualifies=qualified(result); if(qualifies) day.qualify(); repository.save(day);
            if(qualifies&&result.mode()==Mode.LEARN&&result.lessonCompleted()) badge(userId,"FIRST_LESSON",result,at);
            if(validDue) badge(userId,"DUE_REVIEW",result,at);
            if(streak(userId,at.atZone(ZONE).toLocalDate()).best()>=7) badge(userId,"SEVEN_DAY_STREAK",result,at);
        }
        profile.record(paid); repository.save(profile);
        String disposition=before?"BEFORE_ACTIVATION":"PROCESSED";
        var league=before?new LeagueAward(null,0,"BEFORE_ACTIVATION"):leagues.award(userId,result,paid);
        var activity=new Activity(result.submissionId(),result.attemptId(),result.mode().name(),result.submittedAt(),at,date,policy.getVersion(),
            disposition,requested,paid,qualifies,decisions,league);
        repository.save(new GamificationProcessedEntity(userId,activity)); repository.clearFailure(result.submissionId());
        repository.save(new GamificationOutboxEntity(userId,profile.getRevision(),at));
    }
    private int claim(long userId,Result result,GamificationDayEntity day,String type,String key,Long objective,Instant at) {
        var claim=repository.save(new GamificationClaimEntity(userId,result.submissionId(),type,key,objective,day.getActivityDate()));
        int awarded=day.award(REWARD);
        repository.save(new XpLedgerEntity(claim,VERSION,REWARD,awarded,result.submittedAt(),at)); return awarded;
    }
    private void badge(long userId,String code,Result result,Instant at) {
        if(repository.badges(userId).stream().noneMatch(b->b.getBadgeCode().equals(code)))
            repository.save(new UserBadgeEntity(userId,code,at,result.submittedAt(),result.submissionId()));
    }
}
