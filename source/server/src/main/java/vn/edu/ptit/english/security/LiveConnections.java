package vn.edu.ptit.english.security;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import org.springframework.web.socket.*;

@Component
public class LiveConnections implements HttpSessionListener {
    private record Session(long userId, HttpSession session) {}
    private record Socket(long userId, String httpSessionId, WebSocketSession session) {}
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Socket> sockets = new ConcurrentHashMap<>();

    public void register(HttpSession session, long userId) {
        sessions.put(session.getId(), new Session(userId, session));
    }
    public void attach(WebSocketSession session, UserPrincipal actor, String httpSessionId) {
        sockets.put(session.getId(), new Socket(actor.id(), httpSessionId, session));
    }
    public void detach(String id) { sockets.remove(id); }
    @Override public void sessionDestroyed(HttpSessionEvent event) {
        String id = event.getSession().getId();
        sessions.remove(id);
        sockets.forEach((key, connection) -> {
            if (connection.httpSessionId().equals(id)) close(connection.session());
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
            if (connection.userId() == event.userId()) close(connection.session());
        });
    }
    private static void close(WebSocketSession session) {
        try { session.close(new CloseStatus(1008, "SESSION_REVOKED")); } catch (IOException ignored) {}
    }
}
