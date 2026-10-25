package vn.edu.ptit.english.service.impl;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.ptit.english.dto.LeagueViews.*;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.repository.LeagueRepository;
import static vn.edu.ptit.english.service.impl.LeagueRanking.*;

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
public class LeagueLifecycle {
    private final LeagueRepository repository;private final Clock clock;
    public LeagueLifecycle(LeagueRepository repository,Clock clock){this.repository=repository;this.clock=clock;}
    private Instant now(){return clock.instant().truncatedTo(ChronoUnit.MICROS);}
    public void ensureCurrent(){
        // A single existing policy row serializes first creation, including concurrent schedulers.
        repository.lockPolicy();var start=weekStart(now());if(repository.season(start).isPresent())return;
        var season=repository.save(new LeagueSeasonEntity(start,RULES,GamificationRules.VERSION));
        for(var tier:Tier.values())repository.save(new LeagueAllocatorEntity(season.getId(),tier));
    }
    @Transactional(readOnly=true) public List<Long> due(){return repository.dueSeasons(now());}
    public List<Long> close(long id){
        var season=repository.lockSeason(id);var at=now();if(at.isBefore(season.getEndAt())||season.getStatus().equals("FINALIZED"))return List.of();
        season.closing();repository.save(season);if(at.isBefore(season.getCutoffAt()))return List.of();
        var groups=repository.unfinishedGroups(id);if(groups.isEmpty()){season.finish();repository.save(season);}return groups;
    }
    public void finalizeGroup(long seasonId,long groupId){
        var season=repository.lockSeason(seasonId);var at=now();if(at.isBefore(season.getCutoffAt()))return;
        var group=repository.lockGroup(groupId);if(group.getSeasonId()!=seasonId)throw new IllegalArgumentException("Wrong season");
        if(group.getFinalizedAt()!=null)return;
        var members=repository.members(groupId);var ranked=rank(members.stream().map(m->new Score(m.getMemberKey(),m.getWeeklyXp())).toList(),group.getTier(),season.getRules());
        for(var member:members){var placement=ranked.placements().get(member.getMemberKey());
            member.finish(new Result(placement.rank(),members.size(),ranked.stats().activeCount(),member.getWeeklyXp(),member.getTier(),placement.next(),placement.outcome(),placement.reason(),at));
            repository.save(member);
        }
        // No actor lock or new users FK here: frozen results live on the existing membership rows.
        group.finish(at);season.changed();repository.save(group);repository.save(season);
        repository.save(new LeagueOutboxEntity(groupId,null,group.getRevision(),"LEAGUE_CHANGED",at));
        if(repository.unfinishedGroups(seasonId).isEmpty()){season.finish();repository.save(season);}
    }
}
