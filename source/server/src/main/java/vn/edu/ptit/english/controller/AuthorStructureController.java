package vn.edu.ptit.english.controller;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.english.dto.AuthoringRequests;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.AuthoringService;
@RestController @RequestMapping("/api/author")
public class AuthorStructureController {
    private final AuthoringService service;
    public AuthorStructureController(AuthoringService service){this.service=service;}
    @GetMapping("/units/{id}") public ResponseEntity<?> unit(@PathVariable long id,@AuthenticationPrincipal UserPrincipal actor){return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.unit(id,actor));}
    @PutMapping("/units/{id}/lesson-order") public ResponseEntity<?> order(@PathVariable long id,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){var result=service.order(id,AuthoringRequests.order(input),actor);var response=ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore().cachePrivate());if(result.replayed())response.header("Idempotent-Replayed","true");return response.body(result.body());}
    @PostMapping("/questions/{id}/copy") public ResponseEntity<?> copy(@PathVariable long id,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){var result=service.copy(id,AuthoringRequests.copy(input),actor);var response=ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore().cachePrivate());if(result.replayed())response.header("Idempotent-Replayed","true");return response.body(result.body());}
}
