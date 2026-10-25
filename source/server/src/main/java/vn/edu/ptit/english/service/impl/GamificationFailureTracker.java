package vn.edu.ptit.english.service.impl;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.ptit.english.entity.GamificationFailureEntity;
import vn.edu.ptit.english.repository.*;

@Service
public class GamificationFailureTracker {
    private final GamificationRepository repository;
    private final UserJpaRepository users;
    private final Clock clock;
    public GamificationFailureTracker(GamificationRepository repository,UserJpaRepository users,Clock clock) { this.repository=repository; this.users=users; this.clock=clock; }
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public void defer(GamificationSourceFailure failure) {
        if(users.lockById(failure.userId()).isEmpty()||repository.processed(failure.submissionId()).isPresent()) return;
        if(repository.source(failure.userId(),failure.submissionId()).isEmpty()) return;
        var at=clock.instant().plusSeconds(60);
        var row=repository.failure(failure.submissionId()).orElse(null);
        if(row==null) repository.save(new GamificationFailureEntity(failure.userId(),failure.submissionId(),failure.code(),at));
        else { row.retry(failure.code(),at); repository.save(row); }
    }
}
