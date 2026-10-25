package vn.edu.ptit.english.controller;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.GamificationService;
import vn.edu.ptit.english.service.impl.*;

@RestController @RequestMapping("/api/me")
public class GamificationController {
    private final GamificationService service;
    private final GamificationFailureTracker failures;
    public GamificationController(GamificationService service,GamificationFailureTracker failures) { this.service=service; this.failures=failures; }
    private ResponseEntity<?> read(Object body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(body); }
    private void query(Map<String,String> query,String... allowed) { if(!Set.of(allowed).containsAll(query.keySet())) throw GamificationServiceImpl.invalid(); }
    private int integer(Map<String,String> query,String name,int fallback) {
        try { return query.containsKey(name)?Integer.parseInt(query.get(name)):fallback; }
        catch(NumberFormatException e) { throw GamificationServiceImpl.invalid(); }
    }
    @GetMapping("/gamification") public ResponseEntity<?> summary(@RequestParam Map<String,String> query,@AuthenticationPrincipal UserPrincipal actor) {
        query(query); return read(service.summary(actor));
    }
    @GetMapping("/badges") public ResponseEntity<?> badges(@RequestParam Map<String,String> query,@AuthenticationPrincipal UserPrincipal actor) {
        query(query); return read(service.badges(actor));
    }
    @GetMapping("/gamification/activities") public ResponseEntity<?> activities(@RequestParam Map<String,String> query,@AuthenticationPrincipal UserPrincipal actor) {
        query(query,"submissionId","page","size"); Long source=null;
        try { if(query.containsKey("submissionId")) source=Long.valueOf(query.get("submissionId")); }
        catch(NumberFormatException e) { throw GamificationServiceImpl.invalid(); }
        return read(service.activities(source,integer(query,"page",0),integer(query,"size",20),actor));
    }
    @PostMapping("/gamification/sync") public ResponseEntity<?> sync(@RequestBody JsonNode body,@AuthenticationPrincipal UserPrincipal actor) {
        if(!body.isObject()||body.size()!=1||!body.path("requestId").isTextual()) throw GamificationServiceImpl.invalid();
        try {
            var result=service.synchronize(body.path("requestId").asText(),actor);
            var response=ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore().cachePrivate());
            if(result.replayed()) response.header("Idempotent-Replayed","true"); return response.body(result.body());
        } catch(GamificationSourceFailure failure) { failures.defer(failure); throw failure; }
    }
}
