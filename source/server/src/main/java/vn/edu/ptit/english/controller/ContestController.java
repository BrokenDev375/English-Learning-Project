package vn.edu.ptit.english.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.english.dto.ContestData;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.ContestService;
import vn.edu.ptit.english.service.RequestReceiptService.Result;

@RestController @RequestMapping("/api")
public class ContestController {
    private final ContestService contests;
    public ContestController(ContestService contests){this.contests=contests;}
    private ResponseEntity<?> read(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(value);}
    private ResponseEntity<?> result(Result value){var response=ResponseEntity.status(value.status()).cacheControl(CacheControl.noStore().cachePrivate());if(value.replayed())response.header("Idempotent-Replayed","true");return response.body(value.body());}
    @GetMapping("/contests") public ResponseEntity<?> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@AuthenticationPrincipal UserPrincipal actor){return read(contests.list(page,size,actor));}
    @GetMapping("/author/contests/{id}") public ResponseEntity<?> author(@PathVariable long id,@AuthenticationPrincipal UserPrincipal actor){return read(contests.author(id,actor));}
    @PostMapping("/author/contests") public ResponseEntity<?> create(@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(contests.create(ContestData.parse(input,"create"),actor));}
    @PutMapping("/author/contests/{id}/{action:draft|paper}") public ResponseEntity<?> edit(@PathVariable long id,@PathVariable String action,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(contests.configure(id,action,ContestData.parse(input,action),actor));}
    @PostMapping("/author/contests/{id}/{action:publish|close|cancel|archive}") public ResponseEntity<?> control(@PathVariable long id,@PathVariable String action,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(contests.configure(id,action,ContestData.parse(input,action),actor));}
    @GetMapping("/author/contests/{id}/monitor") public ResponseEntity<?> monitor(@PathVariable long id,@RequestParam(defaultValue="ALL") String filter,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@AuthenticationPrincipal UserPrincipal actor){return read(contests.monitor(id,filter,page,size,actor));}
    @GetMapping("/author/contests/{id}/events") public ResponseEntity<?> events(@PathVariable long id,@RequestParam(required=false) Long participantId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@AuthenticationPrincipal UserPrincipal actor){return read(contests.events(id,participantId,page,size,actor));}
    @PostMapping("/author/contests/{id}/participants/{participant}/{action:approve|reject|revoke|acknowledge}") public ResponseEntity<?> decide(@PathVariable long id,@PathVariable long participant,@PathVariable String action,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(contests.decide(id,participant,action,ContestData.parse(input,action),actor));}
    @GetMapping("/me/contests/{id}") public ResponseEntity<?> learner(@PathVariable long id,@AuthenticationPrincipal UserPrincipal actor){return read(contests.learner(id,actor));}
    @PostMapping("/me/contests/{id}/{action:register|check-in|claim|save|submit|visibility}") public ResponseEntity<?> participate(@PathVariable long id,@PathVariable String action,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(contests.participate(id,action,ContestData.parse(input,action),actor));}
    @PostMapping("/me/contests/{id}/heartbeat") public ResponseEntity<?> heartbeat(@PathVariable long id,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return read(contests.heartbeat(id,input,actor));}
    @GetMapping("/contests/{id}/leaderboard") public ResponseEntity<?> board(@PathVariable long id,@AuthenticationPrincipal UserPrincipal actor){return read(contests.board(id,actor));}
}
