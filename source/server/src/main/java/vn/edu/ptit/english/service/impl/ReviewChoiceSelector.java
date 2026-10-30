package vn.edu.ptit.english.service.impl;

import java.util.*;
import org.springframework.stereotype.Component;
import vn.edu.ptit.english.dto.ReviewViews.Choices;
import vn.edu.ptit.english.repository.ReviewRepository;
import vn.edu.ptit.english.service.impl.ReviewQuestionPool.Candidate;

/** Question-based choices; legacy DUE/MISTAKES keep their objective/schedule policy. */
@Component
public class ReviewChoiceSelector {
    private final ReviewQuestionPool questions;
    private final ReviewRepository reviews;
    public ReviewChoiceSelector(ReviewQuestionPool questions, ReviewRepository reviews) {
        this.questions = questions; this.reviews = reviews;
    }
    public static boolean supports(String kind) { return Set.of("ALL", "PRIORITY", "WRONG").contains(kind); }
    private record Bank(ReviewQuestionPool.Pool pool, Set<String> wrongOrigins, Set<Long> wrongObjectives) {
        boolean wrong(Candidate q) { return wrongOrigins.contains(q.originKey()); }
        boolean matches(Candidate q, String kind) {
            return wrong(q) || (!kind.equals("WRONG") && q.role().equals("REVIEW_VARIANT")
                && (kind.equals("ALL") || wrongObjectives.contains(q.objectiveId())));
        }
        List<Candidate> candidates(String kind) {
            var unique = new LinkedHashMap<String, Candidate>();
            pool.candidates().stream().filter(q -> matches(q, kind))
                .sorted(Comparator.comparingLong(Candidate::versionId).reversed())
                .forEach(q -> unique.putIfAbsent(q.originKey(), q));
            return new ArrayList<>(unique.values());
        }
    }
    private Bank read(long userId, Long topicId) {
        var wrong = reviews.wrongLearnQuestions(userId);
        var corrected = new HashMap<String, Long>();
        if (!wrong.isEmpty()) reviews.correctReviewQuestions(userId, wrong.stream().mapToLong(ReviewRepository.WrongLearnQuestion::submissionId).min().orElseThrow())
            .forEach(q -> corrected.merge(q.originKey(), q.submissionId(), Math::max));
        var origins = new HashSet<String>(); var objectives = new HashSet<Long>();
        wrong.stream().filter(q -> q.submissionId() > corrected.getOrDefault(q.originKey(), 0L))
            .forEach(q -> { origins.add(q.originKey()); objectives.add(q.objectiveId()); });
        return new Bank(questions.read(userId, topicId, true), origins, objectives);
    }
    public Choices counts(long userId, Long topicId) {
        var bank = read(userId, topicId);
        return new Choices(bank.candidates("ALL").size(), bank.candidates("PRIORITY").size(), bank.candidates("WRONG").size());
    }
    public List<Long> select(long userId, String kind, Long topicId, int count) {
        var bank = read(userId, topicId); var candidates = bank.candidates(kind);
        // Rotate through the eligible bank rather than repeat the same first ten forever.
        candidates.sort(Comparator.comparingInt(bank.pool()::seenCount).thenComparing(bank.pool()::lastSeen)
            .thenComparingLong(Candidate::versionId));
        if (kind.equals("PRIORITY")) {
            var wrong = new ArrayDeque<Candidate>(); var variants = new ArrayDeque<Candidate>();
            candidates.forEach(q -> (bank.wrong(q) ? wrong : variants).add(q));
            candidates.clear();
            while (!wrong.isEmpty() || !variants.isEmpty()) {
                if (!wrong.isEmpty()) candidates.add(wrong.remove());
                if (!variants.isEmpty()) candidates.add(variants.remove());
            }
        }
        // Keep a shared passage/audio together, preserving its authored question order.
        var groups = new LinkedHashMap<String, List<Candidate>>();
        candidates.stream().limit(count).forEach(q -> groups.computeIfAbsent(
            q.materialVersionId() == null ? "q:" + q.versionId() : "m:" + q.materialVersionId(), k -> new ArrayList<>()).add(q));
        return groups.values().stream().flatMap(group -> group.stream().sorted(Comparator.comparingInt(Candidate::position)))
            .map(Candidate::versionId).toList();
    }
}
