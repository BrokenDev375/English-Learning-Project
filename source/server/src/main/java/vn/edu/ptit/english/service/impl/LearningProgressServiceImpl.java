package vn.edu.ptit.english.service.impl;

import java.util.*;
import vn.edu.ptit.english.dto.AttemptViews.RoundProgress;
import vn.edu.ptit.english.repository.ContentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.ptit.english.dto.AttemptViews.LessonProgress;
import vn.edu.ptit.english.dto.CatalogViews.CatalogPage;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.enums.AttemptEnums.ProgressState;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.AttemptRepository;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.LearningProgressService;
import static vn.edu.ptit.english.dto.AuthoringRequests.id;

@Service
public class LearningProgressServiceImpl implements LearningProgressService {
    private final AttemptRepository attempts;
    private final AccountGuard accounts;
    private final RoundJourneyReader journey;
    private final ContentRepository content;
    public LearningProgressServiceImpl(AttemptRepository attempts,AccountGuard accounts,RoundJourneyReader journey,ContentRepository content){this.content=content;this.attempts=attempts;this.accounts=accounts;this.journey=journey;}
    @Override @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public CatalogPage<RoundProgress> rounds(String q,Long topicId,int page,int size,UserPrincipal actor) {
        if(actor.role()!=Role.STUDENT)throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Chỉ học viên được xem vòng học cá nhân.");
        accounts.requireCurrent(actor);if(topicId!=null)id(topicId);
        if(q.length()>100||page<0||size<1||size>50)throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Bộ lọc hoặc phân trang không hợp lệ.");
        var all=new HashMap<Long,RoundProgress>();journey.read(actor.id()).forEach(r->all.put(r.unit().id(),r));
        return CatalogPage.from(content.publicUnits(q,topicId,page,size).map(u->all.get(u.getId())));
    }
    @Override @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public RoundProgress round(long unitId,UserPrincipal actor) {
        if(actor.role()!=Role.STUDENT)throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Chỉ học viên được xem vòng học cá nhân.");
        accounts.requireCurrent(actor);id(unitId);return journey.find(actor.id(),unitId);
    }

    @Override @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public CatalogPage<LessonProgress> list(Long unitId,int page,int size,UserPrincipal actor) {
        if(actor.role()!=Role.STUDENT)throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Chỉ học viên được xem tiến độ cá nhân.");
        accounts.requireCurrent(actor); if(unitId!=null)id(unitId);
        if(page<0||size<1||size>50)throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Phân trang không hợp lệ.");
        return CatalogPage.from(attempts.progressLessons(actor.id(),unitId,page,size).map(lesson->{
            Long current=lesson.getRetiredAt()==null&&lesson.getPublished()!=null?lesson.getPublished().getId():null;
            var row=attempts.progress(new LessonProgressKey(actor.id(),lesson.getId()),false).orElse(null);
            if(row==null)return new LessonProgress(lesson.getId(),ProgressState.NOT_STARTED,null,null,null,current,false,null);
            Long previous=row.getLastCompletedVersionId();
            if(previous==null)previous=attempts.attempt(row.getLastAttemptId(),actor.id(),false).map(AttemptEntity::getLessonVersionId).orElse(null);
            return new LessonProgress(lesson.getId(),row.getState(),row.getLastAttemptId(),row.getCompletedAt(),row.getLastCompletedVersionId(),
                current,current!=null&&previous!=null&&!Objects.equals(current,previous),row.getLastScorePercent());
        }));
    }
}
