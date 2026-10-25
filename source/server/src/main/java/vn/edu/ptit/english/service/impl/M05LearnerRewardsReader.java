package vn.edu.ptit.english.service.impl;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.ptit.english.dto.StatisticsViews.*;
import vn.edu.ptit.english.dto.LeagueViews.Tier;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.*;
import static vn.edu.ptit.english.service.impl.GamificationRules.ZONE;

/** Read the same committed projections as the M05 pages; never sync or award on GET. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class M05LearnerRewardsReader implements LearnerRewardsReader {
    private final GamificationService rewards;
    private final LeagueService leagues;
    public M05LearnerRewardsReader(GamificationService rewards, LeagueService leagues) {
        this.rewards = rewards; this.leagues = leagues;
    }
    @Override public Rewards read(UserPrincipal actor) {
        var summary = rewards.summary(actor);
        var badges = rewards.badges(actor).items().stream().filter(b -> b.earnedAt() != null)
            .map(b -> new Badge(b.code(), b.name(), b.earnedAt())).toList();
        var league = leagues.overview(actor);
        var member = league.membership();
        var current = member == null ? null : week(league.season().startAt(), member.tier(), member.rank(), member.weeklyXp());
        var history = leagues.history(0, 5, actor).items().stream()
            .map(h -> week(h.season().startAt(), h.result().tierBefore(), h.result().rank(), h.result().weeklyXp())).toList();
        return new Rewards("AVAILABLE", summary.totalXp(), summary.activityLevel(), summary.streak().current(), badges, current, history);
    }
    private LeagueWeek week(Instant start, Tier tier, Integer rank, long xp) {
        return new LeagueWeek(start.atZone(ZONE).toLocalDate().toString(), tier.name(), rank, xp);
    }
}
