package vn.edu.ptit.english.service.impl;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.english.dto.WeeklyLeaderboardViews.*;
import vn.edu.ptit.english.entity.GamificationPolicyEntity;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.WeeklyLeaderboardService;
import static vn.edu.ptit.english.service.impl.GamificationRules.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class WeeklyLeaderboardServiceImpl implements WeeklyLeaderboardService {
    private final WeeklyLeaderboardRepository repository;
    private final GamificationRepository rewards;
    private final GamificationContextRepository contexts;
    private final AccountGuard accounts;
    private final Clock clock;
    public WeeklyLeaderboardServiceImpl(WeeklyLeaderboardRepository repository,GamificationRepository rewards,
        GamificationContextRepository contexts,AccountGuard accounts,Clock clock) {
        this.repository=repository;this.rewards=rewards;this.contexts=contexts;this.accounts=accounts;this.clock=clock;
    }
    private long level(long xp,GamificationPolicyEntity policy) {
        return (VERSION.equals(policy.getVersion())?ActivityLevels.progressive(xp)
            :ActivityLevels.fixed(xp,policy.getPolicy().path("xpPerActivityLevel").asLong())).value();
    }
    private Member member(WeeklyLeaderboardRepository.Score score,long selfId,GamificationPolicyEntity policy) {
        return new Member(score.position(),score.rank(),score.displayName(),level(score.totalXp(),policy),score.weeklyXp(),score.userId()==selfId);
    }
    public Board read(int page,int size,UserPrincipal actor) {
        if(actor.role()!=Role.STUDENT)throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Bảng xếp hạng dành cho học viên.");
        var user=accounts.requireCurrent(actor);
        if(page<0||size<1||size>50)throw GamificationServiceImpl.invalid();
        var at=clock.instant().truncatedTo(ChronoUnit.MICROS);
        var start=LeagueRanking.weekStart(at);var firstDay=start.atZone(ZONE).toLocalDate();var lastDay=firstDay.plusWeeks(1);
        var policy=GamificationServiceImpl.supported(contexts.policy());
        var items=repository.page(firstDay,lastDay,(long)page*size,size).stream().map(s->member(s,actor.id(),policy)).toList();
        var top=page==0&&size>=3?items.stream().limit(3).toList():repository.page(firstDay,lastDay,0,3).stream().map(s->member(s,actor.id(),policy)).toList();
        var own=repository.self(firstDay,lastDay,actor.id()).map(s->member(s,actor.id(),policy)).orElseGet(()->
            new Member(null,null,user.fullName(),level(rewards.profile(actor.id()).map(p->p.getTotalXp()).orElse(0L),policy),0,true));
        long total=repository.count(firstDay,lastDay);
        return new Board(at,ZONE.getId(),start,lastDay.atStartOfDay(ZONE).toInstant(),top,items,own,total,page,size,(total+size-1)/size);
    }
}
