package vn.edu.ptit.english.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.PersonalReviewService;
import static vn.edu.ptit.english.dto.MaterialRequests.invalid;

@RestController @RequestMapping("/api/me/review")
public class PersonalReviewController {
    private final PersonalReviewService service;
    public PersonalReviewController(PersonalReviewService service) { this.service = service; }
    @GetMapping public ResponseEntity<?> dashboard(@RequestParam(required = false) Long topicId,
        @RequestParam(defaultValue = "ALL") String kind, @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal UserPrincipal actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
            .body(service.dashboard(topicId, kind, page, size, actor));
    }
    @PostMapping("/sync") public ResponseEntity<?> synchronize(@RequestBody JsonNode body, @AuthenticationPrincipal UserPrincipal actor) {
        if (!body.isObject() || body.size() != 1 || !body.path("requestId").isTextual())
            throw invalid("body", "Chỉ nhận requestId dạng UUID.");
        var result = service.synchronize(body.path("requestId").asText(), actor);
        var response = ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore().cachePrivate());
        if (result.replayed()) response.header("Idempotent-Replayed", "true");
        return response.body(result.body());
    }
}
