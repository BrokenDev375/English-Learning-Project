package vn.edu.ptit.english.service.impl;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import vn.edu.ptit.english.entity.QuestionExposureEntity;
import vn.edu.ptit.english.repository.AttemptRepository;
import vn.edu.ptit.english.service.PublishedContentReader;

@Component
public class ReviewQuestionPool {
    public record Candidate(long versionId, String originKey, long objectiveId, long topicId, String difficulty,
                            String role, Long materialVersionId, int position) {}
    public record Pool(List<Candidate> candidates, Map<String, QuestionExposureEntity> exposures) {
        public boolean unseen(Candidate q) { return !exposures.containsKey(q.originKey()); }
        public Instant lastSeen(Candidate q) {
            var e = exposures.get(q.originKey()); return e == null ? Instant.MIN : e.getLastSeenAt();
        }
        public int seenCount(Candidate q) {
            var e = exposures.get(q.originKey()); return e == null ? 0 : e.getSeenCount();
        }
    }
    private final PublishedContentReader content;
    private final AttemptRepository attempts;
    public ReviewQuestionPool(PublishedContentReader content, AttemptRepository attempts) {
        this.content = content; this.attempts = attempts;
    }
    public Pool read(long userId, Long topicId) {
        var byVersion = new LinkedHashMap<Long, Candidate>();
        for (var lesson : content.activeLessons(topicId)) {
            for (var slot : lesson.questions()) {
                if (slot.practiceRole().equals("GUIDED")) continue;
                var q = content.questionVersion(slot.questionVersionId()); var data = q.data();
                var candidate = new Candidate(q.versionId(), q.originKey(), data.objectiveId(), data.topicId(),
                    data.difficulty(), slot.practiceRole(), data.materialVersionId(), slot.position());
                byVersion.merge(q.versionId(), candidate, (old, newer) -> old.role().equals("REVIEW_VARIANT") ? old : newer);
            }
        }
        var exposures = new HashMap<String, QuestionExposureEntity>();
        var origins = byVersion.values().stream().map(Candidate::originKey).distinct().toList();
        // Keep IN query parameters bounded when the bank grows.
        for (int from = 0; from < origins.size(); from += 500)
            attempts.exposures(userId, origins.subList(from, Math.min(from + 500, origins.size())))
                .forEach(e -> exposures.put(e.getId().getOriginKey(), e));
        return new Pool(List.copyOf(byVersion.values()), exposures);
    }
}
