package vn.edu.ptit.english.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.ptit.english.dto.AttemptData.*;
import vn.edu.ptit.english.dto.AttemptRequests.Create;
import vn.edu.ptit.english.dto.AttemptViews.Summary;
import vn.edu.ptit.english.dto.CatalogViews.CatalogPage;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.enums.AttemptEnums.*;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.AttemptRepository;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.*;
import static vn.edu.ptit.english.dto.MaterialRequests.invalid;

@Service
public class AttemptServiceImpl implements AttemptService {
    private final AttemptRepository attempts;
    private final AttemptSelector selector;
    private final AttemptSnapshotReader snapshots;
    private final RequestReceiptService receipts;
    private final AccountGuard accounts;
    private final ObjectMapper json;
    public AttemptServiceImpl(AttemptRepository attempts,AttemptSelector selector,AttemptSnapshotReader snapshots,
        RequestReceiptService receipts,AccountGuard accounts,ObjectMapper json) {
        this.attempts=attempts; this.selector=selector; this.snapshots=snapshots; this.receipts=receipts; this.accounts=accounts; this.json=json;
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public RequestReceiptService.Result create(Create input,UserPrincipal actor) {
        student(actor);
        return receipts.execute(actor,input.requestId(),"POST /api/attempts",input.payload(),()-> {
            var selection=json.valueToTree(input.selection());
            for(var existing:attempts.active(actor.id(),input.mode())) if(sameSelection(existing,input))
                return new RequestReceiptService.Reply(200,snapshots.read(existing));
            var chosen=selector.select(input,actor.id());
            var attempt=attempts.save(new AttemptEntity(actor.id(),input.mode(),chosen.lessonVersionId(),selection,PlayerState.empty()));
            Long first=null; int position=0; Instant now=Instant.now();
            for(var selected:chosen.questions()) {
                var q=selected.question();
                var key=new QuestionExposureKey(actor.id(),q.originKey());
                var previous=attempts.exposure(key,true); int seen=previous.map(QuestionExposureEntity::getSeenCount).orElse(0);
                var options=new ArrayList<String>(); var right=new ArrayList<String>();
                q.data().content().path("options").forEach(o->options.add(o.path("id").asText()));
                q.data().content().path("rightItems").forEach(o->right.add(o.path("id").asText()));
                Collections.shuffle(options); Collections.shuffle(right);
                var item=attempts.save(new AttemptQuestionEntity(attempt.getId(),q.versionId(),selected.slot(),q.originKey(),selected.role(),position++,new Display(options,right),seen));
                if(first==null)first=item.getId();
                attempts.save(new AttemptAnswerEntity(item.getId(),null));
                var exposure=previous.orElseGet(()->new QuestionExposureEntity(actor.id(),q.originKey(),attempt.getId(),now));
                if(previous.isPresent())exposure.seenIn(attempt.getId(),now);
                attempts.save(exposure);
            }
            attempt.savePlayerState(new PlayerState(first,List.of())); attempts.save(attempt);
            if(input.mode()==Mode.LEARN) {
                var key=new LessonProgressKey(actor.id(),input.lessonId());
                var progress=attempts.progress(key,true).orElseGet(()->new LessonProgressEntity(actor.id(),input.lessonId(),attempt.getId()));
                progress.started(attempt.getId()); attempts.save(progress);
            }
            return new RequestReceiptService.Reply(201,snapshots.read(attempt));
        });
    }
    @Override @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public CatalogPage<Summary> list(String mode,String state,int page,int size,UserPrincipal actor) {
        student(actor); accounts.requireCurrent(actor);
        if(page<0||size<1||size>50)throw invalid("page","Phân trang không hợp lệ.");
        Mode filterMode=parse(Mode.class,mode,"mode"); State filterState=parse(State.class,state,"state");
        return CatalogPage.from(attempts.attempts(actor.id(),filterMode,filterState,page,size).map(a->{
            var items=attempts.items(a.getId());
            int answered=(int)attempts.answers(items.stream().map(i->i.getId()).toList()).stream().filter(v->AttemptDraftValidator.answered(v.getResponse())).count();
            return new Summary(a.getId(),a.getMode(),a.getState(),a.getLessonVersionId(),a.getRevision(),items.size(),answered,
                a.getCreatedAt(),a.getUpdatedAt(),a.getSubmittedAt(),attempts.submission(a.getId()).map(SubmissionEntity::getId).orElse(null));
        }));
    }
    private <T extends Enum<T>> T parse(Class<T> type,String value,String path) {
        if(value==null)return null;
        try{return Enum.valueOf(type,value);}catch(IllegalArgumentException e){throw invalid(path,"Bộ lọc không hợp lệ.");}
    }
    private boolean sameSelection(AttemptEntity attempt,Create input) {
        var s=attempt.getSelection();
        return switch(input.mode()) {
            case LEARN -> s.path("lessonId").asLong()==input.lessonId();
            case PRACTICE -> s.path("topicId").asLong()==input.topicId()&&s.path("difficulty").asText().equals(input.difficulty())&&s.path("count").asInt()==input.count();
            case REVIEW -> s.path("reviewKind").asText().equals(input.reviewKind())&&s.path("count").asInt()==input.count()
                &&(input.topicId()==null?s.path("topicId").isNull():s.path("topicId").asLong()==input.topicId());
        };
    }
    private void student(UserPrincipal actor) {
        if(actor.role()!=Role.STUDENT)throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Chỉ học viên được sử dụng lượt làm.");
    }
}
