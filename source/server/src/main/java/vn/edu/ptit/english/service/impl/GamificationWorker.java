package vn.edu.ptit.english.service.impl;

import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.edu.ptit.english.service.GamificationService;

@Component @ConditionalOnProperty(name="app.gamification.worker-enabled",havingValue="true",matchIfMissing=true)
public class GamificationWorker {
    private static final Logger log=LoggerFactory.getLogger(GamificationWorker.class);
    private final GamificationService service;
    private final GamificationFailureTracker failures;
    private final GamificationEventDispatcher dispatcher;
    private long cursor;
    public GamificationWorker(GamificationService service,GamificationFailureTracker failures,GamificationEventDispatcher dispatcher) { this.service=service; this.failures=failures; this.dispatcher=dispatcher; }
    @Scheduled(fixedDelayString="${app.gamification.poll-ms:3000}",initialDelay=1500)
    public void tick() {
        try {
            var users=service.pendingUsers(cursor);
            if(users.isEmpty()) { cursor=0; users=service.pendingUsers(0); }
            for(long user:users) {
                cursor=user;
                try { service.processUser(user); }
                catch(GamificationSourceFailure failure) { failures.defer(failure); }
                catch(RuntimeException failure) { log.warn("Reward batch rolled back for user {}: {}",user,failure.getClass().getSimpleName()); }
            }
            dispatcher.dispatch();
        } catch(Exception failure) { log.warn("Reward worker will retry: {}",failure.getClass().getSimpleName()); }
    }
}
