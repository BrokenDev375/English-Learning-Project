package vn.edu.ptit.english.controller;

import vn.edu.ptit.english.security.AuthValidation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.english.dto.AuthRequests;
import vn.edu.ptit.english.dto.UserView;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final vn.edu.ptit.english.security.LiveConnections connections;
    private final vn.edu.ptit.english.security.AccountGuard guard;
    private final AuthService service;
    private final AuthenticationManager authentication;
    private final SessionAuthenticationStrategy sessions;
    private final SecurityContextRepository contexts;
    public AuthController(AuthService service, AuthenticationManager authentication, SessionAuthenticationStrategy sessions, SecurityContextRepository contexts, vn.edu.ptit.english.security.LiveConnections connections, vn.edu.ptit.english.security.AccountGuard guard) {
        this.connections = connections; this.guard = guard;
        this.service = service; this.authentication = authentication; this.sessions = sessions; this.contexts = contexts;
    }
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken(), "headerName", token.getHeaderName()); }
    @PostMapping("/register")
    public ResponseEntity<UserView> register(@Valid @RequestBody AuthRequests.Register input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(input));
    }
    @PostMapping("/login")
    public UserView login(@Valid @RequestBody AuthRequests.Login input, HttpServletRequest request, HttpServletResponse response) {
        AuthValidation.checkPasswordLength(input.password());
        try {
            var result = authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(AuthValidation.normalizeEmail(input.email()), input.password()));
            // End the previous identity before switching accounts in the same browser.
            // Otherwise one HttpSession could stay registered under two user ids.
            var previousSession = request.getSession(false);
            if (previousSession != null) previousSession.invalidate();
            sessions.onAuthentication(result, request, response);
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(result);
            SecurityContextHolder.setContext(context);
            contexts.saveContext(context, request, response);
            var actor = (UserPrincipal) result.getPrincipal();
            connections.register(request.getSession(), actor.id());
            try { guard.requireCurrent(actor); }
            catch (ApiException ex) {
                request.getSession().invalidate();
                SecurityContextHolder.clearContext();
                throw ex;
            }
            return service.current(actor.id());
        } catch (AuthenticationException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email hoặc mật khẩu chưa đúng.");
        }
    }
    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal UserPrincipal principal) { return service.current(principal.id()); }
}

