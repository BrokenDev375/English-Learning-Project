package vn.edu.ptit.english.service.impl;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @ConditionalOnProperty(name="app.gamification.worker-enabled",havingValue="true",matchIfMissing=true)
public class LeagueWorker {
    private static final Logger log=LoggerFactory.getLogger(LeagueWorker.class);
    private final LeagueLifecycle lifecycle;private final LeagueEventDispatcher dispatcher;
    public LeagueWorker(LeagueLifecycle lifecycle,LeagueEventDispatcher dispatcher){this.lifecycle=lifecycle;this.dispatcher=dispatcher;}
    @Scheduled(fixedDelayString="${app.gamification.poll-ms:3000}",initialDelay=1000)
    public void tick(){try{lifecycle.ensureCurrent();for(long season:lifecycle.due())for(long group:lifecycle.close(season))lifecycle.finalizeGroup(season,group);
        dispatcher.dispatch();}catch(Exception failure){log.warn("League job will resume: {}",failure.getClass().getSimpleName());}}
}
