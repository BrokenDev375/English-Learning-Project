package vn.edu.ptit.english;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static vn.edu.ptit.english.service.impl.GamificationReviewContextRecorder.cycleKey;

class GamificationCycleKeyTest {
    @Test void keyUsesDatabaseMicrosecondsAndStableScheduleIdentity() {
        var at=Instant.parse("2026-10-21T00:00:00.123456Z");
        assertThat(cycleKey(1,2,at,1)).hasSize(64).isEqualTo(cycleKey(1,2,at.plusNanos(999),1));
        assertThat(cycleKey(1,2,at,1)).isNotEqualTo(cycleKey(2,2,at,1))
            .isNotEqualTo(cycleKey(1,3,at,1)).isNotEqualTo(cycleKey(1,2,at,2))
            .isNotEqualTo(cycleKey(1,2,at.plusNanos(1000),1));
    }
}
