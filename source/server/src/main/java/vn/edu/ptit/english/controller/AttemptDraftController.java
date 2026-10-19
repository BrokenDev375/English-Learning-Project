package vn.edu.ptit.english.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.english.dto.AttemptRequests;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.AttemptDraftService;

@RestController @RequestMapping("/api/attempts/{id}")
public class AttemptDraftController {
    private final AttemptDraftService service;
    public AttemptDraftController(AttemptDraftService service){this.service=service;}
    @GetMapping public ResponseEntity<?> read(@PathVariable long id,@AuthenticationPrincipal UserPrincipal actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.read(id,actor));
    }
    @PutMapping("/draft") public ResponseEntity<?> save(@PathVariable long id,@RequestBody JsonNode body,@AuthenticationPrincipal UserPrincipal actor) {
        var result=service.save(id,AttemptRequests.mutation(body),actor);
        var response=ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore().cachePrivate());
        if(result.replayed())response.header("Idempotent-Replayed","true");
        return response.body(result.body());
    }
}
