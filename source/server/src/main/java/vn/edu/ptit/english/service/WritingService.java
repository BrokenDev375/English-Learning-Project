package vn.edu.ptit.english.service;
import vn.edu.ptit.english.dto.WritingData.*;
import vn.edu.ptit.english.dto.CatalogViews.CatalogPage;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.RequestReceiptService.Result;
public interface WritingService {
    Task task(long unit,UserPrincipal actor);
    Result configure(long unit,String operation,Command input,UserPrincipal actor);
    Learner learner(long unit,UserPrincipal actor);
    Result mutate(long unit,String operation,Command input,UserPrincipal actor);
    CatalogPage<QueueItem> queue(long unit,String state,int page,int size,UserPrincipal actor);
    Grading grading(long submission,UserPrincipal actor);
    Result grade(long submission,Command input,UserPrincipal actor);
}
