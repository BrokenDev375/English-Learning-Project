package vn.edu.ptit.english.service.impl;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.edu.ptit.english.dto.AttemptViews.RoundProgress;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.AttemptEnums.ProgressState;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.*;

/** Read the whole published journey before filtering, so search and pagination cannot unlock a round. */
@Component
public class RoundJourneyReader {
    private final ContentRepository content;
    private final AttemptRepository attempts;
    private final WritingRepository writing;
    public RoundJourneyReader(ContentRepository content,AttemptRepository attempts,WritingRepository writing){this.content=content;this.attempts=attempts;this.writing=writing;}
    public List<RoundProgress> read(long userId) {
        var units=content.allPublicUnits();
        var ids=units.stream().map(UnitEntity::getId).toList();
        var lessons=attempts.publishedRoundLessons(ids);
        var progress=new HashMap<Long,LessonProgressEntity>();
        attempts.roundProgress(userId,ids).forEach(p->progress.put(p.getId().getLessonId(),p));
        var tasks=new HashMap<Long,WritingTaskEntity>();writing.tasks(ids).forEach(t->tasks.put(t.getUnitId(),t));
        var works=new HashMap<Long,WritingWorkEntity>();writing.works(userId,ids).forEach(w->works.put(w.getUnitId(),w));
        var byUnit=new HashMap<Long,List<LessonEntity>>();
        lessons.forEach(l->byUnit.computeIfAbsent(l.getUnitId(),id->new ArrayList<>()).add(l));
        var result=new ArrayList<RoundProgress>();
        RoundProgress blocker=null;
        for(var unit:units) {
            var rows=byUnit.getOrDefault(unit.getId(),List.of());
            if(rows.isEmpty())continue;
            int completed=0,started=0;Long next=null;
            for(var lesson:rows){var p=progress.get(lesson.getId());if(p!=null){started++;if(p.getState()==ProgressState.COMPLETED)completed++;}if(next==null&&(p==null||p.getState()!=ProgressState.COMPLETED))next=lesson.getId();}
            var task=tasks.get(unit.getId());var work=works.get(unit.getId());
            boolean writingRequired=task!=null&&task.getPublishedVersionId()!=null,submitted=work!=null&&work.getLatestSubmissionId()!=null;
            var writingProgress=!writingRequired&&work==null?null:new vn.edu.ptit.english.dto.WritingData.Progress(writingRequired,submitted,work==null?"NOT_STARTED":work.getState());
            boolean done=completed==rows.size()&&(!writingRequired||submitted),locked=!done&&blocker!=null;
            String state=done?"COMPLETED":started>0||work!=null?"IN_PROGRESS":"NOT_STARTED";
            var round=new RoundProgress(unit.view(),rows.size(),completed,started,state,next,unit.getRoundNumber(),locked,
                locked?blocker.unit().id():null,locked?blocker.number():null,locked?blocker.unit().title():null,writingProgress);
            result.add(round);
            if(!done&&blocker==null)blocker=round;
        }
        return List.copyOf(result);
    }
    public RoundProgress find(long userId,long unitId) {
        return read(userId).stream().filter(r->r.unit().id()==unitId).findFirst().orElseThrow(AuthoringAccess::missing);
    }
    public void requireOpen(long userId,long unitId) {
        var round=find(userId,unitId);
        if(round.locked())throw new ApiException(HttpStatus.CONFLICT,"ROUND_LOCKED",
            "Hoàn thành Vòng "+round.prerequisiteRoundNumber()+" — "+round.prerequisiteTitle()+" để mở Vòng "+round.number()+".");
    }
}
