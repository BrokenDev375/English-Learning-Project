package vn.edu.ptit.english.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.english.repository.GamificationRepository;
import vn.edu.ptit.english.security.LiveConnections;

@Service
public class GamificationEventDispatcher {
    private final GamificationRepository repository;
    private final LiveConnections connections;
    private final ObjectMapper json;
    private final Clock clock;
    public GamificationEventDispatcher(GamificationRepository repository,LiveConnections connections,ObjectMapper json,Clock clock) {
        this.repository=repository; this.connections=connections; this.json=json; this.clock=clock;
    }
    @Transactional
    public void dispatch() throws Exception {
        for(var event:repository.pendingEvents(100)) {
            String message=json.writeValueAsString(Map.of("version",1,"eventId",event.getEventId(),"type",event.getEventType(),
                "resourceId","me","revision",event.getRevision(),"serverTime",event.getCreatedAt().toString()));
            connections.notifyGamification(event.getUserId(),message); event.delivered(clock.instant()); repository.save(event);
        }
    }
}
