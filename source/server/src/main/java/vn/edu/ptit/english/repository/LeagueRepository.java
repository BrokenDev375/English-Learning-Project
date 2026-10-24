package vn.edu.ptit.english.repository;
import java.time.Instant;
import java.util.*;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.dto.LeagueViews.Tier;

public interface LeagueRepository {
    <T extends GamificationRow> T save(T entity);
    void lockPolicy();
    Optional<LeagueSeasonEntity> season(Instant start);
    LeagueSeasonEntity lockSeason(long id);
    LeagueSeasonEntity season(long id);
    void lockAllocator(long season,Tier tier);
    Optional<LeagueGroupEntity> available(long season,Tier tier);
    int nextNumber(long season,Tier tier);
    LeagueGroupEntity group(long id);
    LeagueGroupEntity lockGroup(long id);
    Optional<LeagueMemberEntity> member(long user,long season);
    List<LeagueMemberEntity> members(long group);
    List<LeagueMemberEntity> previous(long user,Instant before);
    Map<Long,String> names(List<Long> users);
    List<LeagueMemberEntity> history(long user,int page,int size);
    long historyCount(long user);
    List<Long> dueSeasons(Instant at);
    List<Long> unfinishedGroups(long season);
    List<XpLedgerEntity> ledger(long submission);
    List<LeagueOutboxEntity> events(int limit);
}
