package vn.edu.ptit.english.service.impl;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.ptit.english.dto.AttemptViews.Result;
import vn.edu.ptit.english.dto.GamificationViews.LeagueAward;
import vn.edu.ptit.english.dto.LeagueViews.*;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.LeagueRepository;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.*;
import static vn.edu.ptit.english.service.impl.LeagueRanking.*;

@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class LeagueServiceImpl implements LeagueService {
    private final LeagueRepository repository;
    private final AccountGuard accounts;
    private final RequestReceiptService receipts;
    private final Clock clock;
    public LeagueServiceImpl(LeagueRepository repository,AccountGuard accounts,RequestReceiptService receipts,Clock clock){
        this.repository=repository;this.accounts=accounts;this.receipts=receipts;this.clock=clock;}
    private Instant now(){return clock.instant().truncatedTo(ChronoUnit.MICROS);}
    private void student(UserPrincipal actor){if(actor.role()!=Role.STUDENT)throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Chỉ học viên được tham gia giải đấu.");accounts.requireCurrent(actor);}
    private static ApiException conflict(String code,String message){return new ApiException(HttpStatus.CONFLICT,code,message);}
    private void page(int page,int size){if(page<0||size<1||size>50)throw GamificationServiceImpl.invalid();}
    private Season view(LeagueSeasonEntity s,Instant at){String status=s.getStatus().equals("FINALIZED")?"FINALIZED":at.isBefore(s.getEndAt())?"OPEN":"CLOSING";
        return new Season(s.getId(),s.getStartAt(),s.getEndAt(),s.getCutoffAt(),status,s.getPolicyVersion(),s.getRules());}
    private Ranking ranking(LeagueGroupEntity group,LeagueSeasonEntity season,List<LeagueMemberEntity> members){
        return rank(members.stream().map(m->new Score(m.getMemberKey(),m.getWeeklyXp())).toList(),group.getTier(),season.getRules());}
    private Membership membership(LeagueMemberEntity member,LeagueSeasonEntity season){
        var group=repository.group(member.getGroupId());var frozen=member.getResult();
        var placement=ranking(group,season,repository.members(group.getId())).placements().get(member.getMemberKey());
        return new Membership(member.getMemberKey(),group.getId(),member.getTier(),member.getJoinedAt(),member.getWeeklyXp(),
            frozen==null?placement.rank():frozen.rank(),frozen==null?placement.outcome():frozen.outcome(),frozen);
    }
    @Override public Overview overview(UserPrincipal actor){
        student(actor);var at=now();var season=repository.season(weekStart(at)).orElse(null);
        if(season==null)return new Overview(at,0,"UNAVAILABLE",false,null,null);
        var member=repository.member(actor.id(),season.getId()).orElse(null);
        if(member==null){boolean waiting=repository.previous(actor.id(),season.getStartAt()).stream().anyMatch(m->m.getResult()==null);
            return new Overview(at,season.getRevision(),waiting?"WAITING_PREVIOUS_SETTLEMENT":"NOT_JOINED",!waiting&&at.isBefore(season.getEndAt())&&season.getStatus().equals("OPEN"),view(season,at),null);}
        var group=repository.group(member.getGroupId());String state=member.getResult()!=null?"FINALIZED":at.isBefore(season.getEndAt())?"ACTIVE":"CLOSING";
        return new Overview(at,group.getRevision(),state,false,view(season,at),membership(member,season));
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public RequestReceiptService.Result join(String requestId,UserPrincipal actor){
        student(actor);if(requestId==null||!requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw GamificationServiceImpl.invalid();
        return receipts.execute(actor,requestId,"POST /api/me/league/join",Map.of("requestId",requestId),200,()->{
            var initial=repository.season(weekStart(now())).orElseThrow(()->conflict("LEAGUE_NOT_OPEN","Giải đấu tuần chưa sẵn sàng."));
            // Older memberships are read without locks: finalization only turns null into a frozen result.
            var previous=repository.previous(actor.id(),initial.getStartAt());
            if(previous.stream().anyMatch(m->m.getResult()==null))throw conflict("LEAGUE_SETTLEMENT_PENDING","Kết quả tuần trước đang được chốt. Vui lòng thử lại sau.");
            var season=repository.lockSeason(initial.getId());var at=now();
            if(!weekStart(at).equals(season.getStartAt())||!at.isBefore(season.getEndAt())||!season.getStatus().equals("OPEN"))
                throw conflict("LEAGUE_NOT_OPEN","Tuần thi đấu đã thay đổi. Vui lòng tải lại.");
            var existing=repository.member(actor.id(),season.getId()).orElse(null);
            if(existing!=null)return new Join(requestId,false,repository.group(existing.getGroupId()).getRevision(),view(season,at),membership(existing,season));
            Tier tier=previous.isEmpty()?Tier.BRONZE:previous.get(0).getResult().tierAfter();
            repository.lockAllocator(season.getId(),tier);
            var available=repository.available(season.getId(),tier);
            var group=available.isPresent()?repository.lockGroup(available.get().getId()):repository.save(new LeagueGroupEntity(season.getId(),tier,repository.nextNumber(season.getId(),tier)));
            var member=repository.save(new LeagueMemberEntity(actor.id(),group,at));group.addMember();season.changed();repository.save(group);repository.save(season);
            repository.save(new LeagueOutboxEntity(group.getId(),null,group.getRevision(),"LEAGUE_CHANGED",at));
            return new Join(requestId,true,group.getRevision(),view(season,at),membership(member,season));
        });
    }
    @Override public Board leaderboard(int page,int size,UserPrincipal actor){
        student(actor);page(page,size);var at=now();var season=repository.season(weekStart(at)).orElse(null);
        var self=season==null?null:repository.member(actor.id(),season.getId()).orElse(null);
        if(self==null)throw new ApiException(HttpStatus.NOT_FOUND,"LEAGUE_MEMBERSHIP_NOT_FOUND","Bạn chưa tham gia giải đấu tuần này.");
        var group=repository.group(self.getGroupId());var members=repository.members(group.getId());var ranking=ranking(group,season,members);
        var names=repository.names(members.stream().map(LeagueMemberEntity::getUserId).toList());var byKey=new HashMap<String,LeagueMemberEntity>();members.forEach(m->byKey.put(m.getMemberKey(),m));
        var rows=ranking.sorted().stream().map(score->{var m=byKey.get(score.key());var placement=ranking.placements().get(score.key());var frozen=m.getResult();
            return new Member(m.getMemberKey(),names.getOrDefault(m.getUserId(),"Học viên"),m.getUserId()==actor.id(),frozen==null?placement.rank():frozen.rank(),
                m.getWeeklyXp(),frozen==null?placement.outcome():frozen.outcome());}).toList();
        var own=rows.stream().filter(Member::isSelf).findFirst().orElseThrow();int from=(int)Math.min(rows.size(),(long)page*size),to=Math.min(rows.size(),from+size);
        return new Board(rows.subList(from,to),rows.size(),page,size,(rows.size()+size-1)/size,at,season.getId(),group.getId(),group.getTier(),
            group.getRevision(),group.getFinalizedAt()==null,ranking.stats(),own);
    }
    @Override public History history(int page,int size,UserPrincipal actor){
        student(actor);page(page,size);long total=repository.historyCount(actor.id());var at=now();
        return new History(repository.history(actor.id(),page,size).stream().map(m->new HistoryItem(view(repository.season(m.getSeasonId()),at),m.getGroupId(),m.getResult())).toList(),
            total,page,size,(int)Math.min(Integer.MAX_VALUE,(total+size-1)/size));
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void lockSources(List<Result> sources){
        // Pre-lock all seasons in time order, before writing profiles/claims for any source in the batch.
        sources.stream().map(r->weekStart(r.submittedAt())).distinct().sorted().forEach(start->repository.season(start).ifPresent(s->repository.lockSeason(s.getId())));
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public LeagueAward award(long user,Result source,int xp){
        var season=repository.season(weekStart(source.submittedAt())).orElse(null);var member=season==null?null:repository.member(user,season.getId()).orElse(null);
        String reason=xp==0?"NO_AWARDED_XP":member==null?"NOT_JOINED":source.submittedAt().isBefore(member.getJoinedAt())?"BEFORE_JOIN":
            !now().isBefore(season.getCutoffAt())||member.getResult()!=null||season.getStatus().equals("FINALIZED")?"LATE_AFTER_CUTOFF":"COUNTED";
        if(reason.equals("COUNTED")){
            var group=repository.lockGroup(member.getGroupId());member.award(xp);group.changed();season.changed();repository.save(member);repository.save(group);repository.save(season);
            repository.save(new LeagueOutboxEntity(group.getId(),null,group.getRevision(),"LEADERBOARD_CHANGED",now()));
        }
        for(var ledger:repository.ledger(source.submissionId())){ledger.league(season==null?null:season.getId(),reason.equals("COUNTED")?ledger.getAwardedXp():0,
            ledger.getAwardedXp()==0?"NO_AWARDED_XP":reason);repository.save(ledger);}
        return new LeagueAward(season==null?null:season.getId(),reason.equals("COUNTED")?xp:0,reason);
    }
}
