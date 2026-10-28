package vn.edu.ptit.english.repository;

import java.time.Instant;
import java.util.List;
import vn.edu.ptit.english.dto.StatisticsViews.*;
import vn.edu.ptit.english.dto.CatalogViews.CatalogPage;

public interface StatisticsRepository {
    record Row(String phase, String segment, String outcome, long count) {}
    record Dimension(long id, String name, long topicId) {}
    Activity activity(Long userId, Instant since, Instant until);
    List<Row> performance(Long userId, Instant since, Instant until, String dimension, Long id);
    CatalogPage<Dimension> dimensions(long userId, Instant since, Instant until, String groupBy, int page, int size);
    CatalogPage<Lesson> lessons(long userId, int page, int size);
    Review review(long userId, Instant now);
    List<Count> users(String field);
    List<OnlineAccount> onlineAccounts(java.util.Set<Long> ids);
    CatalogPage<Question> questions(Instant since, Instant until, String difficulty, int page, int size);
    boolean ownsUnit(long teacherId, long unitId);
    CatalogPage<TeacherUnit> teacherUnits(long teacherId, int page, int size);
    long teacherUnitCount(long teacherId, Long unitId);
    long teacherLearnersStarted(long teacherId, Long unitId, Long lessonId);
    Activity teacherActivity(long teacherId, Long unitId, Long lessonId, Instant since, Instant until);
    long teacherHints(long teacherId, Long unitId, Long lessonId, Instant since, Instant until);
    List<Row> teacherPerformance(long teacherId, Long unitId, Long lessonId, Instant since, Instant until, String dimension, Long dimensionId);
    CatalogPage<Dimension> teacherDimensions(long teacherId, Long unitId, Instant since, Instant until, String groupBy, int page, int size);
    record TeacherLessonInfo(long lessonId, long unitId, String unitTitle, String title) {}
    CatalogPage<TeacherLessonInfo> teacherLessons(long teacherId, Long unitId, int page, int size);
    CatalogPage<Question> teacherQuestions(long teacherId, Long unitId, Instant since, Instant until, String difficulty, int page, int size);
}
