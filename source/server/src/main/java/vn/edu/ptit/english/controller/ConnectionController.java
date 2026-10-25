package vn.edu.ptit.english.controller;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.ConnectionService;

@RestController @RequestMapping("/api/me/connections")
public class ConnectionController {
    private final ConnectionService service;
    public ConnectionController(ConnectionService service) { this.service = service; }
    @GetMapping public ResponseEntity<?> presence(@AuthenticationPrincipal UserPrincipal actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.presence(actor));
    }
}
