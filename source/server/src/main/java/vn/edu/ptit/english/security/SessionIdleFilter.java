package vn.edu.ptit.english.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.ptit.english.exception.ApiException;

public class SessionIdleFilter extends OncePerRequestFilter {
    private final SessionActivityRegistry activity;
    private final ObjectMapper json;
    public SessionIdleFilter(SessionActivityRegistry activity,ObjectMapper json){this.activity=activity;this.json=json;}
    @Override protected boolean shouldNotFilter(HttpServletRequest r) {
        return Set.of("/api/health","/api/auth/csrf","/api/auth/login","/api/auth/register","/error").contains(r.getServletPath());
    }
    @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain) throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        var session=r.getSession(false);
        try {
            if(auth!=null && auth.getPrincipal() instanceof UserPrincipal)
                activity.state(session==null?r.getRequestedSessionId():session.getId());
            else if(activity.idleReason(r.getRequestedSessionId()))throw activity.unauthorized(r.getRequestedSessionId());
        } catch(ApiException ex) {
            SecurityContextHolder.clearContext();
            SecurityErrors.write(json,s,401,ex.code(),ex.getMessage());return;
        }
        chain.doFilter(r,s);
    }
}
