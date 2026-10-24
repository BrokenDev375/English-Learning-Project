package vn.edu.ptit.english;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.edu.ptit.english.dto.LeagueViews.*;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.repository.LeagueRepository;
import vn.edu.ptit.english.service.impl.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses the shared M03 HTTP fixture; XP tests run once in their own class. */
class LeagueApiTest extends GamificationFixture {
    @Autowired LeagueLifecycle lifecycle;
    @MockitoSpyBean LeagueRepository leagues;
    static AtomicInteger weeks=new AtomicInteger();
    Instant start;
    void week(){start=Instant.parse("2027-01-03T17:00:00Z").plus(Duration.ofDays(70L*weeks.getAndIncrement()));
        clock.set(start.plusSeconds(3600).toString());lifecycle.ensureCurrent();}
    long season(){return tx.execute(t->leagues.season(start).orElseThrow().getId());}
    com.fasterxml.jackson.databind.JsonNode join(Client c)throws Exception{return ok(c.call("POST","/api/me/league/join",Map.of("requestId",key())),200);}
    com.fasterxml.jackson.databind.JsonNode board(Client c)throws Exception{return ok(c.call("GET","/api/me/league/leaderboard",null),200);}
    long group(Client c)throws Exception{return join(c).path("membership").path("groupId").asLong();}
    void finish(long season,long group){clock.set(start.plus(Duration.ofDays(7)).plusSeconds(120).toString());lifecycle.finalizeGroup(season,group);}

    @Test void leagueReadsDoNotCreateSeasonsAndStrictlyScopeMembership()throws Exception{
        start=Instant.parse("2035-01-07T17:00:00Z");clock.set(start.plusSeconds(3600).toString());var c=new Client().login(alice);
        assertThat(ok(c.call("GET","/api/me/league",null),200).path("state").asText()).isEqualTo("UNAVAILABLE");
        assertThat((boolean)tx.execute(t->leagues.season(start).isPresent())).isFalse();
        lifecycle.ensureCurrent();assertThat(ok(c.call("GET","/api/me/league",null),200).path("canJoin").asBoolean()).isTrue();
        ok(c.call("GET","/api/me/league/leaderboard",null),404);
        ok(c.call("GET","/api/me/league?userId="+bob.id(),null),400);
        ok(c.call("GET","/api/me/league/leaderboard?groupId=1",null),400);
        ok(c.call("GET","/api/me/league/history?size=0",null),400);
        ok(c.call("POST","/api/me/league/join",Map.of("requestId",key(),"tier","GOLD")),400);
        ok(new Client().login(teacher).call("POST","/api/me/league/join",Map.of("requestId",key())),403);
        ok(new Client().call("GET","/api/me/league",null),401);
        var b=new Client().login(bob);group(b);ok(c.call("GET","/api/me/league/leaderboard",null),404);
    }
    @Test void joinReplayAndTwoSessionsNeverCreateSecondMembershipOrMoveJoinedAt()throws Exception{
        week();var c=new Client().login(alice);String request=key();var original=c.call("POST","/api/me/league/join",Map.of("requestId",request));var body=ok(original,200);
        assertThat(body.path("created").asBoolean()).isTrue();var replay=c.call("POST","/api/me/league/join",Map.of("requestId",request));
        assertThat(ok(replay,200)).isEqualTo(body);assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        var other=new Client().login(alice);var pool=Executors.newFixedThreadPool(2);
        try{var a=pool.submit(()->join(c));var b=pool.submit(()->join(other));
            assertThat(a.get(20,TimeUnit.SECONDS).path("created").asBoolean()).isFalse();assertThat(b.get(20,TimeUnit.SECONDS).path("membership")).isEqualTo(body.path("membership"));
        }finally{pool.shutdownNow();}
        assertThat(board(c).path("totalElements").asInt()).isEqualTo(1);
        ok(c.call("POST","/api/me/gamification/sync",Map.of("requestId",request)),409);
    }
    @Test void xpBeforeJoinIsLifetimeOnlyAndNewXpCountsExactlyOnce()throws Exception{
        week();var c=new Client().login(alice);var before=submit(c,practice(c,3),"yes");clock.set(start.plusSeconds(7200).toString());join(c);sync(c);
        assertThat(summary(c).path("totalXp").asInt()).isEqualTo(15);assertThat(board(c).path("self").path("weeklyXp").asInt()).isZero();
        var history=ok(c.call("GET","/api/me/gamification/activities?submissionId="+before.path("submissionId").asLong(),null),200);
        assertThat(history.path("items").get(0).path("league").path("reason").asText()).isEqualTo("BEFORE_JOIN");
        var next=submit(c,practice(c,6),"yes");sync(c);var total=summary(c).path("totalXp").asInt();
        assertThat(total).isGreaterThan(15);assertThat(board(c).path("self").path("weeklyXp").asInt()).isEqualTo(total-15);
        sync(c);assertThat(board(c).path("self").path("weeklyXp").asInt()).isEqualTo(total-15);
        assertThat(board(c).toString()).doesNotContain(alice.email(),"userId","password","answerKey");
        assertThat(next.path("submissionId").asLong()).isPositive();
    }
    @Test void twoUsersCompetingForLastSlotStayWithinCapacity()throws Exception{
        week();long season=season();tx.executeWithoutResult(t->{var locked=leagues.lockSeason(season);leagues.lockAllocator(season,Tier.BRONZE);
            var g=leagues.save(new LeagueGroupEntity(season,Tier.BRONZE,1));
            for(int i=0;i<24;i++){var u=users.create(key()+"@league.test",hash,"Peer "+i,Role.STUDENT);leagues.save(new LeagueMemberEntity(u.id(),g,clock.instant()));g.addMember();}leagues.save(g);});
        var a=new Client().login(alice);var b=new Client().login(bob);var pool=Executors.newFixedThreadPool(2);
        try{var x=pool.submit(()->join(a));var y=pool.submit(()->join(b));long ga=x.get(30,TimeUnit.SECONDS).path("membership").path("groupId").asLong();long gb=y.get(30,TimeUnit.SECONDS).path("membership").path("groupId").asLong();
            assertThat(ga).isNotEqualTo(gb);assertThat(board(a).path("totalElements").asInt()+board(b).path("totalElements").asInt()).isEqualTo(26);
        }finally{pool.shutdownNow();}
        assertThat(board(a).path("self").path("rank").asInt()).isEqualTo(1);
        assertThat(ok(a.call("GET","/api/me/league/leaderboard?page=1&size=1",null),200).path("self").path("isSelf").asBoolean()).isTrue();
    }
    @Test void graceIncludesOldWeekButCutoffExcludesItWithoutLosingLifetimeXp()throws Exception{
        week();var a=new Client().login(alice);var b=new Client().login(bob);group(a);group(b);
        clock.set(start.plus(Duration.ofDays(7)).minusNanos(1000).toString());var ra=submit(a,practice(a,3),"yes");var rb=submit(b,practice(b,3),"yes");
        clock.set(start.plus(Duration.ofDays(7)).plusSeconds(119).toString());sync(a);
        clock.set(start.plus(Duration.ofDays(7)).plusSeconds(120).toString());sync(b);
        assertThat((long)tx.execute(t->leagues.member(alice.id(),season()).orElseThrow().getWeeklyXp())).isEqualTo(15L);
        assertThat((long)tx.execute(t->leagues.member(bob.id(),season()).orElseThrow().getWeeklyXp())).isZero();
        assertThat(summary(b).path("totalXp").asInt()).isEqualTo(15);
        var activity=ok(b.call("GET","/api/me/gamification/activities?submissionId="+rb.path("submissionId").asLong(),null),200).path("items").get(0);
        assertThat(activity.path("league").path("reason").asText()).isEqualTo("LATE_AFTER_CUTOFF");assertThat(ra.path("submissionId").asLong()).isPositive();
    }
    @Test void finalizerRollsBackAndResumeFreezesResultExactlyOnce()throws Exception{
        week();var c=new Client().login(alice);long group=group(c),season=season();submit(c,practice(c,3),"yes");sync(c);
        clock.set(start.plus(Duration.ofDays(7)).plusSeconds(120).toString());
        doAnswer(call->{Object row=call.getArgument(0);if(row instanceof LeagueOutboxEntity)throw new IllegalStateException("injected finalizer outbox failure");return call.callRealMethod();}).when(leagues).save(any());
        try{assertThatThrownBy(()->lifecycle.finalizeGroup(season,group)).isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class).hasMessageContaining("injected finalizer outbox failure");}finally{reset(leagues);}
        assertThat((Object)tx.execute(t->leagues.member(alice.id(),season).orElseThrow().getResult())).isNull();
        assertThat((Object)tx.execute(t->leagues.group(group).getFinalizedAt())).isNull();
        lifecycle.finalizeGroup(season,group);var first=ok(c.call("GET","/api/me/league/history",null),200);
        lifecycle.finalizeGroup(season,group);assertThat(ok(c.call("GET","/api/me/league/history",null),200)).isEqualTo(first);
        assertThat(first.path("items").get(0).path("result").path("holdReason").asText()).isEqualTo("TOO_FEW_MEMBERS");
    }
    @Test void onlyOwnUnsettledGroupBlocksNewWeekAndLatestFinalTierSurvivesAnAbsentWeek()throws Exception{
        week();var c=new Client().login(alice);long group=group(c),old=season();
        tx.executeWithoutResult(t->{leagues.lockSeason(old);leagues.lockAllocator(old,Tier.SILVER);leagues.save(new LeagueGroupEntity(old,Tier.SILVER,1));});
        clock.set(start.plus(Duration.ofDays(7)).plusSeconds(120).toString());lifecycle.ensureCurrent();
        assertThat(ok(c.call("GET","/api/me/league",null),200).path("state").asText()).isEqualTo("WAITING_PREVIOUS_SETTLEMENT");
        ok(c.call("POST","/api/me/league/join",Map.of("requestId",key())),409);lifecycle.finalizeGroup(old,group);
        assertThat(join(c).path("created").asBoolean()).isTrue();
        clock.set(start.plus(Duration.ofDays(14)).plusSeconds(120).toString());
        long second=tx.execute(t->leagues.season(start.plus(Duration.ofDays(7))).orElseThrow().getId());
        long secondGroup=tx.execute(t->leagues.member(alice.id(),second).orElseThrow().getGroupId());lifecycle.finalizeGroup(second,secondGroup);
        clock.set(start.plus(Duration.ofDays(21)).plusSeconds(3600).toString());lifecycle.ensureCurrent();
        assertThat(join(c).path("membership").path("tier").asText()).isEqualTo("BRONZE");
        assertThat(ok(c.call("GET","/api/me/league/history",null),200).path("totalElements").asInt()).isEqualTo(2);
    }
    @Test void timeIsCheckedAfterWaitingForSeasonLockAtCutoff()throws Exception{
        week();var c=new Client().login(alice);group(c);long id=season();clock.set(start.plus(Duration.ofDays(7)).minusSeconds(1).toString());submit(c,practice(c,3),"yes");
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{var holder=pool.submit(()->tx.executeWithoutResult(t->{leagues.lockSeason(id);locked.countDown();try{if(!release.await(20,TimeUnit.SECONDS))throw new IllegalStateException("timeout");}catch(InterruptedException e){throw new RuntimeException(e);}}));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();var sync=pool.submit(()->sync(c));Thread.sleep(200);
            clock.set(start.plus(Duration.ofDays(7)).plusSeconds(120).toString());release.countDown();holder.get(20,TimeUnit.SECONDS);sync.get(20,TimeUnit.SECONDS);
            assertThat((long)tx.execute(t->leagues.member(alice.id(),id).orElseThrow().getWeeklyXp())).isZero();assertThat(summary(c).path("totalXp").asInt()).isEqualTo(15);
        }finally{release.countDown();pool.shutdownNow();}
    }
    @Test void promotedFrozenTierIsUsedNextWeekAndConcurrentFinalizersAgree()throws Exception{
        week();var c=new Client().login(alice);long group=group(c),id=season();
        // Deliberate ranking fixture, separate from real-source XP tests and never written to the demo.
        tx.executeWithoutResult(t->{leagues.lockSeason(id);var g=leagues.lockGroup(group);var own=leagues.member(alice.id(),id).orElseThrow();own.award(100);leagues.save(own);
            for(int i=0;i<9;i++){var u=users.create(key()+"@league.test",hash,"Rank peer "+i,Role.STUDENT);var m=new LeagueMemberEntity(u.id(),g,clock.instant());m.award(90-i*10);leagues.save(m);g.addMember();}leagues.save(g);});
        clock.set(start.plus(Duration.ofDays(7)).plusSeconds(120).toString());var pool=Executors.newFixedThreadPool(2);
        try{var a=pool.submit(()->lifecycle.finalizeGroup(id,group));var b=pool.submit(()->lifecycle.finalizeGroup(id,group));a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
        var result=tx.execute(t->leagues.member(alice.id(),id).orElseThrow().getResult());
        assertThat(result.outcome()).isEqualTo("PROMOTE");assertThat(result.tierAfter()).isEqualTo(Tier.SILVER);
        lifecycle.ensureCurrent();assertThat(join(c).path("membership").path("tier").asText()).isEqualTo("SILVER");
        assertThat(ok(c.call("GET","/api/me/league/history",null),200).path("items").get(0).path("result").path("weeklyXp").asInt()).isEqualTo(100);
    }
    @Test void finalizerDoesNotAcquireImplicitUserFkLockBehindWaitingRewardWriter()throws Exception{
        week();var c=new Client().login(alice);long group=group(c),id=season();
        clock.set(start.plus(Duration.ofDays(7)).minusSeconds(1).toString());submit(c,practice(c,3),"yes");
        clock.set(start.plus(Duration.ofDays(7)).plusSeconds(120).toString());
        var seasonHeld=new CountDownLatch(1);var actorHeld=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var closer=pool.submit(()->tx.executeWithoutResult(t->{leagues.lockSeason(id);seasonHeld.countDown();
                try{if(!actorHeld.await(15,TimeUnit.SECONDS))throw new IllegalStateException("actor timeout");}catch(InterruptedException e){throw new RuntimeException(e);}
                lifecycle.finalizeGroup(id,group);}));
            assertThat(seasonHeld.await(10,TimeUnit.SECONDS)).isTrue();
            var writer=pool.submit(()->tx.executeWithoutResult(t->{
                em.find(UserEntity.class,alice.id(),jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);actorHeld.countDown();service.processUser(alice.id());}));
            closer.get(20,TimeUnit.SECONDS);writer.get(20,TimeUnit.SECONDS);
        }finally{actorHeld.countDown();pool.shutdownNow();}
        assertThat(tx.execute(t->leagues.member(alice.id(),id).orElseThrow().getResult()).weeklyXp()).isZero();
        assertThat(summary(c).path("totalXp").asInt()).isEqualTo(15);
    }
    @Test void awardAcceptedBeforeCutoffSurvivesCommitAfterCutoffAndFinalizerWaits()throws Exception{
        week();var c=new Client().login(alice);long group=group(c),id=season();
        clock.set(start.plus(Duration.ofDays(7)).minusSeconds(1).toString());submit(c,practice(c,3),"yes");
        clock.set(start.plus(Duration.ofDays(7)).plusSeconds(119).toString());
        var counted=new CountDownLatch(1);var release=new CountDownLatch(1);var closerStarted=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var writer=pool.submit(()->tx.executeWithoutResult(t->{service.processUser(alice.id());counted.countDown();
                try{if(!release.await(20,TimeUnit.SECONDS))throw new IllegalStateException("commit timeout");}catch(InterruptedException e){throw new RuntimeException(e);}}));
            assertThat(counted.await(15,TimeUnit.SECONDS)).isTrue();clock.set(start.plus(Duration.ofDays(7)).plusSeconds(120).toString());
            var closer=pool.submit(()->{closerStarted.countDown();lifecycle.finalizeGroup(id,group);});
            assertThat(closerStarted.await(5,TimeUnit.SECONDS)).isTrue();Thread.sleep(200);assertThat(closer.isDone()).isFalse();
            release.countDown();writer.get(20,TimeUnit.SECONDS);closer.get(20,TimeUnit.SECONDS);
        }finally{release.countDown();pool.shutdownNow();}
        var frozen=tx.execute(t->leagues.member(alice.id(),id).orElseThrow().getResult());assertThat(frozen.weeklyXp()).isEqualTo(15);
        long ledger=tx.execute(t->((Number)em.createNativeQuery("SELECT SUM(league_counted_xp) FROM xp_ledger WHERE user_id=:u").setParameter("u",alice.id()).getSingleResult()).longValue());
        assertThat(ledger).isEqualTo(frozen.weeklyXp());assertThat(summary(c).path("totalXp").asInt()).isEqualTo(15);
    }
    @Test void twoFullGroupsCreateOnlyOneNextGroupDuringConcurrentAllocation()throws Exception{
        week();long id=season();tx.executeWithoutResult(t->{leagues.lockSeason(id);leagues.lockAllocator(id,Tier.BRONZE);
            for(int n=1;n<=2;n++){var g=leagues.save(new LeagueGroupEntity(id,Tier.BRONZE,n));for(int i=0;i<25;i++){
                var u=users.create(key()+"@allocator.test",hash,"Allocator fixture",Role.STUDENT);leagues.save(new LeagueMemberEntity(u.id(),g,clock.instant()));g.addMember();}leagues.save(g);}});
        var a=new Client().login(alice);var b=new Client().login(bob);var pool=Executors.newFixedThreadPool(2);
        try{var one=pool.submit(()->join(a));var two=pool.submit(()->join(b));long g=one.get(30,TimeUnit.SECONDS).path("membership").path("groupId").asLong();
            assertThat(two.get(30,TimeUnit.SECONDS).path("membership").path("groupId").asLong()).isEqualTo(g);
            assertThat((int)tx.execute(t->leagues.group(g).getGroupNo())).isEqualTo(3);assertThat(board(a).path("totalElements").asInt()).isEqualTo(2);
        }finally{pool.shutdownNow();}
    }
    @Test void exactWeekEndBelongsToTheNewSeasonWhileThePriorMicrosecondBelongsToTheOld()throws Exception{
        week();var a=new Client().login(alice);var b=new Client().login(bob);long old=season();group(a);
        var end=start.plus(Duration.ofDays(7));clock.set(end.minusNanos(1000).toString());var prior=submit(a,practice(a,3),"yes");
        clock.set(end.toString());lifecycle.ensureCurrent();join(b);var atEnd=submit(b,practice(b,3),"yes");sync(a);sync(b);
        var oldActivity=ok(a.call("GET","/api/me/gamification/activities?submissionId="+prior.path("submissionId").asLong(),null),200).path("items").get(0);
        var newActivity=ok(b.call("GET","/api/me/gamification/activities?submissionId="+atEnd.path("submissionId").asLong(),null),200).path("items").get(0);
        assertThat(oldActivity.path("league").path("seasonId").asLong()).isEqualTo(old);assertThat(newActivity.path("league").path("seasonId").asLong()).isNotEqualTo(old);
        assertThat(oldActivity.path("league").path("countedXp").asInt()).isEqualTo(15);assertThat(newActivity.path("league").path("countedXp").asInt()).isEqualTo(15);
    }
    @Test void aNewWorkerResumesAfterOneGroupCommittedAndTheNextGroupFailed()throws Exception{
        week();long id=season();var a=new Client().login(alice);long first=group(a);
        long second=tx.execute(t->{leagues.lockSeason(id);leagues.lockAllocator(id,Tier.SILVER);var g=leagues.save(new LeagueGroupEntity(id,Tier.SILVER,1));
            leagues.save(new LeagueMemberEntity(bob.id(),g,clock.instant()));g.addMember();leagues.save(g);return g.getId();});
        clock.set(start.plus(Duration.ofDays(7)).plusSeconds(120).toString());lifecycle.finalizeGroup(id,first);
        var frozen=tx.execute(t->leagues.member(alice.id(),id).orElseThrow().getResult());
        doAnswer(call->{if(call.getArgument(0) instanceof LeagueOutboxEntity event&&Objects.equals(event.getGroupId(),second))throw new org.springframework.dao.DataAccessResourceFailureException("resume group failure");return call.callRealMethod();}).when(leagues).save(any());
        try{assertThatThrownBy(()->lifecycle.finalizeGroup(id,second)).isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);}finally{reset(leagues);}
        assertThat((Object)tx.execute(t->leagues.member(bob.id(),id).orElseThrow().getResult())).isNull();
        var dispatcher=new LeagueEventDispatcher(leagues,org.mockito.Mockito.mock(vn.edu.ptit.english.security.LiveConnections.class),json,clock);
        new LeagueWorker(lifecycle,dispatcher).tick();
        assertThat((Object)tx.execute(t->leagues.member(alice.id(),id).orElseThrow().getResult())).isEqualTo(frozen);
        assertThat((Object)tx.execute(t->leagues.member(bob.id(),id).orElseThrow().getResult())).isNotNull();
        assertThat((String)tx.execute(t->leagues.season(id).getStatus())).isEqualTo("FINALIZED");
    }
}
