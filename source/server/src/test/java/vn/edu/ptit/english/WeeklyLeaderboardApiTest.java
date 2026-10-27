package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.service.impl.LeagueRanking;
import static org.assertj.core.api.Assertions.*;

class WeeklyLeaderboardApiTest extends GamificationFixture {
    static final AtomicInteger weeks=new AtomicInteger();
    Instant start;
    LocalDate monday;
    @BeforeEach void uniqueWeek() {
        start=LeagueRanking.weekStart(Instant.parse("2048-02-10T12:00:00Z")).plus(Duration.ofDays(14L*weeks.getAndIncrement()));
        monday=start.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate(); clock.set(start.plusSeconds(3600).toString());
    }
    JsonNode board(Client c,String query)throws Exception{return ok(c.call("GET","/api/me/leaderboard"+query,null),200);}
    void score(long user,LocalDate day,int xp) {
        tx.executeWithoutResult(t->{var d=new GamificationDayEntity(user,day,200);d.award(xp);em.persist(d);});
    }
    void profile(long user,int xp) {tx.executeWithoutResult(t->{var p=new GamificationProfileEntity(user);p.record(xp);em.persist(p);});}
    long learner(String name) {return tx.execute(t->users.create(key()+"@global.test",hash,name,Role.STUDENT).id());}
    @Test void readsArePrivateOwnerScopedAndNeverCreateRewardsOrLeagueMembership()throws Exception {
        ok(new Client().call("GET","/api/me/leaderboard",null),401);
        ok(new Client().login(teacher).call("GET","/api/me/leaderboard",null),403);
        var admin=tx.execute(t->users.create(key()+"@admin.global.test",hash,"Admin",Role.ADMIN));
        ok(new Client().login(admin).call("GET","/api/me/leaderboard",null),403);
        var c=new Client().login(alice);
        for(String query:List.of("?userId=1","?week=old","?page=-1","?size=0","?size=51","?page=no","?size=2147483648"))
            ok(c.call("GET","/api/me/leaderboard"+query,null),400);
        var response=c.call("GET","/api/me/leaderboard",null);var data=ok(response,200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store","private");
        assertThat(data.path("items")).isEmpty();assertThat(data.path("self").path("rank").isNull()).isTrue();
        assertThat(data.path("self").path("isSelf").asBoolean()).isTrue();
        board(c,"");for(String table:List.of("gamification_profiles","gamification_daily_stats","xp_ledger","league_members"))assertThat(count(table)).isZero();
        assertThat(data.toString()).doesNotContain("\"email\"","\"userId\"","\"passwordHash\"","\"securityVersion\"");
    }
    @Test void equalXpSharesCompetitionRankAndSelfAndTopRemainVisibleAcrossPages()throws Exception {
        score(alice.id(),monday,10);profile(alice.id(),10);score(bob.id(),monday,30);profile(bob.id(),30);
        for(int xp:new int[]{30,20,20}){long id=learner("Cùng tên");score(id,monday,xp);profile(id,xp);}
        var c=new Client().login(alice);var data=board(c,"?size=50");
        assertThat(data.path("totalElements").asLong()).isEqualTo(5);
        assertThat(data.path("items").findValuesAsText("rank")).containsExactly("1","1","3","3","5");
        var page=board(c,"?page=1&size=2");assertThat(page.path("items").findValuesAsText("rank")).containsExactly("3","3");
        assertThat(page.path("top").findValuesAsText("rank")).containsExactly("1","1","3");
        assertThat(page.path("self").path("rank").asLong()).isEqualTo(5);
        assertThat(page.path("self").path("activityLevel").asLong()).isEqualTo(2);
        assertThat(page.path("items").findValuesAsText("position")).containsExactly("3","4");
        var distant=board(c,"?page=2147483647&size=50");assertThat(distant.path("items")).isEmpty();
        assertThat(distant.path("self").path("rank").asLong()).isEqualTo(5);assertThat(distant.path("top")).hasSize(3);
    }
    @Test void mondayBoundariesUseVietnamDatesAndExcludeTheAdjacentWeeks()throws Exception {
        profile(alice.id(),75);score(alice.id(),monday.minusDays(1),5);score(alice.id(),monday,10);
        score(alice.id(),monday.plusDays(6),20);score(alice.id(),monday.plusDays(7),40);
        var c=new Client().login(alice);
        clock.set(start.minusNanos(1000).toString());assertThat(board(c,"").path("self").path("weeklyXp").asLong()).isEqualTo(5);
        clock.set(start.plus(Duration.ofDays(7)).minusNanos(1000).toString());var data=board(c,"");
        assertThat(data.path("self").path("weeklyXp").asLong()).isEqualTo(30);
        assertThat(data.path("weekStartAt").asText()).isEqualTo(start.toString());assertThat(data.path("zone").asText()).isEqualTo("Asia/Ho_Chi_Minh");
        clock.set(start.plus(Duration.ofDays(7)).toString());assertThat(board(c,"").path("self").path("weeklyXp").asLong()).isEqualTo(40);
    }
    @Test void onlyActiveStudentsWithPositiveWeeklyXpAreRanked()throws Exception {
        score(alice.id(),monday,10);score(bob.id(),monday,100);score(teacher.id(),monday,150);
        long deleted=learner("Deleted");score(deleted,monday,150);
        tx.executeWithoutResult(t->{
            em.createNativeQuery("UPDATE users SET status='LOCKED' WHERE id=:u").setParameter("u",bob.id()).executeUpdate();
            em.createNativeQuery("UPDATE users SET status='DELETED' WHERE id=:u").setParameter("u",deleted).executeUpdate();
        });
        long zero=learner("Zero XP");score(zero,monday,0);
        var data=board(new Client().login(alice),"");assertThat(data.path("totalElements").asLong()).isEqualTo(1);
        assertThat(data.path("self").path("rank").asLong()).isEqualTo(1);assertThat(data.path("top")).hasSize(1);
        assertThat(data.toString()).doesNotContain("Deleted","Teacher","Zero XP");
    }
    @Test void committedRewardsCountWithoutJoiningAndResetNextWeekWithoutLosingLifetimeXp()throws Exception {
        var c=new Client().login(alice);submit(c,practice(c,3),"yes");
        assertThat(board(c,"").path("self").path("weeklyXp").asLong()).isZero();sync(c);
        var data=board(c,"");assertThat(data.path("self").path("weeklyXp").asLong()).isEqualTo(15);
        assertThat(data.path("self").path("rank").asLong()).isEqualTo(1);assertThat(count("league_members")).isZero();
        long ledger=count("xp_ledger");board(c,"");sync(c);assertThat(count("xp_ledger")).isEqualTo(ledger);
        assertThat(board(c,"").path("self").path("weeklyXp").asLong()).isEqualTo(15);
        clock.set(start.plus(Duration.ofDays(7)).toString());data=board(c,"");
        assertThat(data.path("self").path("weeklyXp").asLong()).isZero();assertThat(data.path("self").path("rank").isNull()).isTrue();
        assertThat(data.path("self").path("activityLevel").asLong()).isEqualTo(2);assertThat(summary(c).path("totalXp").asLong()).isEqualTo(15);
    }
}
