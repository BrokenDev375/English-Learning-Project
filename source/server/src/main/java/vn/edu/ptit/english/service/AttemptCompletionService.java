package vn.edu.ptit.english.service;

import vn.edu.ptit.english.dto.AttemptRequests.*;
import vn.edu.ptit.english.dto.AttemptViews.Result;
import vn.edu.ptit.english.security.UserPrincipal;

public interface AttemptCompletionService {
    RequestReceiptService.Result hint(long id,long item,Hint input,UserPrincipal actor);
    RequestReceiptService.Result check(long id,long item,Check input,UserPrincipal actor);
    RequestReceiptService.Result retry(long id,long item,Check input,UserPrincipal actor);
    RequestReceiptService.Result submit(long id,Mutation input,UserPrincipal actor);
    RequestReceiptService.Result complete(long id,Hint input,UserPrincipal actor);
    Result result(long id,UserPrincipal actor);
}
