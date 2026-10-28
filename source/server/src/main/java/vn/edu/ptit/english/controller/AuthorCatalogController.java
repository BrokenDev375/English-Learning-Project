package vn.edu.ptit.english.controller;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.english.dto.CatalogRequests.*;
import vn.edu.ptit.english.dto.CatalogViews.*;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.CatalogService;
import vn.edu.ptit.english.service.RequestReceiptService.Result;

@RestController @RequestMapping("/api/author")
public class AuthorCatalogController {
    private final CatalogService service;
    public AuthorCatalogController(CatalogService service) { this.service = service; }
    private <T> ResponseEntity<T> read(T value) { return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(value); }
    private ResponseEntity<?> result(Result result) {
        var response = ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore().cachePrivate());
        if (result.replayed()) response.header("Idempotent-Replayed","true");
        return response.body(result.body());
    }
    @GetMapping("/topics") public ResponseEntity<CatalogPage<Topic>> topics(@RequestParam(defaultValue="") String q,
        @RequestParam(defaultValue="OWN") String scope, @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size, @AuthenticationPrincipal UserPrincipal actor) {
        return read(service.topics(q,scope,page,size,actor));
    }
    @GetMapping("/objectives") public ResponseEntity<CatalogPage<Objective>> objectives(@RequestParam(defaultValue="") String q,
        @RequestParam(defaultValue="OWN") String scope, @RequestParam(required=false) Long topicId,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
        @AuthenticationPrincipal UserPrincipal actor) { return read(service.objectives(q,scope,topicId,page,size,actor)); }
    @GetMapping("/teachers") public ResponseEntity<?> teachers(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@AuthenticationPrincipal UserPrincipal actor){return read(service.teachers(page,size,actor));}
    @GetMapping("/units") public ResponseEntity<CatalogPage<Unit>> units(@RequestParam(defaultValue="") String q,
        @RequestParam(defaultValue="OWN") String scope, @RequestParam(required=false) Long topicId,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
        @AuthenticationPrincipal UserPrincipal actor) { return read(service.units(q,scope,topicId,page,size,actor)); }
    @PostMapping("/topics") public ResponseEntity<?> createTopic(@Valid @RequestBody TopicCreate input,
        @AuthenticationPrincipal UserPrincipal actor) { return result(service.createTopic(input,actor)); }
    @PutMapping("/topics/{id}") public ResponseEntity<?> updateTopic(@PathVariable long id,@Valid @RequestBody TopicUpdate input,
        @AuthenticationPrincipal UserPrincipal actor) { return result(service.updateTopic(id,input,actor)); }
    @PostMapping("/objectives") public ResponseEntity<?> createObjective(@Valid @RequestBody ObjectiveCreate input,
        @AuthenticationPrincipal UserPrincipal actor) { return result(service.createObjective(input,actor)); }
    @PutMapping("/objectives/{id}") public ResponseEntity<?> updateObjective(@PathVariable long id,@Valid @RequestBody ObjectiveUpdate input,
        @AuthenticationPrincipal UserPrincipal actor) { return result(service.updateObjective(id,input,actor)); }
    @PostMapping("/units") public ResponseEntity<?> createUnit(@Valid @RequestBody UnitCreate input,
        @AuthenticationPrincipal UserPrincipal actor) { return result(service.createUnit(input,actor)); }
    @PutMapping("/units/{id}") public ResponseEntity<?> updateUnit(@PathVariable long id,@Valid @RequestBody UnitUpdate input,
        @AuthenticationPrincipal UserPrincipal actor) { return result(service.updateUnit(id,input,actor)); }
}
