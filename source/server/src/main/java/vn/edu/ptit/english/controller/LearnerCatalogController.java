package vn.edu.ptit.english.controller;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.LearnerCatalogService;
@RestController @RequestMapping("/api")
public class LearnerCatalogController {
    private final LearnerCatalogService service;
    public LearnerCatalogController(LearnerCatalogService service){this.service=service;}
    private ResponseEntity<?> read(Object v){return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(v);}
    @GetMapping("/{kind:topics|objectives|units}") public ResponseEntity<?> list(@PathVariable String kind,@RequestParam(defaultValue="") String q,@RequestParam(required=false) Long topicId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@AuthenticationPrincipal UserPrincipal actor){return read(service.list(kind,q,topicId,page,size,actor));}
    @GetMapping("/units/{id}") public ResponseEntity<?> unit(@PathVariable long id,@AuthenticationPrincipal UserPrincipal actor){return read(service.unit(id,actor));}
    @GetMapping("/lessons/{id}") public ResponseEntity<?> lesson(@PathVariable long id,@AuthenticationPrincipal UserPrincipal actor){return read(service.lesson(id,actor));}
}
