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
    public AccountSocketHandler(ObjectMapper json, LiveConnections connections, AccountGuard guard) {
        this.json = json; this.connections = connections; this.guard = guard;
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
        var actor = (UserPrincipal) raw.getAttributes().get("actor");
        try { guard.requireCurrent(actor); }
        catch (RuntimeException ex) { raw.close(CloseStatus.POLICY_VIOLATION); return; }
        String requestId = "";
        String code = null;
        try {
            var input = json.readTree(message.getPayload());
            requestId = input.path("requestId").asText();
            String type = input.path("type").asText();
            if (input.path("version").asInt() != 1 || requestId.isBlank() || requestId.length() > 64) {
                code = "INVALID_MESSAGE";
            } else if (!type.equals("HELLO") && !type.equals("HEARTBEAT")) {
                code = "UNSUPPORTED_TYPE";
            }
        } catch (Exception ex) { code = "INVALID_MESSAGE"; }
        var sender = (WebSocketSession) raw.getAttributes().get("sender");
        sender.sendMessage(new TextMessage(json.writeValueAsString(code == null
            ? Map.of("version", 1, "type", "ACK", "requestId", requestId)
            : Map.of("version", 1, "type", "ERROR", "requestId", requestId, "code", code))));
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        connections.detach(session.getId());
    }
}
