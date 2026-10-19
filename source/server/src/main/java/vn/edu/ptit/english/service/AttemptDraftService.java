package vn.edu.ptit.english.service;

import vn.edu.ptit.english.dto.AttemptRequests.Mutation;
import vn.edu.ptit.english.dto.AttemptViews.Snapshot;
import vn.edu.ptit.english.security.UserPrincipal;

public interface AttemptDraftService {
    RequestReceiptService.Result save(long attemptId,Mutation input,UserPrincipal actor);
    Snapshot read(long attemptId,UserPrincipal actor);
}
