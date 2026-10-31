package vn.edu.ptit.english.service;

import vn.edu.ptit.english.dto.ContestPaperData.Command;
import vn.edu.ptit.english.entity.ContestPaperEntity;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.RequestReceiptService.Result;

public interface ContestPaperService {
    Object capabilities(UserPrincipal actor);
    Object permissions(String query,boolean proctorOnly,int page,int size,UserPrincipal actor);
    Result grant(long user,Command input,UserPrincipal actor);
    boolean canProctor(long user);
    Object list(String state,String query,int page,int size,UserPrincipal actor);
    Object detail(long id,UserPrincipal actor);
    Result create(Command input,UserPrincipal actor);
    Result change(long id,String action,Command input,UserPrincipal actor);
    ContestPaperEntity ready(long id,boolean lock);
}
