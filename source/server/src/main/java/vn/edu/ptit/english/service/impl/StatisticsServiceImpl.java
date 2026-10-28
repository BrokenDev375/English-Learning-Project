package vn.edu.ptit.english.service.impl;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.ptit.english.dto.StatisticsViews.*;
import vn.edu.ptit.english.dto.CatalogViews.CatalogPage;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.StatisticsRepository;
import vn.edu.ptit.english.repository.StatisticsRepository.Row;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.*;
import static vn.edu.ptit.english.dto.MaterialRequests.invalid;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class StatisticsServiceImpl implements StatisticsService {
    private final StatisticsRepository repository;
    private final AccountGuard accounts;
    private final LiveConnections connections;
    private final ObjectProvider<LearnerRewardsReader> rewards;
    public StatisticsServiceImpl(StatisticsRepository repository, AccountGuard accounts, LiveConnections connections,
                                 ObjectProvider<LearnerRewardsReader> rewards) {
        this.repository = repository; this.accounts = accounts; this.connections = connections; this.rewards = rewards;
    }
    private void require(Role role, UserPrincipal actor) {
        if (actor.role() != role) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền xem thống kê này.");
        accounts.requireCurrent(actor);
    }
    private Instant since(String period, Instant now) {
        return switch (period) {
            case "ALL" -> Instant.EPOCH;
            case "7D" -> now.minus(Duration.ofDays(7));
            case "30D" -> now.minus(Duration.ofDays(30));
            case "90D" -> now.minus(Duration.ofDays(90));
            default -> throw invalid("period", "Chọn ALL, 7D, 30D hoặc 90D.");
        };
    }
    private void pagination(int page, int size) {
        if (page < 0 || size < 1 || size > 50 || (long) page * size > Integer.MAX_VALUE)
            throw invalid("page", "Phân trang không hợp lệ.");
    }
    private Metric metric(List<Row> rows, String phase, String segment) {
        long correct = 0, incorrect = 0, blank = 0;
        for (var row : rows) if (row.phase().equals(phase) && (segment == null || row.segment().equals(segment))) {
            switch (row.outcome()) {
                case "CORRECT" -> correct += row.count();
                case "INCORRECT" -> incorrect += row.count();
                case "UNANSWERED" -> blank += row.count();
                default -> throw new IllegalStateException("Unknown persisted outcome");
            }
        }
        return Metric.of(correct, incorrect, blank);
    }
    private Performance performance(List<Row> rows) {
        return new Performance(metric(rows, "FIRST", null), metric(rows, "FIRST", "GUIDED"),
            metric(rows, "FIRST", "ASSISTED"), metric(rows, "FIRST", "INDEPENDENT_NEW"), metric(rows, "FIRST", "INDEPENDENT_SEEN"),
            metric(rows, "GUIDED_RETRY", null), metric(rows, "CORRECTION", null));
    }
    @Override public LearnerOverview learner(String period, UserPrincipal actor) {
        require(Role.STUDENT, actor); Instant now = Instant.now(), from = since(period, now);
        var reader = rewards.getIfAvailable();
        var reward = reader == null ? Rewards.unavailable() : Objects.requireNonNull(reader.read(actor));
        return new LearnerOverview(now, period, from, repository.activity(actor.id(), from, now),
            performance(repository.performance(actor.id(), from, now, null, null)), repository.review(actor.id(), now), reward);
    }
    @Override public CatalogPage<Breakdown> breakdown(String period, String groupBy, int page, int size, UserPrincipal actor) {
        require(Role.STUDENT, actor); pagination(page, size);
        if (!Set.of("TOPIC", "OBJECTIVE").contains(groupBy)) throw invalid("groupBy", "Chọn TOPIC hoặc OBJECTIVE.");
        Instant now = Instant.now(), from = since(period, now);
        var groups = repository.dimensions(actor.id(), from, now, groupBy, page, size);
        var items = groups.items().stream().map(g -> new Breakdown(g.id(), g.name(), g.topicId(),
            performance(repository.performance(actor.id(), from, now, groupBy, g.id())))).toList();
        return new CatalogPage<>(items, groups.totalElements(), page, size, groups.totalPages());
    }
    @Override public CatalogPage<Lesson> lessons(int page, int size, UserPrincipal actor) {
        require(Role.STUDENT, actor); pagination(page, size); return repository.lessons(actor.id(), page, size);
    }
    @Override public OnlinePresence presence(UserPrincipal actor) {
        require(Role.ADMIN, actor);
        var live = connections.onlineSnapshot();
        var users = repository.onlineAccounts(live.userIds());
        return new OnlinePresence(Instant.now(), users.size(), live.openConnections(), "OPEN_AUTHENTICATED_SOCKETS", users);
    }
    @Override public AdminOverview admin(String period, UserPrincipal actor) {
        require(Role.ADMIN, actor); Instant now = Instant.now(), from = since(period, now);
        var difficulties = List.of("EASY", "MEDIUM", "HARD").stream().map(d -> new Difficulty(d,
            performance(repository.performance(null, from, now, d, null)))).toList();
        var live = connections.snapshot();
        return new AdminOverview(now, period, from, repository.activity(null, from, now),
            performance(repository.performance(null, from, now, null, null)), repository.users("role"), repository.users("status"),
            difficulties, new Presence(live.connectedAccounts(), live.openConnections(), "OPEN_AUTHENTICATED_SOCKETS"));
    }
    @Override public CatalogPage<Question> questions(String period, String difficulty, int page, int size, UserPrincipal actor) {
        require(Role.ADMIN, actor); pagination(page, size); Instant now = Instant.now(), from = since(period, now);
        if (difficulty != null && !Set.of("EASY", "MEDIUM", "HARD").contains(difficulty))
            throw invalid("difficulty", "Độ khó không hợp lệ.");
        return repository.questions(from, now, difficulty, page, size);
    }
    private void teacherScope(Long unitId, UserPrincipal actor) {
        require(Role.TEACHER, actor);
        if (unitId != null) {
            if (unitId <= 0) throw invalid("unitId", "Unit không hợp lệ.");
            if (!repository.ownsUnit(actor.id(), unitId))
                throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy Unit.");
        }
    }
    @Override public CatalogPage<TeacherUnit> teacherUnits(int page, int size, UserPrincipal actor) {
        teacherScope(null, actor); pagination(page, size); return repository.teacherUnits(actor.id(), page, size);
    }
    @Override public TeacherOverview teacher(String period, Long unitId, UserPrincipal actor) {
        teacherScope(unitId, actor); Instant now = Instant.now(), from = since(period, now);
        var difficulties = List.of("EASY", "MEDIUM", "HARD").stream().map(d -> new Difficulty(d,
            performance(repository.teacherPerformance(actor.id(), unitId, null, from, now, d, null)))).toList();
        return new TeacherOverview(now, period, from, unitId, repository.teacherUnitCount(actor.id(), unitId),
            repository.teacherLearnersStarted(actor.id(), unitId, null),
            repository.teacherActivity(actor.id(), unitId, null, from, now),
            performance(repository.teacherPerformance(actor.id(), unitId, null, from, now, null, null)),
            repository.teacherHints(actor.id(), unitId, null, from, now), difficulties);
    }
    @Override public CatalogPage<TeacherLesson> teacherLessons(String period, Long unitId, int page, int size, UserPrincipal actor) {
        teacherScope(unitId, actor); pagination(page, size); Instant now = Instant.now(), from = since(period, now);
        var lessons = repository.teacherLessons(actor.id(), unitId, page, size);
        var items = lessons.items().stream().map(l -> {
            var activity = repository.teacherActivity(actor.id(), unitId, l.lessonId(), from, now);
            return new TeacherLesson(l.lessonId(), l.unitId(), l.unitTitle(), l.title(),
                repository.teacherLearnersStarted(actor.id(), unitId, l.lessonId()), activity.activeAttempts(),
                activity.submittedAttempts(), activity.completedLessons(),
                repository.teacherHints(actor.id(), unitId, l.lessonId(), from, now),
                performance(repository.teacherPerformance(actor.id(), unitId, l.lessonId(), from, now, null, null)));
        }).toList();
        return new CatalogPage<>(items, lessons.totalElements(), page, size, lessons.totalPages());
    }
    @Override public CatalogPage<Question> teacherQuestions(String period, Long unitId, String difficulty, int page, int size, UserPrincipal actor) {
        teacherScope(unitId, actor); pagination(page, size); Instant now = Instant.now(), from = since(period, now);
        if (difficulty != null && !Set.of("EASY", "MEDIUM", "HARD").contains(difficulty))
            throw invalid("difficulty", "Độ khó không hợp lệ.");
        return repository.teacherQuestions(actor.id(), unitId, from, now, difficulty, page, size);
    }
    @Override public CatalogPage<Breakdown> teacherBreakdown(String period, Long unitId, String groupBy, int page, int size, UserPrincipal actor) {
        teacherScope(unitId, actor); pagination(page, size);
        if (!Set.of("TOPIC", "OBJECTIVE").contains(groupBy)) throw invalid("groupBy", "Chọn TOPIC hoặc OBJECTIVE.");
        Instant now = Instant.now(), from = since(period, now);
        var groups = repository.teacherDimensions(actor.id(), unitId, from, now, groupBy, page, size);
        return new CatalogPage<>(groups.items().stream().map(g -> new Breakdown(g.id(), g.name(), g.topicId(),
            performance(repository.teacherPerformance(actor.id(), unitId, null, from, now, groupBy, g.id())))).toList(),
            groups.totalElements(), page, size, groups.totalPages());
    }
}
