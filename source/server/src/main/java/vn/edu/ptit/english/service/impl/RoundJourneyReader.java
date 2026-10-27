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
    public RoundJourneyReader(ContentRepository content,AttemptRepository attempts){this.content=content;this.attempts=attempts;}
    public List<RoundProgress> read(long userId) {
        var units=content.allPublicUnits();
        var ids=units.stream().map(UnitEntity::getId).toList();
        var lessons=attempts.publishedRoundLessons(ids);
        var progress=new HashMap<Long,LessonProgressEntity>();
        attempts.roundProgress(userId,ids).forEach(p->progress.put(p.getId().getLessonId(),p));
        var byUnit=new HashMap<Long,List<LessonEntity>>();
        lessons.forEach(l->byUnit.computeIfAbsent(l.getUnitId(),id->new ArrayList<>()).add(l));
        var result=new ArrayList<RoundProgress>();
        RoundProgress blocker=null;int number=0;
        for(var unit:units) {
            var rows=byUnit.getOrDefault(unit.getId(),List.of());
            if(rows.isEmpty())continue;
            int completed=0,started=0;Long next=null;
            for(var lesson:rows){var p=progress.get(lesson.getId());if(p!=null){started++;if(p.getState()==ProgressState.COMPLETED)completed++;}if(next==null&&(p==null||p.getState()!=ProgressState.COMPLETED))next=lesson.getId();}
            boolean done=completed==rows.size(),locked=!done&&blocker!=null;
            String state=done?"COMPLETED":started>0?"IN_PROGRESS":"NOT_STARTED";
            var round=new RoundProgress(unit.view(),rows.size(),completed,started,state,next,++number,locked,
                locked?blocker.unit().id():null,locked?blocker.number():null,locked?blocker.unit().title():null);
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
