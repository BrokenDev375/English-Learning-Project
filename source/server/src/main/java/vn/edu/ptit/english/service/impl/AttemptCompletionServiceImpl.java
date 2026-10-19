package vn.edu.ptit.english.service.impl;

import java.math.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.ptit.english.dto.AttemptData.Answer;
import vn.edu.ptit.english.dto.AttemptRequests.*;
import vn.edu.ptit.english.dto.AttemptViews.*;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.enums.AttemptEnums.*;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.AttemptRepository;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.*;
import static vn.edu.ptit.english.dto.AuthoringRequests.id;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class AttemptCompletionServiceImpl implements AttemptCompletionService {
    private final AttemptRepository attempts;
    private final RequestReceiptService receipts;
    private final AccountGuard accounts;
    private final PublishedContentReader content;
    private final AnswerGrader grader;
    private final AttemptDraftValidator validator;
    public AttemptCompletionServiceImpl(AttemptRepository attempts,RequestReceiptService receipts,AccountGuard accounts,
        PublishedContentReader content,AnswerGrader grader,AttemptDraftValidator validator) {
        this.attempts=attempts; this.receipts=receipts; this.accounts=accounts; this.content=content; this.grader=grader; this.validator=validator;
    }
    private void student(UserPrincipal actor,long attemptId) {
        if(actor.role()!=Role.STUDENT)throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Chỉ học viên được sử dụng lượt làm.");
        id(attemptId);
    }
    private ApiException conflict(String code){return new ApiException(HttpStatus.CONFLICT,code,"Thao tác không phù hợp với trạng thái lượt làm. Hãy tải lại để đối chiếu.");}
    private AttemptEntity locked(long attemptId,long revision,State state,UserPrincipal actor) {
        var attempt=attempts.attempt(attemptId,actor.id(),true).orElseThrow(AuthoringAccess::missing);
        if(attempt.getState()!=state)throw conflict(state==State.IN_PROGRESS?"ATTEMPT_FINISHED":"RESULT_NOT_READY");
        if(attempt.getRevision()!=revision)throw conflict("REVISION_CONFLICT");
        return attempt;
    }
    private AttemptQuestionEntity item(long itemId,long attemptId) {
        id(itemId); return attempts.item(itemId,attemptId).orElseThrow(AuthoringAccess::missing);
    }
    private void guided(AttemptQuestionEntity item) {
        if(item.getPracticeRole()!=PracticeRole.GUIDED)throw conflict("FEEDBACK_NOT_AVAILABLE");
    }
    private boolean supported(AttemptQuestionEntity item){return item.getHintOpenedAt()!=null||item.getFeedbackOpenedAt()!=null;}
    private void answer(long itemId,Answer response) {
        var row=attempts.answers(List.of(itemId)).stream().findFirst().orElse(null);
        if(row==null)row=new AttemptAnswerEntity(itemId,response); else row.update(response);
        attempts.save(row);
    }
    @Override public RequestReceiptService.Result hint(long attemptId,long itemId,Hint input,UserPrincipal actor) {
        student(actor,attemptId); id(itemId);
        return receipts.execute(actor,input.requestId(),"POST /api/attempts/"+attemptId+"/items/"+itemId+"/hint",input,200,()-> {
            var attempt=locked(attemptId,input.baseRevision(),State.IN_PROGRESS,actor);
            var item=item(itemId,attemptId); guided(item);
            String hint=content.questionVersion(item.getQuestionVersionId()).data().hint();
            if(hint==null||hint.isBlank())throw new ApiException(HttpStatus.NOT_FOUND,"HINT_NOT_AVAILABLE","Câu hỏi không có gợi ý.");
            if(item.openHint(Instant.now())){attempts.save(item); attempt.changed(); attempts.save(attempt);}
            return new HintResult(input.requestId(),attemptId,attempt.getRevision(),itemId,hint);
        });
    }
    @Override public RequestReceiptService.Result check(long attemptId,long itemId,Check input,UserPrincipal actor) {
        return feedback(attemptId,itemId,input,actor,false);
    }
    @Override public RequestReceiptService.Result retry(long attemptId,long itemId,Check input,UserPrincipal actor) {
        return feedback(attemptId,itemId,input,actor,true);
    }
    private RequestReceiptService.Result feedback(long attemptId,long itemId,Check input,UserPrincipal actor,boolean correction) {
        student(actor,attemptId); id(itemId);
        String operation="POST /api/attempts/"+attemptId+"/items/"+itemId+(correction?"/retry":"/check");
        return receipts.execute(actor,input.requestId(),operation,input,200,()-> {
            var attempt=locked(attemptId,input.baseRevision(),correction?State.SUBMITTED:State.IN_PROGRESS,actor);
            var item=item(itemId,attemptId); if(!correction)guided(item);
            var question=content.questionVersion(item.getQuestionVersionId()).data();
            var grade=grader.grade(question,input.response());
            if(!grade.responseComplete())throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ANSWER","Cần trả lời đầy đủ trước khi kiểm tra hoặc chữa bài.");
            var history=attempts.trials(itemId);
            int number=history.isEmpty()?1:history.get(history.size()-1).getTrialNo()+1;
            var kind=correction?TrialKind.CORRECTION:history.isEmpty()?TrialKind.FIRST:TrialKind.GUIDED_RETRY;
            // Capture FIRST support before opening feedback; corrections never overwrite final answers.
            var trial=attempts.save(new AnswerTrialEntity(itemId,number,kind,correction?TrialSource.RETRY:TrialSource.CHECK,
                input.response(),grade.outcome(),grade.responseComplete(),correction||supported(item)));
            if(!correction){answer(itemId,input.response()); item.openFeedback(Instant.now()); attempts.save(item);}
            attempt.changed(); attempts.save(attempt);
            return new Feedback(input.requestId(),attemptId,attempt.getRevision(),itemId,trial.getId(),number,kind,grade.outcome(),
                grade.responseComplete(),trial.isSupportUsed(),question.answerKey(),question.explanation());
        });
    }
    @Override public RequestReceiptService.Result submit(long attemptId,Mutation input,UserPrincipal actor) {
        student(actor,attemptId);
        return receipts.execute(actor,input.requestId(),"POST /api/attempts/"+attemptId+"/submit",input,200,()-> {
            var attempt=locked(attemptId,input.baseRevision(),State.IN_PROGRESS,actor);
            var items=attempts.items(attemptId); var responses=validator.validate(items,input);
            Instant now=Instant.now(); var facts=new ArrayList<ResultItem>(); var materials=new LinkedHashMap<Long,MaterialFeedback>();
            int answered=0,correct=0,freshAnswered=0,freshCorrect=0;
            boolean completed=attempt.getMode()==Mode.LEARN;
            for(var item:items) {
                var question=content.questionVersion(item.getQuestionVersionId()).data();
                Answer response=responses.get(item.getId()); answer(item.getId(),response);
                var grade=grader.grade(question,response); var history=attempts.trials(item.getId());
                boolean checked=history.stream().anyMatch(t->t.getSource()==TrialSource.CHECK);
                var first=history.stream().filter(t->t.getKind()==TrialKind.FIRST).findFirst().orElseGet(()->
                    attempts.save(new AnswerTrialEntity(item.getId(),1,TrialKind.FIRST,TrialSource.SUBMIT,response,
                        grade.outcome(),grade.responseComplete(),supported(item))));
                if(AttemptDraftValidator.answered(response))answered++;
                if(grade.outcome()==Outcome.CORRECT)correct++;
                if(item.getPracticeRole()!=PracticeRole.GUIDED&&!item.isWasSeenBefore()&&!first.isSupportUsed()
                    &&AttemptDraftValidator.answered(first.getResponse())) {
                    freshAnswered++; if(first.getOutcome()==Outcome.CORRECT)freshCorrect++;
                }
                completed&=grade.responseComplete()&&(item.getPracticeRole()!=PracticeRole.GUIDED||checked);
                facts.add(new ResultItem(item.getId(),item.getQuestionVersionId(),item.getOriginKey(),question.objectiveId(),
                    item.getPracticeRole(),item.isWasSeenBefore(),first.getResponse(),first.getOutcome(),first.isResponseComplete(),first.isSupportUsed(),
                    response,grade.outcome(),grade.responseComplete(),question.answerKey(),question.explanation(),question.materialVersionId()));
                Long material=question.materialVersionId();
                if(material!=null)materials.computeIfAbsent(material,key->new MaterialFeedback(key,content.materialVersion(key).data().transcript()));
            }
            var score=BigDecimal.valueOf(correct).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(items.size()),2,RoundingMode.HALF_UP);
            var result=new Result(1,0,attemptId,attempt.getMode(),attempt.getLessonVersionId(),now,attempt.getRevision()+1,
                items.size(),answered,items.size()-answered,correct,score,freshAnswered,freshCorrect,completed,facts,new ArrayList<>(materials.values()));
            // The repository binds the generated ID into result_json inside this same transaction.
            var submission=attempts.save(new SubmissionEntity(result));
            attempt.savePlayerState(input.playerState()); attempt.submit(now); attempt.changed(); attempts.save(attempt);
            if(attempt.getMode()==Mode.LEARN) {
                long lesson=content.lessonVersion(attempt.getLessonVersionId()).lessonId();
                var progress=attempts.progress(new LessonProgressKey(actor.id(),lesson),true)
                    .orElseGet(()->new LessonProgressEntity(actor.id(),lesson,attemptId));
                progress.recordSubmission(attemptId,attempt.getLessonVersionId(),score,completed,now); attempts.save(progress);
            }
            return submission.getResult();
        });
    }
    @Override @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Result result(long attemptId,UserPrincipal actor) {
        student(actor,attemptId); accounts.requireCurrent(actor);
        var attempt=attempts.attempt(attemptId,actor.id(),false).orElseThrow(AuthoringAccess::missing);
        if(attempt.getState()!=State.SUBMITTED)throw conflict("RESULT_NOT_READY");
        return attempts.submission(attemptId).orElseThrow(AuthoringAccess::missing).getResult();
    }
}
