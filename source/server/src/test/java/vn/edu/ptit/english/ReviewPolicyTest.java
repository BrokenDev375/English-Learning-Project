package vn.edu.ptit.english;

import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.service.impl.ReviewPolicy;
import static org.assertj.core.api.Assertions.*;

class ReviewPolicyTest {
    private final List<Integer> days = List.of(1, 3, 7);
    private final ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
    private final Instant day = Instant.parse("2026-10-20T10:00:00Z");
    private void good(ReviewProgressEntity p, long id, Instant at) {
        p.record(id, at, 10, 10, 0, 0, 0, 10, 10, "EASY", days, zone);
    }
    @Test void tenCorrectQuestionsAdvanceOneStageAndOneDayOnly() {
        var p = new ReviewProgressEntity(1, 2);
        good(p, 1, day); good(p, 2, day.plusSeconds(60));
        assertThat(p.getStage()).isEqualTo(1);
        assertThat(p.getNextReviewAt()).isEqualTo(day.plusSeconds(60).plus(Duration.ofDays(1)));
        assertThat(p.isNeedsPractice()).isFalse();
    }
    @Test void independentSuccessOnDifferentDaysUsesOneThreeSeven() {
        var p = new ReviewProgressEntity(1, 2);
        good(p, 1, day); good(p, 2, day.plus(Duration.ofDays(1)));
        assertThat(p.getStage()).isEqualTo(2);
        assertThat(p.getNextReviewAt()).isEqualTo(day.plus(Duration.ofDays(4)));
        good(p, 3, day.plus(Duration.ofDays(4)));
        assertThat(p.getStage()).isEqualTo(3);
        assertThat(p.getNextReviewAt()).isEqualTo(day.plus(Duration.ofDays(11)));
    }
    @Test void mixedObjectiveResultResetsOnceAndBecomesImmediatelyDue() {
        var p = new ReviewProgressEntity(1, 2); good(p, 1, day);
        p.record(2, day.plusSeconds(10), 5, 4, 0, 0, 1, 5, 4, "EASY", days, zone);
        assertThat(p.getStage()).isZero();
        assertThat(p.isHasMistakes()).isTrue();
        assertThat(p.getNextReviewAt()).isEqualTo(day.plusSeconds(10));
    }
    @Test void guidedCorrectAndBlankCannotEraseAnUnresolvedMistake() {
        var p = new ReviewProgressEntity(1, 2);
        p.record(1, day, 1, 0, 0, 0, 1, 1, 0, "EASY", days, zone);
        p.record(2, day.plusSeconds(20), 1, 1, 1, 0, 0, 0, 0, null, days, zone);
        p.record(3, day.plusSeconds(40), 0, 0, 0, 2, 0, 0, 0, null, days, zone);
        assertThat(p.isHasMistakes()).isTrue();
        assertThat(p.isNeedsPractice()).isTrue();
        assertThat(p.getStage()).isZero();
        assertThat(p.getUnansweredCount()).isEqualTo(2);
    }
    @Test void blankAfterSuccessDoesNotAdvanceOrResetTheSchedule() {
        var p = new ReviewProgressEntity(1, 2); good(p, 1, day);
        p.record(2, day.plusSeconds(40), 0, 0, 0, 3, 0, 0, 0, null, days, zone);
        assertThat(p.getStage()).isEqualTo(1);
        assertThat(p.getNextReviewAt()).isEqualTo(day.plus(Duration.ofDays(1)));
        assertThat(p.isNeedsPractice()).isFalse();
    }
    @Test void smallFreshSetsDoNotQualifyAndFailedSetResetsGoodStreak() {
        var e = new ReviewEvidenceEntity(1, 2, "EASY");
        e.record(2, 2, 3, 80);
        assertThat(e.getFreshSetCount()).isZero();
        e.record(5, 4, 3, 80); e.record(5, 3, 3, 80);
        assertThat(e.getFreshSetCount()).isEqualTo(2);
        assertThat(e.getGoodSetStreak()).isZero();
        assertThat(e.getFreshAnsweredCount()).isEqualTo(12);
    }
    @Test void policyRejectsOneSetPromotionAndInvalidIntervals() {
        assertThatThrownBy(() -> new ReviewPolicy(days, 3, 80, 1, zone.toString())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReviewPolicy(List.of(3, 1, 7), 3, 80, 2, zone.toString())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void dayBoundaryUsesVietnameseCalendar() {
        var p = new ReviewProgressEntity(1, 2);
        good(p, 1, Instant.parse("2026-10-20T16:59:59Z"));
        good(p, 2, Instant.parse("2026-10-20T17:00:01Z"));
        assertThat(p.getStage()).isEqualTo(2);
    }
}
