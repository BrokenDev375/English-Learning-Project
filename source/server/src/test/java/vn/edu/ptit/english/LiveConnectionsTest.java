package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.*;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.socket.AttemptChanged;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LiveConnectionsTest {
    static class Time extends Clock {
        Instant time = Instant.parse("2026-10-21T00:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return time;} void advance(long millis){time=time.plusMillis(millis);}
    }
    final Time clock = new Time();
    final LiveConnections live = new LiveConnections(clock, new ObjectMapper(), 1000);
    WebSocketSession socket(String id, long user) {
        var s=mock(WebSocketSession.class);when(s.getId()).thenReturn(id);when(s.isOpen()).thenReturn(true);
        var actor=mock(UserPrincipal.class);when(actor.id()).thenReturn(user);
        live.attach(s,actor,"http-"+user);return s;
    }
    @Test void presenceCountsOnlyLiveSocketsForThisUserAndOneTabCanRemain() {
        socket("a",1);socket("b",1);socket("c",2);
        assertThat(live.presence(1).activeConnectionCount()).isEqualTo(2);
        live.detach("a");assertThat(live.presence(1).activeConnectionCount()).isEqualTo(1);
        clock.advance(800);live.heartbeat("b");clock.advance(300);live.sweep();
        assertThat(live.presence(1).online()).isTrue();assertThat(live.presence(2).online()).isFalse();
        clock.advance(701);assertThat(live.presence(1).online()).isFalse();
    }
    @Test void malformedTrafficCannotKeepSocketAliveAndFloodIsBounded() throws Exception {
        var a=socket("a",1);clock.advance(1001);assertThat(live.allowMessage("a")).isFalse();verify(a).close(any());
        var b=socket("b",2);for(int i=0;i<120;i++)assertThat(live.allowMessage("b")).isTrue();
        assertThat(live.allowMessage("b")).isFalse();verify(b).close(any());
    }
    @Test void invalidationsReachOnlySubscribedOwnerAndSubscriptionsAreBounded() throws Exception {
        var a=socket("a",1);var other=socket("b",2);live.subscribe("a",42);live.subscribe("b",42);
        live.changed(new AttemptChanged(1,42,3));verify(a).sendMessage(argThat(m -> m.getPayload().toString().contains("ATTEMPT_CHANGED")));verify(other,never()).sendMessage(any());
        for(long i=100;i<107;i++)assertThat(live.subscribe("a",i)).isTrue();assertThat(live.subscribe("a",108)).isFalse();
        live.unsubscribe("a",42);clearInvocations(a);live.changed(new AttemptChanged(1,42,4));verify(a,never()).sendMessage(any());
    }
}
