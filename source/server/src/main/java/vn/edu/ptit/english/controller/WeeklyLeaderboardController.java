package vn.edu.ptit.english.controller;

import java.util.Map;
import java.util.Set;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.WeeklyLeaderboardService;
import vn.edu.ptit.english.service.impl.GamificationServiceImpl;

@RestController
@RequestMapping("/api/me/leaderboard")
public class WeeklyLeaderboardController {
    private final WeeklyLeaderboardService service;
    public WeeklyLeaderboardController(WeeklyLeaderboardService service){this.service=service;}
    private int integer(Map<String,String> query,String key,int fallback) {
        try{return query.containsKey(key)?Integer.parseInt(query.get(key)):fallback;}
        catch(NumberFormatException e){throw GamificationServiceImpl.invalid();}
    }
    @GetMapping public ResponseEntity<?> read(@RequestParam Map<String,String> query,@AuthenticationPrincipal UserPrincipal actor) {
        if(!Set.of("page","size").containsAll(query.keySet()))throw GamificationServiceImpl.invalid();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
            .body(service.read(integer(query,"page",0),integer(query,"size",20),actor));
    }
}
