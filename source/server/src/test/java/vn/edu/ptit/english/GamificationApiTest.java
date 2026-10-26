package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import jakarta.persistence.EntityManager;
import java.net.*;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.dto.AuthoringData.*;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.service.GamificationService;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GamificationApiTest extends GamificationFixture {
    @Test void readEndpointsHaveNoWriteSideEffectsAndRejectForeignDataAndRoles()throws Exception {
        ok(new Client().call("GET","/api/me/gamification",null),401); var c=new Client().login(alice);
        var response=c.call("GET","/api/me/gamification",null); var body=ok(response,200);
        assertThat(body.path("totalXp").asLong()).isZero(); assertThat(body.path("activityLevel").asInt()).isEqualTo(1);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store","private");
        ok(c.call("GET","/api/me/badges",null),200); ok(c.call("GET","/api/me/gamification/activities",null),200);
        assertThat(count("gamification_profiles")).isZero(); assertThat(count("gamification_daily_stats")).isZero();
        ok(c.call("GET","/api/me/gamification?userId=2",null),400);
        ok(c.call("GET","/api/me/gamification/activities?size=51",null),400);
        ok(c.call("POST","/api/me/gamification/sync",Map.of("requestId",key(),"xp",500)),400);
        ok(new Client().login(teacher).call("GET","/api/me/gamification",null),403);
    }
    @Test void levelPaceChangesWithoutMintingXpAndLateSourcesKeepTheirSubmissionTimePolicy()throws Exception {
        tx.executeWithoutResult(t->{
            em.createNativeQuery("UPDATE gamification_policies SET effective_at='2026-10-22 00:00:00' WHERE version='m05-p0-v2'").executeUpdate();
            em.createNativeQuery("UPDATE gamification_policies SET effective_at='2026-10-01 00:00:00' WHERE version='m05-p0-v1'").executeUpdate();
        });
        var c=new Client().login(alice);clock.set("2026-10-21T12:00:00Z");var old=submit(c,practice(c,5),"yes");
        clock.set("2026-10-22T12:00:00Z");sync(c);
        var state=summary(c);assertThat(state.path("policyVersion").asText()).isEqualTo("m05-p0-v2");assertThat(state.path("totalXp").asInt()).isEqualTo(25);assertThat(state.path("activityLevel").asInt()).isEqualTo(1);assertThat(state.path("nextLevelAtXp").asInt()).isEqualTo(50);
        var fact=ok(c.call("GET","/api/me/gamification/activities?submissionId="+old.path("submissionId").asLong(),null),200).path("items").get(0);
        assertThat(fact.path("policyVersion").asText()).isEqualTo("m05-p0-v1");
        tx.executeWithoutResult(t->bank(5));submit(c,practice(c,5),"yes");sync(c);
        state=summary(c);assertThat(state.path("totalXp").asInt()).isEqualTo(50);assertThat(state.path("activityLevel").asInt()).isEqualTo(2);assertThat(state.path("levelFloorXp").asInt()).isEqualTo(50);assertThat(state.path("nextLevelAtXp").asInt()).isEqualTo(100);
        long ledger=count("xp_ledger");summary(c);sync(c);assertThat(count("xp_ledger")).isEqualTo(ledger);assertThat(summary(c).path("totalXp").asInt()).isEqualTo(50);
        clock.set("2026-10-21T12:00:00Z");assertThat(summary(c).path("activityLevel").asInt()).isEqualTo(1);assertThat(summary(c).path("nextLevelAtXp").asInt()).isEqualTo(100);
    }
    @Test void freshAnswersAreAwardedOnceAndReceiptReplayKeepsOriginalBody()throws Exception {
        var c=new Client().login(alice); var result=submit(c,practice(c,5),"yes");
        assertThat(summary(c).path("backlog").path("pendingSubmissions").asInt()).isEqualTo(1);
        var command=Map.of("requestId",key()); var first=c.call("POST","/api/me/gamification/sync",command); var body=ok(first,200);
        var replay=c.call("POST","/api/me/gamification/sync",command); assertThat(ok(replay,200)).isEqualTo(body);
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true"); sync(c); service.processUser(alice.id());
        assertThat(summary(c).path("totalXp").asInt()).isEqualTo(25); assertThat(count("xp_ledger")).isEqualTo(5);
        assertThat(count("gamification_outbox")).isEqualTo(1); assertThat(count("gamification_processed_submissions")).isEqualTo(1);
        ok(new Client().login(bob).call("GET","/api/me/gamification/activities?submissionId="+result.path("submissionId").asLong(),null),404);
    }
    @Test void workerProcessesWithoutOpeningRewardPageAndWrongCorrectionDoesNotAddXp()throws Exception {
        var c=new Client().login(alice); var snapshot=practice(c,3); submit(c,snapshot,"no"); service.processUser(alice.id());
        assertThat(summary(c).path("totalXp").asInt()).isZero(); assertThat(summary(c).path("day").path("isActive").asBoolean()).isFalse();
        var latest=ok(c.call("GET","/api/attempts/"+snapshot.path("attemptId").asLong(),null),200);
        ok(c.call("POST","/api/attempts/"+snapshot.path("attemptId").asLong()+"/items/"+snapshot.path("items").get(0).path("attemptQuestionId").asLong()+"/retry",
            Map.of("requestId",key(),"baseRevision",latest.path("revision").asLong(),"response",Map.of("type","SINGLE_CHOICE","optionId","yes"))),200);
        sync(c); assertThat(summary(c).path("totalXp").asInt()).isZero(); assertThat(count("gamification_processed_submissions")).isEqualTo(1);
    }
    @Test void capIsSharedAndOldQuestionsStillKeepStreakWithoutCarryingXp()throws Exception {
        tx.executeWithoutResult(t->bank(40)); var c=new Client().login(alice);
        for(int i=0;i<5;i++){submit(c,practice(c,10),"yes");sync(c);}
        var state=summary(c); assertThat(state.path("totalXp").asInt()).isEqualTo(200); assertThat(state.path("day").path("awardedXp").asInt()).isEqualTo(200);
        assertThat(count("gamification_reward_claims")).isEqualTo(46);
        ok(c.call("POST","/api/me/review/sync",Map.of("requestId",key())),200);
        tx.executeWithoutResult(t->em.createNativeQuery("UPDATE review_objective_progress SET next_review_at='2026-10-20' WHERE user_id=:u").setParameter("u",alice.id()).executeUpdate());
        var review=ok(c.call("POST","/api/attempts",Map.of("requestId",key(),"mode","REVIEW","reviewKind","DUE","topicId",topic,"count",3)),201);
        submit(c,review,"yes");sync(c); assertThat(count("user_badges")).isEqualTo(1);
        clock.set("2026-10-22T12:00:00Z"); submit(c,practice(c,3),"yes"); sync(c);
        state=summary(c); assertThat(state.path("totalXp").asInt()).isEqualTo(200); assertThat(state.path("day").path("awardedXp").asInt()).isZero();
        assertThat(state.path("streak").path("current").asInt()).isEqualTo(2);
    }
    @Test void outboxFailureRollsBackLedgerClaimsDayAndReceiptThenRetrySucceeds()throws Exception {
        var c=new Client().login(alice); submit(c,practice(c,3),"yes"); var command=Map.of("requestId",key());
        doThrow(new DataAccessResourceFailureException("injected reward outbox failure")).when(rewards).save(any(GamificationOutboxEntity.class));
        try{ok(c.call("POST","/api/me/gamification/sync",command),503);}finally{reset(rewards);}
        for(String table:List.of("gamification_profiles","gamification_daily_stats","gamification_reward_claims","xp_ledger","gamification_processed_submissions","gamification_outbox")) assertThat(count(table)).as(table).isZero();
        ok(c.call("POST","/api/me/gamification/sync",command),200); assertThat(summary(c).path("totalXp").asInt()).isEqualTo(15);
    }
    @Test void concurrentBatchesDoNotDuplicateRewards()throws Exception {
        var a=new Client().login(alice); var b=new Client().login(alice); submit(a,practice(a,5),"yes");
        var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->a.call("POST","/api/me/gamification/sync",Map.of("requestId",key())));
            var second=pool.submit(()->b.call("POST","/api/me/gamification/sync",Map.of("requestId",key())));
            int processed=ok(first.get(30,TimeUnit.SECONDS),200).path("processedSubmissions").asInt()+ok(second.get(30,TimeUnit.SECONDS),200).path("processedSubmissions").asInt();
            assertThat(processed).isEqualTo(1);
        } finally {pool.shutdownNow();}
        assertThat(summary(a).path("totalXp").asInt()).isEqualTo(25); assertThat(count("xp_ledger")).isEqualTo(5);
    }
    @Test void beforeActivationIsRecordedButNotRewarded()throws Exception {
        var c=new Client().login(alice); clock.set("2026-09-30T12:00:00Z"); var source=submit(c,practice(c,3),"yes"); sync(c);
        var page=ok(c.call("GET","/api/me/gamification/activities?submissionId="+source.path("submissionId").asLong(),null),200);
        assertThat(page.path("requestedSubmission").path("state").asText()).isEqualTo("BEFORE_ACTIVATION");
        assertThat(count("xp_ledger")).isZero(); assertThat(count("gamification_daily_stats")).isZero();
    }
    @Test void streakRebuildHandlesOutOfOrderDaysAndIssuesSevenDayBadge()throws Exception {
        var c=new Client().login(alice);
        for(int day:new int[]{10,12,11}){clock.set("2026-10-"+day+"T12:00:00Z");submit(c,practice(c,3),"yes");sync(c);}
        clock.set("2026-10-12T12:00:00Z"); assertThat(summary(c).path("streak").path("current").asInt()).isEqualTo(3);
        clock.set("2026-10-14T12:00:00Z"); assertThat(summary(c).path("streak").path("current").asInt()).isZero();
        for(int day=13;day<=16;day++){clock.set("2026-10-"+day+"T12:00:00Z");submit(c,practice(c,3),"yes");sync(c);}
        var state=summary(c); assertThat(state.path("streak").path("current").asInt()).isEqualTo(7);
        var badges=ok(c.call("GET","/api/me/badges",null),200).path("items");
        assertThat(badges.findValuesAsText("code")).contains("SEVEN_DAY_STREAK");
        assertThat(count("user_badges")).isEqualTo(1);
    }
    @Test void dayBoundaryUsesVietnameseServerDate()throws Exception {
        var c=new Client().login(alice); clock.set("2026-10-21T16:59:59.999999Z");submit(c,practice(c,3),"yes");sync(c);
        clock.set("2026-10-21T17:00:00Z");submit(c,practice(c,3),"yes");sync(c);
        var state=summary(c); assertThat(state.path("day").path("date").asText()).isEqualTo("2026-10-22");
        assertThat(state.path("streak").path("current").asInt()).isEqualTo(2); assertThat(state.path("totalXp").asInt()).isEqualTo(30);
    }
    @Test void dueReviewReceivesFreshAndCycleRewardsAndBadge()throws Exception {
        var c=new Client().login(alice);submit(c,practice(c,3),"yes");sync(c);
        ok(c.call("POST","/api/me/review/sync",Map.of("requestId",key())),200);
        tx.executeWithoutResult(t->em.createNativeQuery("UPDATE review_objective_progress SET next_review_at=:d WHERE user_id=:u")
            .setParameter("d",Timestamp.from(Instant.parse("2026-10-20T00:00:00Z"))).setParameter("u",alice.id()).executeUpdate());
        var s=ok(c.call("POST","/api/attempts",Map.of("requestId",key(),"mode","REVIEW","reviewKind","DUE","topicId",topic,"count",3)),201);
        var source=submit(c,s,"yes"); sync(c);
        var activity=ok(c.call("GET","/api/me/gamification/activities?submissionId="+source.path("submissionId").asLong(),null),200).path("items").get(0);
        assertThat(activity.path("awardedXp").asInt()).isEqualTo(20); assertThat(count("user_badges")).isEqualTo(1);
    }
    @Test void lessonCompletionAwardsOnlyIndependentQuestionsAndFirstLessonBadge()throws Exception {
        var c=new Client().login(alice);
        var snapshot=ok(c.call("POST","/api/attempts",Map.of("requestId",key(),"mode","LEARN","lessonId",lesson)),201);
        var checked=ok(c.call("POST","/api/attempts/"+snapshot.path("attemptId").asLong()+"/items/"+snapshot.path("items").get(0).path("attemptQuestionId").asLong()+"/check",
            Map.of("requestId",key(),"baseRevision",snapshot.path("revision").asLong(),"response",Map.of("type","SINGLE_CHOICE","optionId","yes"))),200);
        ((ObjectNode)snapshot).put("revision",checked.path("revision").asLong());
        assertThat(submit(c,snapshot,"yes").path("lessonCompleted").asBoolean()).isTrue(); sync(c);
        assertThat(summary(c).path("totalXp").asInt()).isEqualTo(30); assertThat(count("user_badges")).isEqualTo(1);
    }
    @Test void unsupportedFactsAreDeferredWithoutFakeSuccessAndCanRecover()throws Exception {
        var c=new Client().login(alice); var source=submit(c,practice(c,3),"yes");long id=source.path("submissionId").asLong();
        tx.executeWithoutResult(t->em.createNativeQuery("UPDATE submissions SET result_json=JSON_SET(result_json,'$.factsSchemaVersion',2) WHERE id=:s").setParameter("s",id).executeUpdate());
        ok(c.call("POST","/api/me/gamification/sync",Map.of("requestId",key())),503);
        assertThat(count("gamification_processed_submissions")).isZero(); assertThat(summary(c).path("backlog").path("deferredSubmissions").asInt()).isEqualTo(1);
        tx.executeWithoutResult(t->{em.createNativeQuery("UPDATE submissions SET result_json=JSON_SET(result_json,'$.factsSchemaVersion',1) WHERE id=:s").setParameter("s",id).executeUpdate();
            em.createNativeQuery("UPDATE gamification_processing_failures SET retry_at='2026-10-01' WHERE submission_id=:s").setParameter("s",id).executeUpdate();});
        service.processUser(alice.id()); assertThat(summary(c).path("totalXp").asInt()).isEqualTo(15);assertThat(count("gamification_processing_failures")).isZero();
    }
}
