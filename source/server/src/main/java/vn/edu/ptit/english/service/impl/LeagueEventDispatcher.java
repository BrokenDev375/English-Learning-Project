package vn.edu.ptit.english.service.impl;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.english.repository.LeagueRepository;
import vn.edu.ptit.english.security.LiveConnections;

@Service
public class LeagueEventDispatcher {
    private final LeagueRepository repository;private final LiveConnections connections;private final ObjectMapper json;private final Clock clock;
    public LeagueEventDispatcher(LeagueRepository repository,LiveConnections connections,ObjectMapper json,Clock clock){this.repository=repository;this.connections=connections;this.json=json;this.clock=clock;}
    @Transactional public void dispatch()throws Exception{
        for(var event:repository.events(100)){
            var message=json.writeValueAsString(Map.of("version",1,"eventId",event.getEventId(),"type",event.getEventType(),"resourceId",event.getGroupId()==null?"me":event.getGroupId().toString(),
                "revision",event.getRevision(),"serverTime",event.getCreatedAt().toString()));
            if(event.getGroupId()==null)connections.notifyLeague(event.getUserId(),message);
            else for(var member:repository.members(event.getGroupId()))connections.notifyLeague(member.getUserId(),message);
            event.delivered(clock.instant());repository.save(event);
        }
    }
}
