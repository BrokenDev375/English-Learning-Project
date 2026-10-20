package vn.edu.ptit.english.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.english.dto.AttemptRequests;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.*;

@RestController @RequestMapping("/api/attempts/{id}")
public class AttemptCompletionController {
    private final AttemptCompletionService service;
    public AttemptCompletionController(AttemptCompletionService service){this.service=service;}
    private ResponseEntity<?> response(RequestReceiptService.Result result) {
        var response=ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore().cachePrivate());
        if(result.replayed())response.header("Idempotent-Replayed","true");
        return response.body(result.body());
    }
    @PostMapping("/items/{itemId}/hint") public ResponseEntity<?> hint(@PathVariable long id,@PathVariable long itemId,
        @RequestBody JsonNode body,@AuthenticationPrincipal UserPrincipal actor) {
        return response(service.hint(id,itemId,AttemptRequests.hint(body),actor));
    }
    @PostMapping("/items/{itemId}/check") public ResponseEntity<?> check(@PathVariable long id,@PathVariable long itemId,
        @RequestBody JsonNode body,@AuthenticationPrincipal UserPrincipal actor) {
        return response(service.check(id,itemId,AttemptRequests.check(body),actor));
    }
    @PostMapping("/items/{itemId}/retry") public ResponseEntity<?> retry(@PathVariable long id,@PathVariable long itemId,
        @RequestBody JsonNode body,@AuthenticationPrincipal UserPrincipal actor) {
        return response(service.retry(id,itemId,AttemptRequests.check(body),actor));
    }
    @PostMapping("/submit") public ResponseEntity<?> submit(@PathVariable long id,@RequestBody JsonNode body,@AuthenticationPrincipal UserPrincipal actor) {
        return response(service.submit(id,AttemptRequests.mutation(body),actor));
    }
    @GetMapping("/result") public ResponseEntity<?> result(@PathVariable long id,@AuthenticationPrincipal UserPrincipal actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.result(id,actor));
    }
}
