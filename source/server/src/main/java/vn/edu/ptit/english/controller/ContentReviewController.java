package vn.edu.ptit.english.controller;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.RequestReceiptService.Result;
import vn.edu.ptit.english.service.impl.*;
@RestController @RequestMapping("/api/author")
public class ContentReviewController {
    private final ContentReviewService reviews;private final ContentGovernance governance;private final AccountGuard accounts;
    public ContentReviewController(ContentReviewService reviews,ContentGovernance governance,AccountGuard accounts){this.reviews=reviews;this.governance=governance;this.accounts=accounts;}
    private ResponseEntity<?> read(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(value);}
    private ResponseEntity<?> result(Result value){var response=ResponseEntity.status(value.status()).cacheControl(CacheControl.noStore().cachePrivate());if(value.replayed())response.header("Idempotent-Replayed","true");return response.body(value.body());}
    @GetMapping("/reviews") public ResponseEntity<?> list(@RequestParam(defaultValue="PENDING") String state,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@AuthenticationPrincipal UserPrincipal actor){return read(reviews.list(state,page,size,actor));}
    @GetMapping("/reviews/{id}") public ResponseEntity<?> detail(@PathVariable long id,@AuthenticationPrincipal UserPrincipal actor){return read(reviews.detail(id,actor));}
    @PostMapping("/{resource:questions|lessons}/{id}/submit-review") public ResponseEntity<?> submit(@PathVariable String resource,@PathVariable long id,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(reviews.submit(resource,id,MaterialRequests.command(input,true),actor));}
    @PostMapping("/reviews/{id}/{action:approve|return|withdraw}") public ResponseEntity<?> decide(@PathVariable long id,@PathVariable String action,@Valid @RequestBody ContentReviewRequests.Decision input,@AuthenticationPrincipal UserPrincipal actor){return result(reviews.decide(id,action,input,actor));}
    @GetMapping("/versions/{resource:questions|materials}/{id}") public ResponseEntity<?> version(@PathVariable String resource,@PathVariable long id,@AuthenticationPrincipal UserPrincipal actor){accounts.requireCurrent(actor);AssetServiceImpl.author(actor);return read(governance.version(resource,AuthoringRequests.id(id),actor));}
}
