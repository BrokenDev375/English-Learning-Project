package vn.edu.ptit.english.service;
import vn.edu.ptit.english.dto.AudioUpdateData;
import vn.edu.ptit.english.security.UserPrincipal;
public interface AudioUpdateService {
    AudioUpdateData.Preview preview(long materialId,UserPrincipal actor);
    RequestReceiptService.Result apply(long materialId,AudioUpdateData.Request input,UserPrincipal actor);
}
