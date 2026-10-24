package vn.edu.ptit.english.service.impl;

import org.springframework.http.HttpStatus;
import vn.edu.ptit.english.exception.ApiException;

public class GamificationSourceFailure extends ApiException {
    private final long userId,submissionId;
    public GamificationSourceFailure(long userId,long submissionId,String code) {
        super(HttpStatus.SERVICE_UNAVAILABLE,code,"Phần thưởng đang chờ xử lý. Hãy thử cập nhật lại sau.");
        this.userId=userId; this.submissionId=submissionId;
    }
    public long userId() { return userId; }
    public long submissionId() { return submissionId; }
}
