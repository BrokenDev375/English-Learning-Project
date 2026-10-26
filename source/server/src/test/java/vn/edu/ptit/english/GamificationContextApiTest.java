package vn.edu.ptit.english;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.edu.ptit.english.entity.ReviewRewardContextEntity;
import vn.edu.ptit.english.repository.GamificationContextRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Reuses the real M04 HTTP fixture and also inherits its regression scenarios. */
class GamificationContextApiTest extends PersonalReviewApiTest {
    @MockitoSpyBean GamificationContextRepository rewards;

    @Test void reviewContextSurvivesCreateReplayAndResumeAndIsOwnerScoped() throws Exception {
        var c=new Client().login(alice);
        submit(c,learn(c,lessonA),"yes"); sync(c);
        Instant due=Instant.now().minusSeconds(86400).truncatedTo(ChronoUnit.MICROS);
        tx.executeWithoutResult(t->em.createNativeQuery("UPDATE review_objective_progress SET next_review_at=:due WHERE user_id=:u AND objective_id=:o")
            .setParameter("due",Timestamp.from(due)).setParameter("u",alice.id()).setParameter("o",objectiveA).executeUpdate());
        var command=review("DUE",5);
        var created=ok(c.call("POST","/api/attempts",command),201);
        long id=created.path("attemptId").asLong();
        var before=tx.execute(t->rewards.contexts(id,alice.id()));
        assertThat(before).hasSize(1); var context=before.get(0);
        assertThat(context.isEligible()).isTrue(); assertThat(context.getDueAt()).isEqualTo(due);
        assertThat(context.getPolicyVersion()).isEqualTo("m05-p0-v2");
        ok(c.call("POST","/api/attempts",command),201);
        assertThat(ok(c.call("POST","/api/attempts",review("DUE",5)),200).path("attemptId").asLong()).isEqualTo(id);
        tx.executeWithoutResult(t->{
            var rows=rewards.contexts(id,alice.id()); assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getCycleKey()).isEqualTo(context.getCycleKey());
            assertThat(rows.get(0).getSelectedAt()).isEqualTo(context.getSelectedAt());
            assertThat(rewards.contexts(id,bob.id())).isEmpty();
        });
    }

    @Test void immediateMistakeReviewIsRecordedWithoutDueBonusEligibility() throws Exception {
        var c=new Client().login(alice); submit(c,learn(c,lessonA),"no"); sync(c);
        long id=ok(c.call("POST","/api/attempts",review("MISTAKES",5)),201).path("attemptId").asLong();
        tx.executeWithoutResult(t->{var contexts=rewards.contexts(id,alice.id());
            assertThat(contexts).hasSize(1); assertThat(contexts.get(0).isEligible()).isFalse();
            assertThat(contexts.get(0).getStage()).isZero();});
    }

    @Test void contextFailureRollsBackAttemptExposureAndReceiptAndAllowsRetry() throws Exception {
        var c=new Client().login(alice); submit(c,learn(c,lessonA),"no"); sync(c);
        long before=tx.execute(t->em.createQuery("select count(a) from AttemptEntity a where a.userId=:u",Long.class)
            .setParameter("u",alice.id()).getSingleResult());
        long exposure=tx.execute(t->((Number)em.createNativeQuery("SELECT SUM(seen_count) FROM question_exposures WHERE user_id=:u")
            .setParameter("u",alice.id()).getSingleResult()).longValue());
        var command=review("DUE",5);
        doThrow(new DataAccessResourceFailureException("injected context storage failure")).when(rewards).save(any(ReviewRewardContextEntity.class));
        try { assertThat(ok(c.call("POST","/api/attempts",command),503).path("code").asText()).isEqualTo("DATABASE_UNAVAILABLE"); }
        finally { reset(rewards); }
        tx.executeWithoutResult(t->{
            assertThat(em.createQuery("select count(a) from AttemptEntity a where a.userId=:u",Long.class).setParameter("u",alice.id()).getSingleResult()).isEqualTo(before);
            assertThat(((Number)em.createNativeQuery("SELECT SUM(seen_count) FROM question_exposures WHERE user_id=:u").setParameter("u",alice.id()).getSingleResult()).longValue()).isEqualTo(exposure);
        });
        ok(c.call("POST","/api/attempts",command),201);
    }
}
