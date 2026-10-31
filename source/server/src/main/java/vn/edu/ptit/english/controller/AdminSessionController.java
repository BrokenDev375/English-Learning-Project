package vn.edu.ptit.english.controller;

import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import vn.edu.ptit.english.dto.AdminSessionQuery;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.AdminSessionService;

@RestController
@RequestMapping("/api/admin/sessions")
public class AdminSessionController {
    private final AdminSessionService service;
    public AdminSessionController(AdminSessionService service){this.service=service;}
    private ResponseEntity<?> response(Object body){return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(body);}
    @GetMapping public ResponseEntity<?> list(@RequestParam MultiValueMap<String,String> params,@AuthenticationPrincipal UserPrincipal actor){
        return response(service.list(AdminSessionQuery.parse(params),actor));
    }
    @GetMapping("/{sessionRef}") public ResponseEntity<?> detail(@PathVariable String sessionRef,@AuthenticationPrincipal UserPrincipal actor){
        return response(service.detail(sessionRef,actor));
    }
}
