package vn.edu.ptit.english.service.impl;

import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.edu.ptit.english.service.ContestService;

@Component
@ConditionalOnProperty(name="app.contest.worker-enabled",havingValue="true",matchIfMissing=true)
public class ContestWorker {
    private static final Logger log=LoggerFactory.getLogger(ContestWorker.class);
    private final ContestService contests;
    public ContestWorker(ContestService contests){this.contests=contests;}
    @Scheduled(fixedDelayString="${app.contest.poll-ms:1000}",initialDelay=1000)
    public void tick(){try{for(long id:contests.activeIds())try{contests.tick(id);}catch(RuntimeException error){log.warn("Contest {} settlement will retry: {}",id,error.getClass().getSimpleName());}}catch(RuntimeException error){log.warn("Contest schedule will retry: {}",error.getClass().getSimpleName());}}
}
