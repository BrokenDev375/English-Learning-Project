package vn.edu.ptit.english.repository.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.english.dto.StatisticsViews.*;
import vn.edu.ptit.english.dto.CatalogViews.CatalogPage;
import vn.edu.ptit.english.repository.StatisticsRepository;
import static vn.edu.ptit.english.enums.AttemptEnums.*;

@Repository
public class StatisticsRepositoryImpl implements StatisticsRepository {
    private final EntityManager em;
    public StatisticsRepositoryImpl(EntityManager em) { this.em = em; }
    private static final String SUBMISSIONS = " from AttemptEntity a, SubmissionEntity s where s.attemptId=a.id"
        + " and a.submittedAt>=:since and a.submittedAt<=:until";
    private static final String TRIALS = " from AnswerTrialEntity t, AttemptQuestionEntity i, AttemptEntity a,"
        + " SubmissionEntity s, QuestionVersionEntity q where t.attemptQuestionId=i.id and i.attemptId=a.id"
        + " and s.attemptId=a.id and i.questionVersionId=q.id and a.submittedAt>=:since and a.submittedAt<=:until"
        + " and t.createdAt<=:until";
    private static final String SEGMENT = "case when i.practiceRole=:guided then 'GUIDED' when t.supportUsed=true"
        + " then 'ASSISTED' when i.wasSeenBefore=true then 'INDEPENDENT_SEEN' else 'INDEPENDENT_NEW' end";
    private String owner(Long userId) { return userId == null ? "" : " and a.userId=:userId"; }
    private <T> TypedQuery<T> scope(TypedQuery<T> query, Long userId, Instant since, Instant until) {
        query.setParameter("since", since).setParameter("until", until);
        if (userId != null) query.setParameter("userId", userId);
        return query;
    }
    private long number(Object value) { return value == null ? 0 : ((Number) value).longValue(); }
    private <T> CatalogPage<T> page(List<T> rows, long count, int page, int size) {
        return new CatalogPage<>(rows, count, page, size, (int) ((count + size - 1) / size));
    }
    @Override public Activity activity(Long userId, Instant since, Instant until) {
        var rows = scope(em.createQuery("select a.mode,count(s.id)" + SUBMISSIONS + owner(userId)
            + " group by a.mode order by a.mode", Object[].class), userId, since, until).getResultList();
        var counts = new EnumMap<Mode, Long>(Mode.class);
        rows.forEach(r -> counts.put((Mode) r[0], number(r[1])));
        var modes = Arrays.stream(Mode.values()).map(m -> new ModeCount(m.name(), counts.getOrDefault(m, 0L))).toList();
        Object[] totals = scope(em.createQuery("select count(distinct a.userId),max(a.submittedAt)" + SUBMISSIONS
            + owner(userId), Object[].class), userId, since, until).getSingleResult();
        var activeQuery = em.createQuery("select count(a.id) from AttemptEntity a where a.state=:state" + owner(userId), Long.class)
            .setParameter("state", State.IN_PROGRESS);
        if (userId != null) activeQuery.setParameter("userId", userId);
        var completedQuery = em.createQuery("select count(p.id) from LessonProgressEntity p where p.state=:state"
            + (userId == null ? "" : " and p.id.userId=:userId"), Long.class).setParameter("state", ProgressState.COMPLETED);
        if (userId != null) completedQuery.setParameter("userId", userId);
        return new Activity(modes.stream().mapToLong(ModeCount::submissions).sum(), activeQuery.getSingleResult(),
            completedQuery.getSingleResult(), number(totals[0]), (Instant) totals[1], modes);
    }
    @Override public List<Row> performance(Long userId, Instant since, Instant until, String dimension, Long id) {
        String filter = "";
        if (dimension != null) filter = switch (dimension) {
            case "TOPIC" -> " and q.topicId=:dimensionId";
            case "OBJECTIVE" -> " and q.objectiveId=:dimensionId";
            case "EASY", "MEDIUM", "HARD" -> " and q.difficulty=:difficulty";
            default -> throw new IllegalArgumentException("Invalid statistics dimension");
        };
        var query = scope(em.createQuery("select t.kind," + SEGMENT + ",t.outcome,count(t.id)" + TRIALS + owner(userId)
            + filter + " group by t.kind," + SEGMENT + ",t.outcome", Object[].class), userId, since, until)
            .setParameter("guided", PracticeRole.GUIDED);
        if (id != null) query.setParameter("dimensionId", id);
        else if (dimension != null) query.setParameter("difficulty", dimension);
        return query.getResultList().stream().map(r -> new Row(r[0].toString(), (String) r[1], r[2].toString(), number(r[3]))).toList();
    }
    @Override public CatalogPage<Dimension> dimensions(long userId, Instant since, Instant until, String groupBy, int page, int size) {
        String key = groupBy.equals("TOPIC") ? "q.topicId" : "q.objectiveId";
        String source = TRIALS + owner(userId) + " and t.kind=:first";
        long count = scope(em.createQuery("select count(distinct " + key + ")" + source, Long.class), userId, since, until)
            .setParameter("first", TrialKind.FIRST).getSingleResult();
        var ids = scope(em.createQuery("select distinct " + key + source + " order by " + key, Long.class), userId, since, until)
            .setParameter("first", TrialKind.FIRST).setFirstResult(page * size).setMaxResults(size).getResultList();
        if (ids.isEmpty()) return page(List.of(), count, page, size);
        String metadata = groupBy.equals("TOPIC") ? "select x.id,x.name,x.id from TopicEntity x"
            : "select x.id,x.name,x.topicId from ObjectiveEntity x";
        var labels = new HashMap<Long, Dimension>();
        em.createQuery(metadata + " where x.id in :ids", Object[].class).setParameter("ids", ids).getResultList()
            .forEach(r -> labels.put(number(r[0]), new Dimension(number(r[0]), (String) r[1], number(r[2]))));
        return page(ids.stream().map(labels::get).toList(), count, page, size);
    }
    @Override public CatalogPage<Lesson> lessons(long userId, int page, int size) {
        String source = " from LessonProgressEntity p, LessonVersionEntity v where p.id.userId=:userId"
            + " and v.id=p.lastCompletedVersionId and p.state=:completed";
        long count = em.createQuery("select count(p.id)" + source, Long.class).setParameter("userId", userId)
            .setParameter("completed", ProgressState.COMPLETED).getSingleResult();
        var rows = em.createQuery("select p.id.lessonId,v.id,v.title,p.state,p.lastAttemptId,p.completedAt,p.lastScorePercent"
            + source + " order by p.completedAt desc,p.id.lessonId desc", Object[].class).setParameter("userId", userId)
            .setParameter("completed", ProgressState.COMPLETED).setFirstResult(page * size).setMaxResults(size).getResultList();
        return page(rows.stream().map(r -> new Lesson(number(r[0]), number(r[1]), (String) r[2], r[3].toString(),
            number(r[4]), (Instant) r[5], r[6] == null ? null : ((Number) r[6]).doubleValue())).toList(), count, page, size);
    }
    @Override public Review review(long userId, Instant now) {
        Object[] row = em.createQuery("select count(p.id),sum(case when p.needsPractice=true or p.nextReviewAt<=:now then 1 else 0 end),"
            + "sum(case when p.hasMistakes=true then 1 else 0 end) from ReviewProgressEntity p where p.id.userId=:userId", Object[].class)
            .setParameter("userId", userId).setParameter("now", now).getSingleResult();
        Instant next = em.createQuery("select min(p.nextReviewAt) from ReviewProgressEntity p where p.id.userId=:userId"
            + " and p.needsPractice=false and p.nextReviewAt>:now", Instant.class)
            .setParameter("userId", userId).setParameter("now", now).getSingleResult();
        long pending = em.createQuery("select count(s.id) from SubmissionEntity s, AttemptEntity a where s.attemptId=a.id"
            + " and a.userId=:userId and not exists (select p.submissionId from ReviewProcessedSubmissionEntity p where p.submissionId=s.id)", Long.class)
            .setParameter("userId", userId).getSingleResult();
        return new Review(number(row[0]), number(row[1]), number(row[2]), pending, next);
    }
    @Override public List<Count> users(String field) {
        if (!Set.of("role", "status").contains(field)) throw new IllegalArgumentException("Invalid user aggregate");
        return em.createQuery("select u." + field + ",count(u.id) from UserEntity u group by u." + field, Object[].class)
            .getResultList().stream().map(r -> new Count(r[0].toString(), number(r[1]))).toList();
    }
    @Override public CatalogPage<Question> questions(Instant since, Instant until, String difficulty, int page, int size) {
        String source = TRIALS + " and t.kind=:first" + (difficulty == null ? "" : " and q.difficulty=:difficulty");
        var countQuery = scope(em.createQuery("select count(distinct q.id)" + source + " and t.outcome=:wrong", Long.class), null, since, until)
            .setParameter("first", TrialKind.FIRST).setParameter("wrong", Outcome.INCORRECT);
        if (difficulty != null) countQuery.setParameter("difficulty", difficulty);
        long count = countQuery.getSingleResult();
        var query = scope(em.createQuery("select q.questionId,q.id,q.versionNo,q.prompt,q.type,q.difficulty,count(distinct a.userId),"
            + "sum(case when t.outcome=:correct then 1 else 0 end),sum(case when t.outcome=:wrong then 1 else 0 end),"
            + "sum(case when t.outcome=:blank then 1 else 0 end)" + source
            + " group by q.questionId,q.id,q.versionNo,q.prompt,q.type,q.difficulty"
            + " having sum(case when t.outcome=:wrong then 1 else 0 end)>0"
            + " order by sum(case when t.outcome=:wrong then 1 else 0 end) desc,q.id asc", Object[].class), null, since, until)
            .setParameter("first", TrialKind.FIRST).setParameter("correct", Outcome.CORRECT)
            .setParameter("wrong", Outcome.INCORRECT).setParameter("blank", Outcome.UNANSWERED);
        if (difficulty != null) query.setParameter("difficulty", difficulty);
        var rows = query.setFirstResult(page * size).setMaxResults(size).getResultList();
        return page(rows.stream().map(r -> new Question(number(r[0]), number(r[1]), ((Number) r[2]).intValue(), (String) r[3],
            (String) r[4], (String) r[5], number(r[6]), Metric.of(number(r[7]), number(r[8]), number(r[9])))).toList(), count, page, size);
    }
    // Attribute a LEARN observation through its pinned LessonVersion, never by
    // the question's owner or its current membership in a published lesson.
    private String teacherFilter(Long unitId, Long lessonId) {
        return " and a.mode=:learn and exists (select l.id from LessonVersionEntity v, LessonEntity l, UnitEntity u"
            + " where v.id=a.lessonVersionId and v.lessonId=l.id and l.unitId=u.id and u.ownerId=:teacherId"
            + (unitId == null ? "" : " and u.id=:unitId") + (lessonId == null ? "" : " and l.id=:lessonId") + ")";
    }
    private <T> TypedQuery<T> teacherScope(TypedQuery<T> query, long teacherId, Long unitId, Long lessonId) {
        query.setParameter("teacherId", teacherId).setParameter("learn", Mode.LEARN);
        if (unitId != null) query.setParameter("unitId", unitId);
        if (lessonId != null) query.setParameter("lessonId", lessonId);
        return query;
    }
    @Override public boolean ownsUnit(long teacherId, long unitId) {
        return em.createQuery("select count(u.id) from UnitEntity u where u.ownerId=:teacherId and u.id=:unitId", Long.class)
            .setParameter("teacherId", teacherId).setParameter("unitId", unitId).getSingleResult() > 0;
    }
    @Override public long teacherUnitCount(long teacherId, Long unitId) {
        var q = em.createQuery("select count(u.id) from UnitEntity u where u.ownerId=:teacherId"
            + (unitId == null ? "" : " and u.id=:unitId"), Long.class).setParameter("teacherId", teacherId);
        if (unitId != null) q.setParameter("unitId", unitId);
        return q.getSingleResult();
    }
    @Override public CatalogPage<TeacherUnit> teacherUnits(long teacherId, int page, int size) {
        var rows = em.createQuery("select u.id,u.title,u.stage,u.sortOrder from UnitEntity u where u.ownerId=:teacherId"
            + " order by u.stage,u.sortOrder,u.id", Object[].class).setParameter("teacherId", teacherId)
            .setFirstResult(page * size).setMaxResults(size).getResultList();
        return page(rows.stream().map(r -> new TeacherUnit(number(r[0]), (String) r[1],
            ((Number) r[2]).intValue(), ((Number) r[3]).intValue())).toList(), teacherUnitCount(teacherId, null), page, size);
    }
    @Override public long teacherLearnersStarted(long teacherId, Long unitId, Long lessonId) {
        return teacherScope(em.createQuery("select count(distinct a.userId) from AttemptEntity a where 1=1"
            + teacherFilter(unitId, lessonId), Long.class), teacherId, unitId, lessonId).getSingleResult();
    }
    @Override public Activity teacherActivity(long teacherId, Long unitId, Long lessonId, Instant since, Instant until) {
        var totals = teacherScope(scope(em.createQuery("select count(s.id),count(distinct a.userId),max(a.submittedAt)"
            + SUBMISSIONS + teacherFilter(unitId, lessonId), Object[].class), null, since, until),
            teacherId, unitId, lessonId).getSingleResult();
        long active = teacherScope(em.createQuery("select count(a.id) from AttemptEntity a where a.state=:state"
            + teacherFilter(unitId, lessonId), Long.class).setParameter("state", State.IN_PROGRESS),
            teacherId, unitId, lessonId).getSingleResult();
        var completed = em.createQuery("select count(p.id) from LessonProgressEntity p, LessonEntity l, UnitEntity u"
            + " where p.id.lessonId=l.id and l.unitId=u.id and u.ownerId=:teacherId and p.state=:completed"
            + (unitId == null ? "" : " and u.id=:unitId") + (lessonId == null ? "" : " and l.id=:lessonId"), Long.class)
            .setParameter("teacherId", teacherId).setParameter("completed", ProgressState.COMPLETED);
        if (unitId != null) completed.setParameter("unitId", unitId);
        if (lessonId != null) completed.setParameter("lessonId", lessonId);
        return new Activity(number(totals[0]), active, completed.getSingleResult(), number(totals[1]), (Instant) totals[2],
            List.of(new ModeCount("LEARN", number(totals[0]))));
    }
    @Override public long teacherHints(long teacherId, Long unitId, Long lessonId, Instant since, Instant until) {
        return teacherScope(scope(em.createQuery("select count(i.id) from AttemptQuestionEntity i, AttemptEntity a, SubmissionEntity s"
            + " where i.attemptId=a.id and s.attemptId=a.id and a.submittedAt>=:since and a.submittedAt<=:until"
            + " and i.hintOpenedAt is not null" + teacherFilter(unitId, lessonId), Long.class), null, since, until),
            teacherId, unitId, lessonId).getSingleResult();
    }
    @Override public List<Row> teacherPerformance(long teacherId, Long unitId, Long lessonId, Instant since, Instant until, String dimension, Long dimensionId) {
        String filter = dimension == null ? "" : switch (dimension) {
            case "TOPIC" -> " and q.topicId=:dimensionId";
            case "OBJECTIVE" -> " and q.objectiveId=:dimensionId";
            case "EASY", "MEDIUM", "HARD" -> " and q.difficulty=:difficulty";
            default -> throw new IllegalArgumentException("Invalid statistics dimension");
        };
        var query = teacherScope(scope(em.createQuery("select t.kind," + SEGMENT + ",t.outcome,count(t.id)"
            + TRIALS + teacherFilter(unitId, lessonId) + filter
            + " group by t.kind," + SEGMENT + ",t.outcome", Object[].class), null, since, until), teacherId, unitId, lessonId)
            .setParameter("guided", PracticeRole.GUIDED);
        if (dimensionId != null) query.setParameter("dimensionId", dimensionId);
        else if (dimension != null) query.setParameter("difficulty", dimension);
        return query.getResultList().stream().map(r -> new Row(r[0].toString(), (String) r[1], r[2].toString(), number(r[3]))).toList();
    }
    @Override public CatalogPage<Dimension> teacherDimensions(long teacherId, Long unitId, Instant since, Instant until, String groupBy, int page, int size) {
        String key = groupBy.equals("TOPIC") ? "q.topicId" : "q.objectiveId";
        String source = TRIALS + teacherFilter(unitId, null) + " and t.kind=:first";
        long count = teacherScope(scope(em.createQuery("select count(distinct " + key + ")" + source, Long.class), null, since, until),
            teacherId, unitId, null).setParameter("first", TrialKind.FIRST).getSingleResult();
        var ids = teacherScope(scope(em.createQuery("select distinct " + key + source + " order by " + key, Long.class), null, since, until),
            teacherId, unitId, null).setParameter("first", TrialKind.FIRST).setFirstResult(page * size).setMaxResults(size).getResultList();
        if (ids.isEmpty()) return page(List.of(), count, page, size);
        String metadata = groupBy.equals("TOPIC") ? "select x.id,x.name,x.id from TopicEntity x" : "select x.id,x.name,x.topicId from ObjectiveEntity x";
        var labels = new HashMap<Long, Dimension>();
        em.createQuery(metadata + " where x.id in :ids", Object[].class).setParameter("ids", ids).getResultList()
            .forEach(row -> labels.put(number(row[0]), new Dimension(number(row[0]), (String) row[1], number(row[2]))));
        return page(ids.stream().map(labels::get).toList(), count, page, size);
    }
    @Override public CatalogPage<TeacherLessonInfo> teacherLessons(long teacherId, Long unitId, int page, int size) {
        String source = " from LessonEntity l, UnitEntity u, LessonVersionEntity v where l.unitId=u.id"
            + " and v.lessonId=l.id and v.versionNo=(select max(v2.versionNo) from LessonVersionEntity v2 where v2.lessonId=l.id)"
            + " and u.ownerId=:teacherId" + (unitId == null ? "" : " and u.id=:unitId");
        var count = em.createQuery("select count(l.id)" + source, Long.class).setParameter("teacherId", teacherId);
        var rows = em.createQuery("select l.id,u.id,u.title,v.title" + source + " order by u.stage,u.sortOrder,u.id,l.sortOrder,l.id", Object[].class)
            .setParameter("teacherId", teacherId).setFirstResult(page * size).setMaxResults(size);
        if (unitId != null) { count.setParameter("unitId", unitId); rows.setParameter("unitId", unitId); }
        return page(rows.getResultList().stream().map(r -> new TeacherLessonInfo(number(r[0]), number(r[1]),
            (String) r[2], (String) r[3])).toList(), count.getSingleResult(), page, size);
    }
    @Override public CatalogPage<Question> teacherQuestions(long teacherId, Long unitId, Instant since, Instant until, String difficulty, int page, int size) {
        String source = TRIALS + teacherFilter(unitId, null) + " and t.kind=:first" + (difficulty == null ? "" : " and q.difficulty=:difficulty");
        var count = teacherScope(scope(em.createQuery("select count(distinct q.id)" + source + " and t.outcome=:wrong", Long.class),
            null, since, until), teacherId, unitId, null).setParameter("first", TrialKind.FIRST).setParameter("wrong", Outcome.INCORRECT);
        var rows = teacherScope(scope(em.createQuery("select q.questionId,q.id,q.versionNo,q.prompt,q.type,q.difficulty,count(distinct a.userId),"
            + "sum(case when t.outcome=:correct then 1 else 0 end),sum(case when t.outcome=:wrong then 1 else 0 end),"
            + "sum(case when t.outcome=:blank then 1 else 0 end)" + source
            + " group by q.questionId,q.id,q.versionNo,q.prompt,q.type,q.difficulty"
            + " having sum(case when t.outcome=:wrong then 1 else 0 end)>0"
            + " order by sum(case when t.outcome=:wrong then 1 else 0 end) desc,q.id asc", Object[].class), null, since, until),
            teacherId, unitId, null).setParameter("first", TrialKind.FIRST).setParameter("correct", Outcome.CORRECT)
            .setParameter("wrong", Outcome.INCORRECT).setParameter("blank", Outcome.UNANSWERED);
        if (difficulty != null) { count.setParameter("difficulty", difficulty); rows.setParameter("difficulty", difficulty); }
        var result = rows.setFirstResult(page * size).setMaxResults(size).getResultList();
        return page(result.stream().map(r -> new Question(number(r[0]), number(r[1]), ((Number) r[2]).intValue(),
            (String) r[3], (String) r[4], (String) r[5], number(r[6]), Metric.of(number(r[7]), number(r[8]), number(r[9])))).toList(),
            count.getSingleResult(), page, size);
    }
}
