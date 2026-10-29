package vn.edu.ptit.english.config;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.socket.AccountSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final AccountSocketHandler handler;
    private final AccountGuard guard;
    private final String[] origins;
    private final SessionActivityRegistry activity;
    public WebSocketConfig(AccountSocketHandler handler, AccountGuard guard,
        @Value("${app.socket.allowed-origins}") String[] origins, SessionActivityRegistry activity) {
        this.handler = handler; this.guard = guard; this.origins = origins;
        this.activity=activity;
    }
    @org.springframework.context.annotation.Bean
    public org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean socketContainer() {
        var container = new org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(4096);
        container.setMaxSessionIdleTimeout(75000L);
        return container;
    }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/events").setAllowedOrigins(origins)
            .addInterceptors(new HandshakeInterceptor() {
                @Override public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                    WebSocketHandler handler, Map<String, Object> attributes) {
                    if (!(request.getPrincipal() instanceof Authentication auth)
                        || !(auth.getPrincipal() instanceof UserPrincipal actor)
                        || !(request instanceof ServletServerHttpRequest servlet)) return false;
                    var session = servlet.getServletRequest().getSession(false);
                    if (session == null) return false;
                    if (!activity.current(session.getId())) return false;
                    guard.requireCurrent(actor);
                    attributes.put("actor", actor);
                    attributes.put("httpSessionId", session.getId());
                    return true;
                }
                @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                    WebSocketHandler handler, Exception exception) {}
            });
    }
}
