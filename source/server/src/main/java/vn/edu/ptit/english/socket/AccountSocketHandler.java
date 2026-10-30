package vn.edu.ptit.english.socket;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import vn.edu.ptit.english.security.*;

@Component
public class AccountSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper json;
    private final LiveConnections connections;
    private final AccountGuard guard;
    private final vn.edu.ptit.english.service.ConnectionService access;
    private final vn.edu.ptit.english.service.ContestService contests;
    public AccountSocketHandler(ObjectMapper json, LiveConnections connections, AccountGuard guard, vn.edu.ptit.english.service.ConnectionService access,vn.edu.ptit.english.service.ContestService contests) {
        this.json = json; this.connections = connections; this.guard = guard;
        this.access = access;
        this.contests = contests;
    }
    @Override public void afterConnectionEstablished(WebSocketSession raw) throws Exception {
        var session = new ConcurrentWebSocketSessionDecorator(raw, 5000, 65536);
        var actor = (UserPrincipal) session.getAttributes().get("actor");
        connections.attach(session, actor, (String) session.getAttributes().get("httpSessionId"));
        try {
            guard.requireCurrent(actor);
            session.sendMessage(new TextMessage(json.writeValueAsString(Map.of(
                "version", 1, "type", "CONNECTED", "userId", actor.id()))));
        } catch (RuntimeException ex) {
            session.close(CloseStatus.POLICY_VIOLATION);
        }
        session.getAttributes().put("sender", session);
    }
    @Override protected void handleTextMessage(WebSocketSession raw, TextMessage message) throws Exception {
        if (!connections.allowMessage(raw.getId())) return;
        var actor = (UserPrincipal) raw.getAttributes().get("actor");
        try { guard.requireCurrent(actor); }
        catch (RuntimeException ex) { raw.close(CloseStatus.POLICY_VIOLATION); return; }
        String requestId = "";
        String code = null;
        try {
            var input = json.readTree(message.getPayload());
            requestId = input.path("requestId").asText();
            String type = input.path("type").asText();
            if (!input.isObject() || !input.path("version").isIntegralNumber() || input.path("version").asInt() != 1 || !input.path("requestId").isTextual() || requestId.isBlank() || requestId.length() > 64 || !input.path("type").isTextual()) {
                code = "INVALID_MESSAGE";
            } else if (type.equals("SUBSCRIBE") || type.equals("UNSUBSCRIBE")) {
                if (input.has("resource")) {
                    if(input.path("resource").asText().equals("CONTEST")) {
                        var id=input.path("contestId");
                        if(input.size()!=5||!id.isIntegralNumber()||!id.canConvertToLong()||id.asLong()<1||id.asLong()>9007199254740991L
                            ||!requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))code="INVALID_SUBSCRIPTION";
                        else if(!contests.canSubscribe(id.asLong(),actor))code="RESOURCE_NOT_FOUND";
                        else if(!connections.subscribeContest(raw.getId(),id.asLong(),type.equals("SUBSCRIBE")))code="SUBSCRIPTION_LIMIT";
                    } else {
                    boolean presence = input.path("resource").asText().equals("PRESENCE");
                    if (actor.role() != (presence ? vn.edu.ptit.english.enums.Role.ADMIN : vn.edu.ptit.english.enums.Role.STUDENT)) code = "FORBIDDEN";
                    else if (!input.path("resource").isTextual()
                        || !java.util.Set.of("GAMIFICATION", "LEAGUE", "PRESENCE").contains(input.path("resource").asText())
                        || input.size() != 4
                        || !requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) code = "INVALID_SUBSCRIPTION";
                    else if (presence) { if (!connections.subscribePresence(raw.getId(), type.equals("SUBSCRIBE"))) code = "FORBIDDEN"; }
                    else raw.getAttributes().put(input.path("resource").asText().equals("LEAGUE") ? "leagueSubscribed" : "gamificationSubscribed", type.equals("SUBSCRIBE"));
                    }
                } else {
                    var value = input.path("attemptId");
                    if (!value.isIntegralNumber() || !value.canConvertToLong() || !access.canSubscribe(value.asLong(), actor)) code = "RESOURCE_NOT_FOUND";
                    else if (type.equals("UNSUBSCRIBE")) connections.unsubscribe(raw.getId(), value.asLong());
                    else if (!connections.subscribe(raw.getId(), value.asLong())) code = "SUBSCRIPTION_LIMIT";
                }
            } else if (!type.equals("HELLO") && !type.equals("HEARTBEAT")) {
                code = "UNSUPPORTED_TYPE";
            }
            if (code == null && (type.equals("HELLO") || type.equals("HEARTBEAT"))) connections.heartbeat(raw.getId());
        } catch (Exception ex) { code = "INVALID_MESSAGE"; }
        var sender = (WebSocketSession) raw.getAttributes().get("sender");
        sender.sendMessage(new TextMessage(json.writeValueAsString(code == null
            ? Map.of("version", 1, "type", "ACK", "requestId", requestId)
            : Map.of("version", 1, "type", "ERROR", "requestId", requestId, "code", code))));
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        connections.detach(session.getId());
    }
    @Override public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        connections.detach(session.getId()); session.close(CloseStatus.SERVER_ERROR);
    }
}
