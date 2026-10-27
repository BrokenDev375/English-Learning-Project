package vn.edu.ptit.english.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeeklyLeaderboardRepository {
    record Score(long userId, String displayName, long totalXp, long weeklyXp, long rank, long position) {}
    List<Score> page(LocalDate start, LocalDate end, long offset, int size);
    Optional<Score> self(LocalDate start, LocalDate end, long userId);
    long count(LocalDate start, LocalDate end);
}
