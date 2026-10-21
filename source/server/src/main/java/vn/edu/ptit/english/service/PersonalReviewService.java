package vn.edu.ptit.english.service;

import vn.edu.ptit.english.dto.ReviewViews.Dashboard;
import vn.edu.ptit.english.security.UserPrincipal;

public interface PersonalReviewService {
    RequestReceiptService.Result synchronize(String requestId, UserPrincipal actor);
    Dashboard dashboard(Long topicId, String kind, int page, int size, UserPrincipal actor);
}
