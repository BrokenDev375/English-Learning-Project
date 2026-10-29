package vn.edu.ptit.english.service.impl;

import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.edu.ptit.english.dto.AttemptRequests.Create;
import vn.edu.ptit.english.entity.QuestionExposureEntity;
import vn.edu.ptit.english.enums.AttemptEnums.*;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.service.*;
import vn.edu.ptit.english.service.PublishedContentReader.*;

@Component
public class AttemptSelector {
    public record Selected(PublishedQuestion question,PracticeRole role,Long slot) {}
    public record Selection(Long lessonVersionId,List<Selected> questions) {}
    private final RoundJourneyReader journey;
    private final PublishedContentReader content;
    private final ContentRepository roots;
    private final AttemptRepository attempts;
    private final ObjectProvider<ReviewSelectionProvider> review;
    public AttemptSelector(PublishedContentReader content,ContentRepository roots,AttemptRepository attempts,ObjectProvider<ReviewSelectionProvider> review,RoundJourneyReader journey) {
        this.journey=journey;
        this.content=content; this.roots=roots; this.attempts=attempts; this.review=review;
    }
    public Selection select(Create input,long userId) {
        if(input.mode()==Mode.LEARN) {
            var root=roots.lesson(input.lessonId(),true).orElseThrow(AuthoringAccess::missing); roots.refresh(root);
            var lesson=content.activeLesson(input.lessonId()); journey.requireOpen(userId,lesson.unitId()); var selected=new ArrayList<Selected>();
            lesson.questions().stream().filter(s->!s.practiceRole().equals("REVIEW_VARIANT"))
                .sorted(Comparator.comparingInt((vn.edu.ptit.english.dto.AuthoringData.Slot s)->s.practiceRole().equals("GUIDED")?0:1).thenComparingInt(s->s.position()))
                .forEach(s->selected.add(new Selected(content.questionVersion(s.questionVersionId()),PracticeRole.valueOf(s.practiceRole()),
                    attempts.lessonSlot(lesson.versionId(),s.questionVersionId()).orElseThrow(AuthoringAccess::missing))));
            if(!lesson.activities().isEmpty()) {
                var order=vn.edu.ptit.english.dto.LessonActivities.order(lesson.activities());
                selected.sort(Comparator.comparingInt(s->order.indexOf(s.question().versionId())));
            }
            return checked(lesson.versionId(),selected);
        }
        ReviewSelectionProvider provider=input.mode()==Mode.REVIEW?review.getIfAvailable():null;
        if(input.mode()==Mode.REVIEW&&provider==null) throw conflict("REVIEW_NOT_READY","Chức năng ôn tập chưa sẵn sàng.");
        var candidates=new LinkedHashMap<Long,Selected>();
        for(var lesson:activeLessonsLocked(input.topicId())) for(var slot:lesson.questions()) {
            if(slot.practiceRole().equals("GUIDED")) continue;
            if(input.mode()==Mode.PRACTICE&&!slot.practiceRole().equals("INDEPENDENT"))continue;
            var q=content.questionVersion(slot.questionVersionId());
            if(input.mode()==Mode.PRACTICE&&!q.data().difficulty().equals(input.difficulty()))continue;
            candidates.putIfAbsent(q.versionId(),new Selected(q,PracticeRole.valueOf(slot.practiceRole()),
                attempts.lessonSlot(lesson.versionId(),q.versionId()).orElseThrow(AuthoringAccess::missing)));
        }
        var selected=new ArrayList<Selected>(); var origins=new HashSet<String>();
        if(input.mode()==Mode.REVIEW) {
            List<Long> ids=provider.select(userId,input.reviewKind(),input.topicId(),input.count());
            if(ids==null)throw conflict("REVIEW_NOT_READY","Chức năng ôn tập chưa sẵn sàng.");
            if(ids.size()>input.count()||new HashSet<>(ids).size()!=ids.size())throw conflict("NO_QUESTIONS_AVAILABLE","Bộ câu ôn không hợp lệ.");
            for(Long id:ids) {
                var candidate=candidates.get(id);
                if(candidate==null||!origins.add(candidate.question().originKey()))throw conflict("NO_QUESTIONS_AVAILABLE","Bộ câu ôn không hợp lệ.");
                selected.add(candidate);
            }
        } else {
            var exposures=new HashMap<String,QuestionExposureEntity>();
            attempts.exposures(userId,candidates.values().stream().map(c->c.question().originKey()).toList()).forEach(e->exposures.put(e.getId().getOriginKey(),e));
            var shuffled=new ArrayList<>(candidates.values()); Collections.shuffle(shuffled);
            shuffled.sort(Comparator.comparingInt((Selected s)->exposures.containsKey(s.question().originKey())?exposures.get(s.question().originKey()).getSeenCount():0)
                .thenComparing(s->exposures.containsKey(s.question().originKey())?exposures.get(s.question().originKey()).getLastSeenAt():Instant.MIN));
            for(var candidate:shuffled) if(origins.add(candidate.question().originKey())) {
                selected.add(candidate); if(selected.size()==input.count())break;
            }
        }
        return checked(null,selected);
    }
    private List<PublishedLesson> activeLessonsLocked(Long topicId) {
        var lessons=new ArrayList<PublishedLesson>();
        // Lock in ID order, matching publication's lesson order. Re-read after a concurrent retire/publish.
        for(long id:content.activeLessons(topicId).stream().mapToLong(PublishedLesson::lessonId).distinct().sorted().toArray()) {
            var root=roots.lesson(id,true).orElseThrow(AuthoringAccess::missing); roots.refresh(root);
            if(root.getRetiredAt()==null&&root.getPublished()!=null)lessons.add(content.activeLesson(id));
        }
        return lessons;
    }
    private Selection checked(Long lesson,List<Selected> selected) {
        if(selected.isEmpty())throw conflict("NO_QUESTIONS_AVAILABLE","Không có câu hỏi phù hợp.");
        var origins=new HashSet<String>();
        for(var s:selected)if(!origins.add(s.question().originKey()))throw conflict("NO_QUESTIONS_AVAILABLE","Bộ câu hỏi bị trùng nguồn gốc.");
        return new Selection(lesson,List.copyOf(selected));
    }
    private ApiException conflict(String code,String message){return new ApiException(HttpStatus.CONFLICT,code,message);}
}
