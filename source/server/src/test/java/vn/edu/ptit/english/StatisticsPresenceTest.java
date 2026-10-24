package vn.edu.ptit.english;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.enums.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatisticsPresenceTest {
    @Test void multipleTabsCountOneAccountAndClosedSocketsAreNotReported() {
        var connections = new LiveConnections();
        var a = socket("a", true); var b = socket("b", true); var c = socket("c", false);
        var user = new UserPrincipal(1L, "a@test.local", "", Role.STUDENT, AccountStatus.ACTIVE, 0);
        connections.attach(a, user, "http-1"); connections.attach(b, user, "http-2");
        connections.attach(c, new UserPrincipal(2L, "b@test.local", "", Role.STUDENT, AccountStatus.ACTIVE, 0), "http-3");
        assertThat(connections.snapshot().connectedAccounts()).isEqualTo(1);
        assertThat(connections.snapshot().openConnections()).isEqualTo(2);
        connections.detach("a"); assertThat(connections.snapshot().openConnections()).isEqualTo(1);
        connections.detach("b"); assertThat(connections.snapshot().connectedAccounts()).isZero();
    }
    WebSocketSession socket(String id, boolean open) {
        var socket = mock(WebSocketSession.class); when(socket.getId()).thenReturn(id); when(socket.isOpen()).thenReturn(open); return socket;
    }
}
