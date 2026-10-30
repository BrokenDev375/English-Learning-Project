package vn.edu.ptit.english.service.impl;

import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.ptit.english.dto.AttemptViews.ResultItem;
import vn.edu.ptit.english.dto.CatalogViews.CatalogPage;
import vn.edu.ptit.english.dto.ReviewViews.*;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.enums.AttemptEnums.*;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.ReviewRepository;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.*;
import static vn.edu.ptit.english.dto.MaterialRequests.invalid;

@Service
public class PersonalReviewServiceImpl implements PersonalReviewService {
    private final ReviewRepository repository;
    private final PublishedContentReader content;
    private final ReviewQuestionPool questions;
    private final ReviewPolicy policy;
    private final RequestReceiptService receipts;
    private final AccountGuard accounts;
    private final ReviewChoiceSelector choices;
    public PersonalReviewServiceImpl(ReviewRepository repository, PublishedContentReader content,
        ReviewQuestionPool questions, ReviewPolicy policy, RequestReceiptService receipts, AccountGuard accounts,
        ReviewChoiceSelector choices) {
        this.repository = repository; this.content = content; this.questions = questions;
        this.policy = policy; this.receipts = receipts; this.accounts = accounts;
        this.choices = choices;
    }
    private void student(UserPrincipal actor) {
        if (actor.role() != Role.STUDENT)
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ học viên được sử dụng ôn tập cá nhân.");
    }
    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public RequestReceiptService.Result synchronize(String requestId, UserPrincipal actor) {
        student(actor);
        if (requestId == null || !requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw invalid("requestId", "Cần UUID chữ thường.");
        return receipts.execute(actor, requestId, "POST /api/me/review/sync", Map.of("requestId", requestId), 200, () -> {
            var pending = repository.pending(actor.id(), 100);
            for (var submission : pending) {
                var result = submission.getResult();
                if (result.factsSchemaVersion() != 1)
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "REVIEW_SCHEMA_UNSUPPORTED", "Chưa hỗ trợ phiên bản kết quả này.");
                var groups = new LinkedHashMap<Long, List<ResultItem>>();
                for (var item : result.items()) groups.computeIfAbsent(item.objectiveId(), k -> new ArrayList<>()).add(item);
                for (var entry : groups.entrySet()) record(actor.id(), result.submissionId(), result.submittedAt(), entry.getKey(), entry.getValue());
                repository.processed(actor.id(), result.submissionId());
            }
            return new Sync(pending.size(), repository.pendingCount(actor.id()));
        });
    }
    private void record(long userId, long submissionId, Instant at, long objectiveId, List<ResultItem> items) {
        int answered = 0, correct = 0, assisted = 0, blank = 0, wrong = 0, independent = 0, independentCorrect = 0;
        String difficulty = null, lowestWrongDifficulty = null;
        var fresh = new LinkedHashMap<String, int[]>();
        for (var item : items) {
            boolean hasAnswer = AttemptDraftValidator.answered(item.firstResponse());
            if (!hasAnswer) { blank++; continue; }
            answered++;
            if (item.firstOutcome() == Outcome.CORRECT) correct++;
            if (item.firstOutcome() == Outcome.INCORRECT) wrong++;
            String level = content.questionVersion(item.questionVersionId()).data().difficulty();
            if (item.firstOutcome() == Outcome.INCORRECT && (lowestWrongDifficulty == null
                || ReviewPolicy.difficultyRank(level) < ReviewPolicy.difficultyRank(lowestWrongDifficulty))) lowestWrongDifficulty = level;
            boolean self = item.practiceRole() != PracticeRole.GUIDED && !item.firstSupportUsed();
            if (!self) { assisted++; continue; }
            independent++;
            if (item.firstOutcome() == Outcome.CORRECT) independentCorrect++;
            if (difficulty == null || ReviewPolicy.difficultyRank(level) > ReviewPolicy.difficultyRank(difficulty)) difficulty = level;
            int[] counts = fresh.computeIfAbsent(level, k -> new int[3]);
            if (item.wasSeenBefore() && item.firstOutcome() == Outcome.INCORRECT) counts[2]++;
            if (!item.wasSeenBefore()) {
                counts[0]++;
                if (item.firstOutcome() == Outcome.CORRECT) counts[1]++;
            }
        }
        var progress = repository.progress(new ReviewObjectiveKey(userId, objectiveId))
            .orElseGet(() -> new ReviewProgressEntity(userId, objectiveId));
        if (lowestWrongDifficulty != null) difficulty = lowestWrongDifficulty;
        progress.record(submissionId, at, answered, correct, assisted, blank, wrong, independent, independentCorrect,
            difficulty, policy.intervals(), policy.zone());
        repository.save(progress);
        fresh.forEach((level, counts) -> {
            var evidence = repository.evidence(new ReviewEvidenceKey(userId, objectiveId, level))
                .orElseGet(() -> new ReviewEvidenceEntity(userId, objectiveId, level));
            evidence.record(counts[0], counts[1], policy.minimumQuestions(), policy.goodPercent(), counts[2] > 0);
            repository.save(evidence);
        });
    }
    private static boolean due(ReviewProgressEntity p, Instant now) {
        return p.isNeedsPractice() || !p.getNextReviewAt().isAfter(now);
    }
    private static Comparator<ReviewProgressEntity> priority(Instant now) {
        return Comparator.comparingInt((ReviewProgressEntity p) -> p.isHasMistakes() ? 0 : p.isNeedsPractice() ? 1 : due(p, now) ? 2 : 3)
            .thenComparing(ReviewProgressEntity::getNextReviewAt)
            .thenComparing(ReviewProgressEntity::getLastPracticedAt)
            .thenComparing(p -> p.getId().getObjectiveId());
    }
    private static boolean matches(ReviewProgressEntity p, String kind, Instant now) {
        return switch (kind) { case "DUE" -> due(p, now); case "MISTAKES" -> p.isHasMistakes(); default -> true; };
    }
    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Dashboard dashboard(Long topicId, String kind, int page, int size, UserPrincipal actor) {
        student(actor); accounts.requireCurrent(actor);
        if (topicId != null) vn.edu.ptit.english.dto.AuthoringRequests.id(topicId);
        if (!Set.of("ALL", "DUE", "MISTAKES").contains(kind) || page < 0 || size < 1 || size > 50)
            throw invalid("query", "Bộ lọc hoặc phân trang không hợp lệ.");
        Instant now = Instant.now(); var states = repository.progress(actor.id());
        var objectives = repository.objectives(states.stream().map(p -> p.getId().getObjectiveId()).toList());
        var scoped = states.stream().filter(p -> objectives.containsKey(p.getId().getObjectiveId()))
            .filter(p -> topicId == null || objectives.get(p.getId().getObjectiveId()).view().topicId() == topicId).toList();
        var filtered = scoped.stream().filter(p -> matches(p, kind, now)).sorted(priority(now)).toList();
        var pool = questions.read(actor.id(), topicId);
        var evidence = new HashMap<ReviewEvidenceKey, ReviewEvidenceEntity>();
        repository.evidence(actor.id()).forEach(e -> evidence.put(e.getId(), e));
        long from = (long) page * size;
        var items = from >= filtered.size() ? List.<ObjectiveReview>of() : filtered.subList((int) from, (int) Math.min(from + size, filtered.size()))
            .stream().map(p -> view(p, objectives.get(p.getId().getObjectiveId()), pool, evidence, now)).toList();
        return new Dashboard(now, repository.pendingCount(actor.id()), scoped.size(),
            (int) scoped.stream().filter(p -> due(p, now)).count(), (int) scoped.stream().filter(ReviewProgressEntity::isHasMistakes).count(),
            new CatalogPage<>(items, filtered.size(), page, size, (filtered.size() + size - 1) / size), choices.counts(actor.id(), topicId));
    }
    private ObjectiveReview view(ReviewProgressEntity p, ObjectiveEntity objective, ReviewQuestionPool.Pool pool,
                                Map<ReviewEvidenceKey, ReviewEvidenceEntity> evidence, Instant now) {
        long objectiveId = p.getId().getObjectiveId();
        var current = pool.candidates().stream().filter(q -> q.objectiveId() == objectiveId && q.difficulty().equals(p.getCurrentDifficulty())).toList();
        int available = (int) current.stream().map(ReviewQuestionPool.Candidate::originKey).distinct().count();
        int unseen = (int) current.stream().filter(pool::unseen).map(ReviewQuestionPool.Candidate::originKey).distinct().count();
        var e = evidence.get(new ReviewEvidenceKey(p.getId().getUserId(), objectiveId, p.getCurrentDifficulty()));
        int sets = e == null ? 0 : e.getFreshSetCount(), streak = e == null ? 0 : e.getGoodSetStreak();
        String recommended = p.getCurrentDifficulty(), recommendation;
        String next = ReviewPolicy.nextDifficulty(p.getCurrentDifficulty());
        if (p.isNeedsPractice()) recommendation = "KEEP_PRACTICING";
        else if (streak < policy.goodSets()) recommendation = unseen < (policy.goodSets() - streak) * policy.minimumQuestions()
            ? "NOT_ENOUGH_NEW_QUESTIONS" : "NOT_ENOUGH_DATA";
        else if (next == null) recommendation = "HIGHEST_DEMO_LEVEL";
        else {
            long nextAvailable = pool.candidates().stream().filter(q -> q.objectiveId() == objectiveId && q.difficulty().equals(next))
                .filter(pool::unseen).map(ReviewQuestionPool.Candidate::originKey).distinct().count();
            if (nextAvailable < policy.minimumQuestions()) recommendation = "NOT_ENOUGH_NEW_QUESTIONS";
            else { recommended = next; recommendation = "TRY_NEXT_DIFFICULTY"; }
        }
        String status = p.getFirstAnsweredCount() == 0 ? "NO_EVIDENCE" : p.isHasMistakes() ? "NEEDS_PRACTICE" : p.isNeedsPractice() ? "NEEDS_INDEPENDENT_WORK"
            : due(p, now) ? "DUE" : "SCHEDULED";
        String reason = p.getFirstAnsweredCount() == 0 ? "Chưa có câu trả lời để đánh giá; hãy tự làm thêm câu cùng mục tiêu."
            : p.isHasMistakes() ? "Có câu trả lời sai ở lần đầu; ưu tiên luyện lại bằng câu cùng mục tiêu."
            : p.isNeedsPractice() ? "Chưa có lượt tự làm đúng đủ để giảm ưu tiên ôn."
            : due(p, now) ? "Đã tới mốc nhắc ôn lại kiến thức." : "Đã tự làm đúng; sẽ nhắc ôn vào mốc tiếp theo.";
        return new ObjectiveReview(objectiveId, objective.view().topicId(), objective.view().name(), status, reason, due(p, now),
            p.isHasMistakes(), p.getStage(), p.getLastPracticedAt(), p.getNextReviewAt(), p.getSubmissionCount(),
            p.getFirstAnsweredCount(), p.getFirstCorrectCount(), p.getAssistedCount(), p.getUnansweredCount(),
            p.getLastWrongCount(), p.getLastIndependentAnswered(), p.getLastIndependentCorrect(), available, unseen,
            p.getCurrentDifficulty(), recommended, recommendation, sets,
            e == null ? 0 : e.getFreshAnsweredCount(), e == null ? 0 : e.getFreshCorrectCount());
    }

    /** Called inside M03's actor-locked create transaction; M03 revalidates every returned ID. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> select(long userId, String reviewKind, Long topicId, int count) {
        if (repository.pendingCount(userId) > 0)
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_SYNC_REQUIRED", "Có kết quả mới. Hãy cập nhật nội dung ôn trước khi bắt đầu.");
        if (ReviewChoiceSelector.supports(reviewKind)) return choices.select(userId, reviewKind, topicId, count);
        Instant now = Instant.now();
        var pool = questions.read(userId, topicId);
        var scopedObjectives = new HashSet<Long>(); pool.candidates().forEach(q -> scopedObjectives.add(q.objectiveId()));
        var states = repository.progress(userId).stream().filter(p -> scopedObjectives.contains(p.getId().getObjectiveId()))
            .filter(p -> matches(p, reviewKind, now)).sorted(priority(now)).toList();
        var queues = new ArrayList<ArrayDeque<ReviewQuestionPool.Candidate>>();
        for (var state : states) {
            var candidates = pool.candidates().stream()
                .filter(q -> q.objectiveId() == state.getId().getObjectiveId() && q.difficulty().equals(state.getCurrentDifficulty()))
                .sorted(Comparator.comparingInt((ReviewQuestionPool.Candidate q) -> pool.unseen(q) ? q.role().equals("REVIEW_VARIANT") ? 0 : 1 : 2)
                    .thenComparingInt(pool::seenCount).thenComparing(pool::lastSeen)
                    .thenComparingLong(ReviewQuestionPool.Candidate::versionId)).toList();
            queues.add(new ArrayDeque<>(candidates));
        }
        var selected = new ArrayList<ReviewQuestionPool.Candidate>(); var origins = new HashSet<String>();
        // Round robin gives multiple weak objectives a turn in a short review.
        boolean added = true;
        while (selected.size() < count && added) {
            added = false;
            for (var queue : queues) {
                while (!queue.isEmpty() && origins.contains(queue.peek().originKey())) queue.remove();
                if (!queue.isEmpty() && selected.size() < count) {
                    var q = queue.remove(); origins.add(q.originKey()); selected.add(q); added = true;
                }
            }
        }
        // Keep questions sharing a passage/audio together and in authored order.
        var groups = new LinkedHashMap<String, List<ReviewQuestionPool.Candidate>>();
        for (var q : selected) groups.computeIfAbsent(q.materialVersionId() == null ? "q:" + q.versionId() : "m:" + q.materialVersionId(),
            k -> new ArrayList<>()).add(q);
        var ids = new ArrayList<Long>();
        groups.values().forEach(group -> group.stream().sorted(Comparator.comparingInt(ReviewQuestionPool.Candidate::position))
            .forEach(q -> ids.add(q.versionId())));
        return List.copyOf(ids);
    }
}
