package vn.edu.ptit.english.service.impl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.ptit.english.dto.AttemptAudioData.*;
import vn.edu.ptit.english.entity.AttemptAudioListenEntity;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.*;

@Service
public class AttemptAudioServiceImpl implements AttemptAudioService {
    private final AttemptRepository attempts;
    private final AttemptAudioRepository audio;
    private final PublishedContentReader content;
    private final AccountGuard guard;
    private final RequestReceiptService receipts;
    public AttemptAudioServiceImpl(AttemptRepository attempts,AttemptAudioRepository audio,PublishedContentReader content,AccountGuard guard,RequestReceiptService receipts){
        this.attempts=attempts;this.audio=audio;this.content=content;this.guard=guard;this.receipts=receipts;
    }
    private void student(UserPrincipal actor){if(actor.role()!=Role.STUDENT)throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Chỉ học viên được nghe trong lượt làm.");}
    private void ownedAudio(long attempt,long material,UserPrincipal actor,boolean lock){
        attempts.attempt(attempt,actor.id(),lock).orElseThrow(AuthoringAccess::missing);
        boolean belongs=attempts.items(attempt).stream().anyMatch(i->java.util.Objects.equals(content.questionVersion(i.getQuestionVersionId()).data().materialVersionId(),material));
        if(!belongs || !content.materialVersion(material).data().kind().equals("AUDIO"))throw AuthoringAccess.missing();
    }
    private Allowance view(long attempt,long material,int used){return new Allowance(attempt,material,used,2,2-used);}
    @Transactional(readOnly=true)
    public Allowance read(long attempt,long material,UserPrincipal actor){
        student(actor);guard.requireCurrent(actor);ownedAudio(attempt,material,actor,false);
        return view(attempt,material,audio.find(attempt,material).map(AttemptAudioListenEntity::getPlaysUsed).orElse(0));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public RequestReceiptService.Result start(long attempt,long material,Start input,UserPrincipal actor){
        student(actor);
        return receipts.execute(actor,input.requestId(),"POST /api/attempts/"+attempt+"/audio/"+material+"/start",input,200,()->{
            // Receipt holds the actor lock; Attempt lock serializes all tabs/devices and first-row creation.
            ownedAudio(attempt,material,actor,true);
            var row=audio.find(attempt,material).orElseGet(()->new AttemptAudioListenEntity(attempt,material));
            if(row.getPlaysUsed()>=2)throw new ApiException(HttpStatus.CONFLICT,"AUDIO_LIMIT_REACHED","Bạn đã dùng đủ 2 lượt nghe cho đoạn audio này.");
            row.start();audio.save(row);return view(attempt,material,row.getPlaysUsed());
        });
    }
}
