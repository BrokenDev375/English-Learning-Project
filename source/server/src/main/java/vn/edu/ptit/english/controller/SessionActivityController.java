package vn.edu.ptit.english.controller;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.SessionActivityService;
@RestController @RequestMapping("/api/auth")
public class SessionActivityController {
    private final SessionActivityService service;
    public SessionActivityController(SessionActivityService service){this.service=service;}
    private String sessionId(HttpServletRequest r) {
        var session=r.getSession(false);
        return session==null?r.getRequestedSessionId():session.getId();
    }
    @GetMapping("/session") public ResponseEntity<?> state(HttpServletRequest r,@AuthenticationPrincipal UserPrincipal actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.state(sessionId(r),actor));
    }
    @PostMapping("/activity") public ResponseEntity<?> report(@RequestBody JsonNode body,HttpServletRequest r,@AuthenticationPrincipal UserPrincipal actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.report(sessionId(r),body,actor));
    }
}
