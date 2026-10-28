package vn.edu.ptit.english.dto;

import java.time.Instant;
import java.util.List;
import vn.edu.ptit.english.dto.CatalogViews.CatalogPage;
import vn.edu.ptit.english.enums.Role;

/** Read projections only. Never include answers, keys, sessions or storage paths. */
public final class StatisticsViews {
    private StatisticsViews() {}
    public record Metric(long total, long correct, long incorrect, long unanswered, Double accuracyPercent) {
        public static Metric of(long correct, long incorrect, long unanswered) {
            long answered = correct + incorrect;
            return new Metric(answered + unanswered, correct, incorrect, unanswered,
                answered == 0 ? null : Math.round(10000.0 * correct / answered) / 100.0);
        }
    }
    public record Performance(Metric first, Metric guided, Metric assisted, Metric independentNew,
                              Metric independentSeen, Metric guidedRetries, Metric corrections) {}
    public record ModeCount(String mode, long submissions) {}
    public record Activity(long submittedAttempts, long activeAttempts, long completedLessons,
                           long learnersWithSubmissions, Instant lastSubmittedAt, List<ModeCount> modes) {}
    public record Review(long trackedObjectives, long needsReview, long mistakeObjectives,
                         long pendingSubmissions, Instant nextScheduledAt) {}
    public record Badge(String code, String name, Instant earnedAt) {}
    public record LeagueWeek(String week, String tier, Integer position, Long xp) {}
    public record Rewards(String state, Long totalXp, Long level, Integer streakDays,
                          List<Badge> badges, LeagueWeek currentWeek, List<LeagueWeek> history) {
        public static Rewards unavailable() { return new Rewards("UNAVAILABLE", null, null, null, null, null, null); }
    }
    public record LearnerOverview(Instant generatedAt, String period, Instant from, Activity activity,
                                  Performance performance, Review review, Rewards rewards) {}
    public record Breakdown(long id, String name, long topicId, Performance performance) {}
    public record Lesson(long lessonId, long lessonVersionId, String title, String state, Long lastAttemptId,
                          Instant completedAt, Double lastScorePercent) {}
    public record Count(String label, long count) {}
    public record Difficulty(String difficulty, Performance performance) {}
    public record Presence(long connectedAccounts, long openConnections, String basis) {}
    public record OnlineAccount(long id, String fullName, Role role) {}
    public record OnlinePresence(Instant generatedAt, long connectedAccounts, long openConnections, String basis, List<OnlineAccount> users) {}
    public record AdminOverview(Instant generatedAt, String period, Instant from, Activity activity,
                                Performance performance, List<Count> roles, List<Count> statuses,
                                List<Difficulty> difficulties, Presence presence) {}
    public record Question(long questionId, long questionVersionId, int versionNo, String prompt, String type,
                            String difficulty, long learners, Metric first) {}
    public record TeacherUnit(long unitId, String title, int stage, int sortOrder, int roundNumber) {}
    public record TeacherOverview(Instant generatedAt, String period, Instant from, Long unitId, long units,
                                  long learnersStarted, Activity activity, Performance performance,
                                  long hintOpenedQuestions, List<Difficulty> difficulties) {}
    public record TeacherLesson(long lessonId, long unitId, String unitTitle, String title,
                                long learnersStarted, long activeAttempts, long submittedAttempts,
                                long completedLearners, long hintOpenedQuestions, Performance performance) {}
}
