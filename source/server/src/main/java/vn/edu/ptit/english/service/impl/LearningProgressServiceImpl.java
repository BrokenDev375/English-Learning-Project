package vn.edu.ptit.english.service.impl;

import java.util.Objects;
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
    public LearningProgressServiceImpl(AttemptRepository attempts,AccountGuard accounts){this.attempts=attempts;this.accounts=accounts;}
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
