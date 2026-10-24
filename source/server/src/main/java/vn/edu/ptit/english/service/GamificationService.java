package vn.edu.ptit.english.service;

import java.util.List;
import vn.edu.ptit.english.dto.GamificationViews.*;
import vn.edu.ptit.english.security.UserPrincipal;

public interface GamificationService {
    Summary summary(UserPrincipal actor);
    Badges badges(UserPrincipal actor);
    ActivitiesPage activities(Long submissionId,int page,int size,UserPrincipal actor);
    RequestReceiptService.Result synchronize(String requestId,UserPrincipal actor);
    void processUser(long userId);
    List<Long> pendingUsers(long afterUserId);
}
