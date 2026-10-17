package vn.edu.ptit.english.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.ptit.english.exception.ApiException;

public class AccountGuardFilter extends OncePerRequestFilter {
    private final AccountGuard guard;
    private final ObjectMapper json;
    public AccountGuardFilter(AccountGuard guard, ObjectMapper json) { this.guard = guard; this.json = json; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal actor) {
            try {
                guard.requireCurrent(actor);
            } catch (ApiException ex) {
                var session = request.getSession(false);
                if (session != null) { try { session.invalidate(); } catch (IllegalStateException ignored) {} }
                SecurityContextHolder.clearContext();
                SecurityErrors.write(json, response, 401, ex.code(), ex.getMessage());
                return;
            } catch (DataAccessException ex) {
                SecurityErrors.write(json, response, 503, "DATABASE_UNAVAILABLE",
                    "Chưa kiểm tra được tài khoản. Vui lòng thử lại sau.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
