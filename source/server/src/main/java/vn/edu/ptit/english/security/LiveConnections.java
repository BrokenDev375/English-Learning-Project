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
import vn.edu.ptit.english.enums.Role;

@Component
public class LiveConnections implements HttpSessionListener {
    private record Session(long userId, HttpSession session) {}
    private static final class Socket {
        final long userId;
        final String httpSessionId;
        final WebSocketSession session;
        final Role role;
        volatile boolean presenceSubscribed;
        final Set<Long> subscriptions = ConcurrentHashMap.newKeySet();
        final Set<Long> contests = ConcurrentHashMap.newKeySet();
        volatile long lastSeen;
        long window; int messages;
        Socket(long userId, Role role, String httpSessionId, WebSocketSession session, long now) {
            this.userId = userId; this.role = role; this.httpSessionId = httpSessionId; this.session = session; this.lastSeen = now; this.window = now;
        }
    }
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Socket> sockets = new ConcurrentHashMap<>();
    private final Clock clock;
    private final long timeout;
    private final ObjectMapper json;
    private OnlineSnapshot announcedPresence = new OnlineSnapshot(Set.of(), 0);
    private boolean publishingPresence;
    public LiveConnections(Clock clock, ObjectMapper json, @Value("${app.socket.presence-timeout-ms:75000}") long timeout) {
        if (timeout < 500 || timeout > 300000) throw new IllegalArgumentException("Invalid presence timeout");
        this.clock = clock; this.json = json; this.timeout = timeout;
    }

    public void register(HttpSession session, long userId) {
        sessions.put(session.getId(), new Session(userId, session));
    }
    public void attach(WebSocketSession session, UserPrincipal actor, String httpSessionId) {
        sockets.put(session.getId(), new Socket(actor.id(), actor.role(), httpSessionId, session, clock.millis()));
        presenceChanged();
    }
    public void detach(String id) { if (sockets.remove(id) != null) presenceChanged(); }
    public boolean allowMessage(String id) {
        var socket = sockets.get(id); if (socket == null) return false;
        synchronized (socket) {
            long now = clock.millis();
            if (now - socket.lastSeen >= timeout) { expire(socket); presenceChanged(); return false; }
            if (now - socket.window >= 60000) { socket.window = now; socket.messages = 0; }
            if (++socket.messages > 120) { expire(socket); presenceChanged(); return false; }
            return true;
        }
    }
    public void heartbeat(String id) {
        var socket = sockets.get(id);
        if (socket != null) { long now = clock.millis(); boolean stale = now - socket.lastSeen >= timeout; socket.lastSeen = now; if (stale) presenceChanged(); }
    }
    public boolean subscribePresence(String id, boolean enabled) {
        var socket = sockets.get(id);
        if (socket == null || socket.role != Role.ADMIN) return false;
        socket.presenceSubscribed = enabled; return true;
    }
    public boolean subscribe(String id, long attemptId) {
        var socket = sockets.get(id); if (socket == null) return false;
        synchronized (socket) {
            if (!socket.subscriptions.contains(attemptId) && socket.subscriptions.size() >= 8) return false;
            socket.subscriptions.add(attemptId); return true;
        }
    }
    public void unsubscribe(String id, long attemptId) { var socket = sockets.get(id); if (socket != null) socket.subscriptions.remove(attemptId); }
    public boolean subscribeContest(String id,long contestId,boolean enabled) {
        var socket=sockets.get(id);if(socket==null)return false;
        if(!enabled){socket.contests.remove(contestId);return true;}
        if(!socket.contests.contains(contestId)&&socket.contests.size()>=4)return false;
        socket.contests.add(contestId);return true;
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void contestChanged(vn.edu.ptit.english.socket.ContestChanged event) {
        sockets.forEach((id,socket)->{
            if(socket.session.isOpen()&&socket.contests.contains(event.contestId())
                &&(event.userId()==null||socket.userId==event.userId()||socket.role!=Role.STUDENT)) {
                try{socket.session.sendMessage(new TextMessage(json.writeValueAsString(Map.of("version",1,"type","CONTEST_CHANGED","contestId",event.contestId()))));}
                catch(IOException|RuntimeException ignored){expire(socket);}
            }
        });
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void contestPermissionsChanged(vn.edu.ptit.english.socket.ContestPermissionsChanged event) {
        sockets.forEach((id,socket)->{
            if(socket.userId==event.userId()&&socket.session.isOpen()) {
                if(!event.canProctor())socket.contests.clear();
                try{socket.session.sendMessage(new TextMessage("{\"version\":1,\"type\":\"CONTEST_PERMISSIONS_CHANGED\"}"));}
                catch(IOException|RuntimeException ignored){expire(socket);}
            }
        });
    }
    @Scheduled(fixedDelayString = "${app.socket.sweep-ms:5000}")
    public void sweep() {
        sockets.forEach((id, socket) -> { if (!socket.session.isOpen() || clock.millis() - socket.lastSeen >= timeout) expire(socket); });
        presenceChanged();
    }
    private void expire(Socket socket) {
        sockets.remove(socket.session.getId(), socket);
        try { socket.session.close(new CloseStatus(1001, "HEARTBEAT_TIMEOUT")); } catch (IOException | RuntimeException ignored) {}
    }
    /** Invalidation only: admins re-read the authorized snapshot, including after subscription ACK. */
    private synchronized void presenceChanged() {
        if (publishingPresence) return;
        publishingPresence = true;
        try {
            for (;;) {
                var current = onlineSnapshot();
                if (current.equals(announcedPresence)) return;
                announcedPresence = current;
                for (var socket : sockets.values()) {
                    if (socket.role != Role.ADMIN || !socket.presenceSubscribed || !socket.session.isOpen()
                        || clock.millis() - socket.lastSeen >= timeout) continue;
                    try { socket.session.sendMessage(new TextMessage("{\"version\":1,\"type\":\"PRESENCE_CHANGED\"}")); }
                    catch (IOException | RuntimeException ignored) { expire(socket); }
                }
            }
        } finally { publishingPresence = false; }
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
        presenceChanged();
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
        presenceChanged();
    }
    public record Snapshot(long connectedAccounts, long openConnections) {}
    public record OnlineSnapshot(Set<Long> userIds, long openConnections) {}
    /** Read-only local snapshot of open authenticated sockets within the heartbeat window. */
    public Snapshot snapshot() {
        var online = onlineSnapshot(); return new Snapshot(online.userIds().size(), online.openConnections());
    }
    public OnlineSnapshot onlineSnapshot() {
        long now = clock.millis();
        var open = sockets.values().stream().filter(s -> s.session.isOpen() && now - s.lastSeen < timeout).toList();
        return new OnlineSnapshot(open.stream().map(s -> s.userId).collect(java.util.stream.Collectors.toUnmodifiableSet()), open.size());
    }
    @Override public void sessionDestroyed(HttpSessionEvent event) {
        String id = event.getSession().getId();
        sessions.remove(id);
        sockets.forEach((key, connection) -> {
            if (connection.httpSessionId.equals(id)) { sockets.remove(key, connection); close(connection.session); }
        });
        presenceChanged();
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
        presenceChanged();
    }
    private static void close(WebSocketSession session) {
        try { session.close(new CloseStatus(1008, "SESSION_REVOKED")); } catch (IOException ignored) {}
    }
}
