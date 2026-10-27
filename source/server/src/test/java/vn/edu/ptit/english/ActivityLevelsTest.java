package vn.edu.ptit.english;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.edu.ptit.english.service.impl.ActivityLevels;
import static org.assertj.core.api.Assertions.*;

class ActivityLevelsTest {
    @ParameterizedTest
    @CsvSource({"0,1,0,10", "9,1,0,10", "10,2,10,30", "29,2,10,30", "30,3,30,60",
        "59,3,30,60", "60,4,60,100", "99,4,60,100", "100,5,100,150", "149,5,100,150",
        "150,6,150,210", "200,6,150,210", "210,7,210,280", "450,10,450,550", "550,11,550,660"})
    void exactThresholdsAndTheirPrecedingXp(long xp, long value, long floor, long next) {
        assertThat(ActivityLevels.progressive(xp)).isEqualTo(new ActivityLevels.Level(value, floor, next));
    }
    @Test void higherLevelsRequireMoreXpAndLargeTotalsStayInTheCorrectInterval() {
        long previousCost = 0;
        for (int level = 1; level <= 100; level++) {
            var state = ActivityLevels.progressive(5L * level * (level - 1));
            assertThat(state.value()).isEqualTo(level);
            long cost = state.nextLevelAtXp() - state.floorXp();
            assertThat(cost).isGreaterThan(previousCost); previousCost = cost;
        }
        for (long xp : new long[]{1_000_000, Integer.MAX_VALUE, ActivityLevels.MAX_XP - 1, ActivityLevels.MAX_XP}) {
            var state = ActivityLevels.progressive(xp);
            assertThat(state.floorXp()).isLessThanOrEqualTo(xp);
            assertThat(state.nextLevelAtXp()).isGreaterThanOrEqualTo(xp).isLessThanOrEqualTo(ActivityLevels.MAX_XP);
            assertThat(5 * state.value() * (state.value() + 1)).isGreaterThan(xp);
        }
    }
    @Test void oldFixedPoliciesKeepTheirBoundariesAndInvalidXpIsRejected() {
        assertThat(ActivityLevels.fixed(99, 100)).isEqualTo(new ActivityLevels.Level(1, 0, 100));
        assertThat(ActivityLevels.fixed(100, 100)).isEqualTo(new ActivityLevels.Level(2, 100, 200));
        assertThat(ActivityLevels.fixed(49, 50)).isEqualTo(new ActivityLevels.Level(1, 0, 50));
        assertThat(ActivityLevels.fixed(50, 50)).isEqualTo(new ActivityLevels.Level(2, 50, 100));
        assertThatThrownBy(() -> ActivityLevels.progressive(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ActivityLevels.progressive(ActivityLevels.MAX_XP + 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
