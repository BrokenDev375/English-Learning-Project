package vn.edu.ptit.english.service.impl;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.ptit.english.dto.AttemptRequests.Mutation;
import vn.edu.ptit.english.dto.AttemptViews.*;
import vn.edu.ptit.english.entity.AttemptAnswerEntity;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.enums.AttemptEnums.State;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.AttemptRepository;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.*;
import static vn.edu.ptit.english.dto.AuthoringRequests.id;

@Service
public class AttemptDraftServiceImpl implements AttemptDraftService {
    private final AttemptRepository attempts;
    private final RequestReceiptService receipts;
    private final AccountGuard accounts;
    private final AttemptDraftValidator validator;
    private final AttemptSnapshotReader snapshots;
    public AttemptDraftServiceImpl(AttemptRepository attempts,RequestReceiptService receipts,AccountGuard accounts,
        AttemptDraftValidator validator,AttemptSnapshotReader snapshots) {
        this.attempts=attempts; this.receipts=receipts; this.accounts=accounts; this.validator=validator; this.snapshots=snapshots;
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public RequestReceiptService.Result save(long attemptId,Mutation input,UserPrincipal actor) {
        student(actor); id(attemptId);
        return receipts.execute(actor,input.requestId(),"PUT /api/attempts/"+attemptId+"/draft",input,200,()-> {
            var attempt=attempts.attempt(attemptId,actor.id(),true).orElseThrow(AuthoringAccess::missing);
            if(attempt.getState()!=State.IN_PROGRESS) throw conflict("ATTEMPT_FINISHED","Lượt làm đã nộp, không thể lưu nháp.");
            if(attempt.getRevision()!=input.baseRevision()) throw conflict("REVISION_CONFLICT","Bài làm đã thay đổi ở nơi khác. Hãy tải lại và đối chiếu.");
            var items=attempts.items(attemptId);
            var responses=validator.validate(items,input);
            var rows=new HashMap<Long,AttemptAnswerEntity>();
            attempts.answers(items.stream().map(i->i.getId()).toList()).forEach(row->rows.put(row.getAttemptQuestionId(),row));
            responses.forEach((itemId,response)-> {
                var row=rows.get(itemId);
                if(row==null) row=new AttemptAnswerEntity(itemId,response); else row.update(response);
                attempts.save(row);
            });
            attempt.savePlayerState(input.playerState()); attempt.changed(); attempts.save(attempt);
            int answered=(int)responses.values().stream().filter(AttemptDraftValidator::answered).count();
            return new DraftAck(input.requestId(),attemptId,attempt.getRevision(),attempt.getState(),attempt.getUpdatedAt(),answered);
        });
    }
    @Override @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Snapshot read(long attemptId,UserPrincipal actor) {
        student(actor); accounts.requireCurrent(actor); id(attemptId);
        return snapshots.read(attempts.attempt(attemptId,actor.id(),false).orElseThrow(AuthoringAccess::missing));
    }
    private void student(UserPrincipal actor) {
        if(actor.role()!=Role.STUDENT) throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Chỉ học viên được sử dụng lượt làm.");
    }
    private ApiException conflict(String code,String message){return new ApiException(HttpStatus.CONFLICT,code,message);}
}
