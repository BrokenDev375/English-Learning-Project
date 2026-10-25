package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.edu.ptit.english.service.impl.LeagueLifecycle;
import static org.assertj.core.api.Assertions.*;

class StatisticsRewardsIntegrationTest extends GamificationFixture {
    @Autowired LeagueLifecycle lifecycle;
    JsonNode profile(Client c) throws Exception { return ok(c.call("GET", "/api/me/statistics", null), 200).path("rewards"); }
    Map<String, Long> persistedRows() {
        var result = new LinkedHashMap<String, Long>();
        for (var table : List.of("xp_ledger", "gamification_reward_claims", "gamification_profiles", "gamification_daily_stats",
            "gamification_processed_submissions", "gamification_outbox", "review_processed_submissions")) {
            result.put(table, count(table));
        }
        result.put("request_receipts", tx.execute(t -> ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM request_receipts WHERE actor_id=:actor").setParameter("actor", alice.id()).getSingleResult()).longValue()));
        return result;
    }
    @Test void profileReadsCommittedRewardsWithoutProcessingPendingWorkOrMixingUsers() throws Exception {
        var c = new Client().login(alice);
        submit(c, practice(c, 3), "yes");
        assertThat(profile(c).path("totalXp").asLong()).isZero();
        assertThat(count("gamification_processed_submissions")).isZero();
        service.processUser(alice.id());
        var expected = summary(c);
        var before = persistedRows();
        var actual = profile(c);
        assertThat(actual.path("state").asText()).isEqualTo("AVAILABLE");
        assertThat(actual.path("totalXp")).isEqualTo(expected.path("totalXp"));
        assertThat(actual.path("totalXp").asLong()).isEqualTo(15);
        assertThat(actual.path("level")).isEqualTo(expected.path("activityLevel"));
        assertThat(actual.path("streakDays")).isEqualTo(expected.path("streak").path("current"));
        assertThat(actual.path("currentWeek").isNull()).isTrue();
        assertThat(actual.path("history").size()).isZero();
        profile(c); assertThat(persistedRows()).isEqualTo(before);
        assertThat(profile(new Client().login(bob)).path("totalXp").asLong()).isZero();
        ok(new Client().login(teacher).call("GET", "/api/me/statistics", null), 403);
    }
    @Test void profileUsesM05CurrentPlacementAndFrozenHistoryWithoutJoiningOnGet() throws Exception {
        var c = new Client().login(alice); lifecycle.ensureCurrent();
        assertThat(profile(c).path("currentWeek").isNull()).isTrue();
        var joined = ok(c.call("POST", "/api/me/league/join", Map.of("requestId", key())), 200);
        submit(c, practice(c, 3), "yes"); service.processUser(alice.id());
        var league = ok(c.call("GET", "/api/me/league", null), 200);
        var actual = profile(c).path("currentWeek");
        assertThat(actual.path("xp")).isEqualTo(league.path("membership").path("weeklyXp"));
        assertThat(actual.path("position")).isEqualTo(league.path("membership").path("rank"));
        assertThat(actual.path("tier")).isEqualTo(league.path("membership").path("tier"));
        var cutoff = Instant.parse(joined.path("season").path("cutoffAt").asText());
        clock.set(cutoff.plusSeconds(1).toString());
        lifecycle.finalizeGroup(joined.path("season").path("id").asLong(), joined.path("membership").path("groupId").asLong());
        lifecycle.ensureCurrent();
        actual = profile(c);
        assertThat(actual.path("currentWeek").isNull()).isTrue();
        var history = ok(c.call("GET", "/api/me/league/history", null), 200).path("items").get(0);
        var item = actual.path("history").get(0);
        assertThat(item.path("xp")).isEqualTo(history.path("result").path("weeklyXp"));
        assertThat(item.path("position")).isEqualTo(history.path("result").path("rank"));
        assertThat(item.path("tier")).isEqualTo(history.path("result").path("tierBefore"));
    }
    @Test void profileShowsOnlyBadgesAlreadyEarnedByM05() throws Exception {
        var c = new Client().login(alice);
        assertThat(profile(c).path("badges").size()).isZero();
        var snapshot = ok(c.call("POST", "/api/attempts", Map.of("requestId", key(), "mode", "LEARN", "lessonId", lesson)), 201);
        var item = snapshot.path("items").get(0);
        ok(c.call("POST", "/api/attempts/" + snapshot.path("attemptId").asLong() + "/items/" + item.path("attemptQuestionId").asLong() + "/check", Map.of(
            "requestId", key(), "baseRevision", snapshot.path("revision").asLong(),
            "response", Map.of("type", "SINGLE_CHOICE", "optionId", "yes"))), 200);
        snapshot = ok(c.call("GET", "/api/attempts/" + snapshot.path("attemptId").asLong(), null), 200);
        submit(c, snapshot, "yes"); service.processUser(alice.id());
        var actual = profile(c).path("badges");
        assertThat(actual.size()).isEqualTo(1);
        assertThat(actual.get(0).path("code").asText()).isEqualTo("FIRST_LESSON");
        var expected = ok(c.call("GET", "/api/me/badges", null), 200).path("items").get(0);
        assertThat(actual.get(0).path("name")).isEqualTo(expected.path("name"));
        assertThat(actual.get(0).path("earnedAt")).isEqualTo(expected.path("earnedAt"));
    }
}
