package vn.edu.ptit.english.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;

public final class WeeklyLeaderboardViews {
    private WeeklyLeaderboardViews() {}
    // position only orders display rows within ties; equal XP always shares rank.
    public record Member(Long position, Long rank, String displayName, long activityLevel, long weeklyXp, boolean isSelf) {}
    public record Board(@JsonFormat(shape=JsonFormat.Shape.STRING) Instant serverTime, String zone,
        @JsonFormat(shape=JsonFormat.Shape.STRING) Instant weekStartAt,
        @JsonFormat(shape=JsonFormat.Shape.STRING) Instant weekEndAt,
        List<Member> top, List<Member> items, Member self, long totalElements, int page, int size, long totalPages) {
        public Board { top=List.copyOf(top); items=List.copyOf(items); }
    }
}
