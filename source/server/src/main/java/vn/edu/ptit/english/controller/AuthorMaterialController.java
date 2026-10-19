package vn.edu.ptit.english.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.english.dto.MaterialRequests;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.MaterialService;
import vn.edu.ptit.english.service.RequestReceiptService.Result;

@RestController @RequestMapping("/api/author/materials")
public class AuthorMaterialController {
    private final MaterialService service;
    public AuthorMaterialController(MaterialService service){this.service=service;}
    private ResponseEntity<?> read(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(value);}
    private ResponseEntity<?> result(Result result){var response=ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore().cachePrivate());
        if(result.replayed())response.header("Idempotent-Replayed","true");return result.status()==204?response.build():response.body(result.body());}
    @GetMapping public ResponseEntity<?> list(@RequestParam(defaultValue="OWN") String scope,@RequestParam(defaultValue="") String q,
        @RequestParam(required=false) String status,@RequestParam(required=false) String kind,@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size,@AuthenticationPrincipal UserPrincipal actor){return read(service.list(scope,q,status,kind,page,size,actor));}
    @GetMapping("/{id}") public ResponseEntity<?> get(@PathVariable long id,@AuthenticationPrincipal UserPrincipal actor){return read(service.get(id,actor));}
    @GetMapping("/{id}/versions/{versionId}") public ResponseEntity<?> version(@PathVariable long id,@PathVariable long versionId,@AuthenticationPrincipal UserPrincipal actor){return read(service.version(id,versionId,actor));}
    @PostMapping public ResponseEntity<?> create(@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(service.create(MaterialRequests.input(input,false),actor));}
    @PostMapping("/{id}/draft") public ResponseEntity<?> start(@PathVariable long id,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(service.start(id,MaterialRequests.command(input,false),actor));}
    @PutMapping("/{id}/draft") public ResponseEntity<?> update(@PathVariable long id,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(service.update(id,MaterialRequests.input(input,true),actor));}
    @DeleteMapping("/{id}/draft") public ResponseEntity<?> discard(@PathVariable long id,@RequestParam String requestId,@RequestParam long baseRevision,@RequestParam long draftVersionId,@AuthenticationPrincipal UserPrincipal actor){return result(service.discard(id,MaterialRequests.discard(requestId,baseRevision,draftVersionId),actor));}
    @GetMapping("/{id}/preview") public ResponseEntity<?> preview(@PathVariable long id,@AuthenticationPrincipal UserPrincipal actor){return read(service.preview(id,actor));}
    @PostMapping("/{id}/publish") public ResponseEntity<?> publish(@PathVariable long id,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(service.publish(id,MaterialRequests.command(input,true),actor));}
    @PostMapping("/{id}/retire") public ResponseEntity<?> retire(@PathVariable long id,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(service.retire(id,MaterialRequests.command(input,false),actor));}
}
