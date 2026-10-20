package vn.edu.ptit.english.controller;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.english.dto.AttemptRequests;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.AttemptService;
@RestController @RequestMapping("/api/attempts")
public class AttemptController {
    private final AttemptService service;
    public AttemptController(AttemptService service){this.service=service;}
    @PostMapping public ResponseEntity<?> create(@RequestBody JsonNode body,@AuthenticationPrincipal UserPrincipal actor) {
        var result=service.create(AttemptRequests.create(body),actor);
        var response=ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore().cachePrivate());
        if(result.replayed())response.header("Idempotent-Replayed","true");
        return response.body(result.body());
    }
    @GetMapping public ResponseEntity<?> list(@RequestParam(required=false) String mode,@RequestParam(required=false) String state,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@AuthenticationPrincipal UserPrincipal actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.list(mode,state,page,size,actor));
    }
}
