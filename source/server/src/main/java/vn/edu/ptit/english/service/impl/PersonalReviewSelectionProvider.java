package vn.edu.ptit.english.service.impl;

import java.util.List;
import org.springframework.stereotype.Component;
import vn.edu.ptit.english.service.ReviewSelectionProvider;

@Component
public class PersonalReviewSelectionProvider implements ReviewSelectionProvider {
    private final PersonalReviewServiceImpl service;
    public PersonalReviewSelectionProvider(PersonalReviewServiceImpl service) { this.service = service; }
    @Override public List<Long> select(long userId, String reviewKind, Long topicId, int count) {
        return service.select(userId, reviewKind, topicId, count);
    }
}
