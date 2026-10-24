package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.*;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.ConnectionService;
import vn.edu.ptit.english.socket.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountSocketHandlerTest {
    final ObjectMapper json = new ObjectMapper();
    final LiveConnectionsTest.Time clock = new LiveConnectionsTest.Time();
    final LiveConnections live = new LiveConnections(clock, json, 1000);
    final AccountGuard guard = mock(AccountGuard.class);
    final ConnectionService access = mock(ConnectionService.class);
    final AccountSocketHandler handler = new AccountSocketHandler(json, live, guard, access);
    record Client(WebSocketSession socket, UserPrincipal actor, List<JsonNode> messages) {}

    Client connect(String id, long userId, Role role) throws Exception {
        var socket = mock(WebSocketSession.class);
        var actor = mock(UserPrincipal.class);
        when(actor.id()).thenReturn(userId); when(actor.role()).thenReturn(role);
        when(socket.getId()).thenReturn(id); when(socket.isOpen()).thenReturn(true);
        var attributes = new HashMap<String, Object>();
        attributes.put("actor", actor); attributes.put("httpSessionId", "http-" + id);
        when(socket.getAttributes()).thenReturn(attributes);
        var messages = new ArrayList<JsonNode>();
        doAnswer(call -> { messages.add(json.readTree(call.getArgument(0, TextMessage.class).getPayload())); return null; })
            .when(socket).sendMessage(any());
        handler.afterConnectionEstablished(socket);
        assertThat(messages.get(0).path("type").asText()).isEqualTo("CONNECTED");
        return new Client(socket, actor, messages);
    }
    JsonNode send(Client client, String type, Map<String, Object> fields) throws Exception {
        var envelope = new HashMap<String, Object>(fields);
        envelope.put("version", 1); envelope.put("type", type); envelope.put("requestId", UUID.randomUUID().toString());
        handler.handleMessage(client.socket(), new TextMessage(json.writeValueAsString(envelope)));
        var response = client.messages().get(client.messages().size() - 1);
        assertThat(response.path("requestId").asText()).isEqualTo(envelope.get("requestId"));
        return response;
    }
    @Test void oneSocketKeepsAttemptAndRewardSubscriptionsIndependentAndOwnerScoped() throws Exception {
        var owner = connect("owner", 1, Role.STUDENT);
        var foreign = connect("foreign", 2, Role.STUDENT);
        when(access.canSubscribe(42, owner.actor())).thenReturn(true);
        assertThat(send(owner, "HELLO", Map.of()).path("type").asText()).isEqualTo("ACK");
        for (String resource : List.of("GAMIFICATION", "LEAGUE")) {
            assertThat(send(owner, "SUBSCRIBE", Map.of("resource", resource)).path("type").asText()).isEqualTo("ACK");
            assertThat(send(foreign, "SUBSCRIBE", Map.of("resource", resource)).path("type").asText()).isEqualTo("ACK");
        }
        assertThat(send(owner, "SUBSCRIBE", Map.of("attemptId", 42)).path("type").asText()).isEqualTo("ACK");
        owner.messages().clear(); foreign.messages().clear();
        live.changed(new AttemptChanged(1, 42, 3));
        live.notifyGamification(1, "{\"version\":1,\"type\":\"GAMIFICATION_CHANGED\"}");
        live.notifyLeague(1, "{\"version\":1,\"type\":\"LEAGUE_CHANGED\"}");
        assertThat(owner.messages()).extracting(m -> m.path("type").asText())
            .containsExactly("ATTEMPT_CHANGED", "GAMIFICATION_CHANGED", "LEAGUE_CHANGED");
        assertThat(foreign.messages()).isEmpty();
        assertThat(send(owner, "UNSUBSCRIBE", Map.of("resource", "GAMIFICATION")).path("type").asText()).isEqualTo("ACK");
        owner.messages().clear();
        live.notifyGamification(1, "{\"type\":\"GAMIFICATION_CHANGED\"}");
        live.changed(new AttemptChanged(1, 42, 4));
        assertThat(owner.messages()).extracting(m -> m.path("type").asText()).containsExactly("ATTEMPT_CHANGED");
        send(owner, "UNSUBSCRIBE", Map.of("attemptId", 42)); owner.messages().clear();
        live.changed(new AttemptChanged(1, 42, 5)); live.notifyLeague(1, "{\"type\":\"LEAGUE_CHANGED\"}");
        assertThat(owner.messages()).extracting(m -> m.path("type").asText()).containsExactly("LEAGUE_CHANGED");
    }
    @Test void subscriptionsRejectForeignAttemptsSpoofedScopeAndWrongRole() throws Exception {
        var student = connect("student", 1, Role.STUDENT);
        var teacher = connect("teacher", 2, Role.TEACHER);
        assertThat(send(student, "SUBSCRIBE", Map.of("attemptId", 999)).path("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(send(student, "SUBSCRIBE", Map.of("resource", "GAMIFICATION", "userId", 2)).path("code").asText()).isEqualTo("INVALID_SUBSCRIPTION");
        assertThat(send(student, "SUBSCRIBE", Map.of("resource", "LEAGUE", "attemptId", 42)).path("code").asText()).isEqualTo("INVALID_SUBSCRIPTION");
        assertThat(send(student, "SUBSCRIBE", Map.of("resource", "UNKNOWN")).path("code").asText()).isEqualTo("INVALID_SUBSCRIPTION");
        assertThat(send(teacher, "SUBSCRIBE", Map.of("resource", "GAMIFICATION")).path("code").asText()).isEqualTo("FORBIDDEN");
    }
    @Test void rewardSubscriptionDoesNotBypassHeartbeatExpiry() throws Exception {
        var client = connect("expiring", 1, Role.STUDENT);
        send(client, "SUBSCRIBE", Map.of("resource", "GAMIFICATION")); client.messages().clear();
        clock.advance(1001); live.sweep();
        assertThat(live.presence(1).online()).isFalse();
        live.notifyGamification(1, "{\"type\":\"GAMIFICATION_CHANGED\"}");
        assertThat(client.messages()).isEmpty(); verify(client.socket()).close(any(CloseStatus.class));
    }
}
