package vn.edu.ptit.english.service;
import vn.edu.ptit.english.dto.AttemptAudioData.*;
import vn.edu.ptit.english.security.UserPrincipal;
public interface AttemptAudioService {
    Allowance read(long attemptId,long materialVersionId,UserPrincipal actor);
    RequestReceiptService.Result start(long attemptId,long materialVersionId,Start input,UserPrincipal actor);
}
