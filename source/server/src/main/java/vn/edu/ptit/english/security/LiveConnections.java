package vn.edu.ptit.english.security;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import org.springframework.web.socket.*;

@Component
public class LiveConnections implements HttpSessionListener {
    private record Session(long userId, HttpSession session) {}
    private static final class Socket {
        final long userId;
        final String httpSessionId;
        final WebSocketSession session;
        final Set<Long> subscriptions = ConcurrentHashMap.newKeySet();
        volatile long lastSeen;
        long window; int messages;
        Socket(long userId, String httpSessionId, WebSocketSession session, long now) {
            this.userId = userId; this.httpSessionId = httpSessionId; this.session = session; this.lastSeen = now; this.window = now;
        }
    }
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Socket> sockets = new ConcurrentHashMap<>();
    private final Clock clock;
    private final long timeout;
    private final ObjectMapper json;
    public LiveConnections(Clock clock, ObjectMapper json, @Value("${app.socket.presence-timeout-ms:75000}") long timeout) {
        if (timeout < 500 || timeout > 300000) throw new IllegalArgumentException("Invalid presence timeout");
        this.clock = clock; this.json = json; this.timeout = timeout;
    }

    public void register(HttpSession session, long userId) {
        sessions.put(session.getId(), new Session(userId, session));
    }
    public void attach(WebSocketSession session, UserPrincipal actor, String httpSessionId) {
        sockets.put(session.getId(), new Socket(actor.id(), httpSessionId, session, clock.millis()));
    }
    public void detach(String id) { sockets.remove(id); }
    public boolean allowMessage(String id) {
        var socket = sockets.get(id); if (socket == null) return false;
        synchronized (socket) {
            long now = clock.millis();
            if (now - socket.lastSeen >= timeout) { expire(socket); return false; }
            if (now - socket.window >= 60000) { socket.window = now; socket.messages = 0; }
            if (++socket.messages > 120) { expire(socket); return false; }
            return true;
        }
    }
    public void heartbeat(String id) { var socket = sockets.get(id); if (socket != null) socket.lastSeen = clock.millis(); }
    public boolean subscribe(String id, long attemptId) {
        var socket = sockets.get(id); if (socket == null) return false;
        synchronized (socket) {
            if (!socket.subscriptions.contains(attemptId) && socket.subscriptions.size() >= 8) return false;
            socket.subscriptions.add(attemptId); return true;
        }
    }
    public void unsubscribe(String id, long attemptId) { var socket = sockets.get(id); if (socket != null) socket.subscriptions.remove(attemptId); }
    @Scheduled(fixedDelayString = "${app.socket.sweep-ms:5000}")
    public void sweep() {
        sockets.forEach((id, socket) -> { if (!socket.session.isOpen() || clock.millis() - socket.lastSeen >= timeout) expire(socket); });
    }
    private void expire(Socket socket) {
        sockets.remove(socket.session.getId(), socket);
        try { socket.session.close(new CloseStatus(1001, "HEARTBEAT_TIMEOUT")); } catch (IOException | RuntimeException ignored) {}
    }
    public vn.edu.ptit.english.dto.ConnectionViews.Presence presence(long userId) {
        sweep();
        int count = (int) sockets.values().stream().filter(s -> s.userId == userId && s.session.isOpen()).count();
        return new vn.edu.ptit.english.dto.ConnectionViews.Presence(count > 0, count, clock.instant());
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void changed(vn.edu.ptit.english.socket.AttemptChanged event) {
        sockets.forEach((id, socket) -> {
            if (socket.userId == event.userId() && socket.subscriptions.contains(event.attemptId()) && socket.session.isOpen()) {
                try { socket.session.sendMessage(new TextMessage(json.writeValueAsString(Map.of(
                    "version", 1, "type", "ATTEMPT_CHANGED", "attemptId", event.attemptId(), "revision", event.revision())))); }
                catch (IOException | RuntimeException ignored) { expire(socket); }
            }
        });
    }
    public void notifyGamification(long userId, String message) {
        notifyResource(userId, "gamificationSubscribed", message);
    }
    public void notifyLeague(long userId, String message) {
        notifyResource(userId, "leagueSubscribed", message);
    }
    private void notifyResource(long userId, String subscription, String message) {
        sockets.forEach((id, socket) -> {
            if (socket.userId == userId && socket.session.isOpen()
                && Boolean.TRUE.equals(socket.session.getAttributes().get(subscription))) {
                try { socket.session.sendMessage(new TextMessage(message)); }
                catch (IOException | RuntimeException ignored) { expire(socket); }
            }
        });
    }
    public record Snapshot(long connectedAccounts, long openConnections) {}
    /** Read-only local snapshot of open authenticated sockets within the heartbeat window. */
    public Snapshot snapshot() {
        var open = sockets.values().stream().filter(s -> s.session.isOpen() && clock.millis() - s.lastSeen < timeout).toList();
        return new Snapshot(open.stream().map(s -> s.userId).distinct().count(), open.size());
    }
    @Override public void sessionDestroyed(HttpSessionEvent event) {
        String id = event.getSession().getId();
        sessions.remove(id);
        sockets.forEach((key, connection) -> {
            if (connection.httpSessionId.equals(id)) { sockets.remove(key, connection); close(connection.session); }
        });
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void accessChanged(AccountAccessChanged event) {
        sessions.forEach((id, connection) -> {
            if (connection.userId() == event.userId()) {
                sessions.remove(id);
                try { connection.session().invalidate(); } catch (IllegalStateException ignored) {}
            }
        });
        sockets.forEach((id, connection) -> {
            if (connection.userId == event.userId()) { sockets.remove(id, connection); close(connection.session); }
        });
    }
    private static void close(WebSocketSession session) {
        try { session.close(new CloseStatus(1008, "SESSION_REVOKED")); } catch (IOException ignored) {}
    }
}
