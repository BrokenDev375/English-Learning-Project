package vn.edu.ptit.english.controller;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.LeagueService;
import vn.edu.ptit.english.service.impl.GamificationServiceImpl;

@RestController @RequestMapping("/api/me/league")
public class LeagueController {
    private final LeagueService service;
    public LeagueController(LeagueService service){this.service=service;}
    private ResponseEntity<?> read(Object body){return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(body);}
    private void query(Map<String,String> query,String... allowed){if(!Set.of(allowed).containsAll(query.keySet()))throw GamificationServiceImpl.invalid();}
    private int integer(Map<String,String> query,String key,int fallback){try{return query.containsKey(key)?Integer.parseInt(query.get(key)):fallback;}catch(NumberFormatException e){throw GamificationServiceImpl.invalid();}}
    @GetMapping public ResponseEntity<?> overview(@RequestParam Map<String,String> query,@AuthenticationPrincipal UserPrincipal actor){query(query);return read(service.overview(actor));}
    @GetMapping("/leaderboard") public ResponseEntity<?> board(@RequestParam Map<String,String> query,@AuthenticationPrincipal UserPrincipal actor){query(query,"page","size");return read(service.leaderboard(integer(query,"page",0),integer(query,"size",25),actor));}
    @GetMapping("/history") public ResponseEntity<?> history(@RequestParam Map<String,String> query,@AuthenticationPrincipal UserPrincipal actor){query(query,"page","size");return read(service.history(integer(query,"page",0),integer(query,"size",10),actor));}
    @PostMapping("/join") public ResponseEntity<?> join(@RequestBody JsonNode body,@AuthenticationPrincipal UserPrincipal actor){
        if(!body.isObject()||body.size()!=1||!body.path("requestId").isTextual())throw GamificationServiceImpl.invalid();
        var result=service.join(body.path("requestId").asText(),actor);var response=ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore().cachePrivate());
        if(result.replayed())response.header("Idempotent-Replayed","true");return response.body(result.body());
    }
}
