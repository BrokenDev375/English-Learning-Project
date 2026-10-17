package vn.edu.ptit.english.controller;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.repository.ContentRepository.Filter;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.AuthoringService;
import vn.edu.ptit.english.service.RequestReceiptService.Result;
@RestController @RequestMapping("/api/author/{resource:questions|lessons}")
public class AuthorContentController {
    private final AuthoringService service;
    public AuthorContentController(AuthoringService service){this.service=service;}
    private ResponseEntity<?> read(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(value);}
    private ResponseEntity<?> result(Result result){var response=ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore().cachePrivate());if(result.replayed())response.header("Idempotent-Replayed","true");return result.status()==204?response.build():response.body(result.body());}
    @GetMapping public ResponseEntity<?> list(@PathVariable String resource,@RequestParam(defaultValue="OWN") String scope,@RequestParam(defaultValue="") String q,@RequestParam(required=false) String status,@RequestParam(required=false) Long topicId,@RequestParam(required=false) Long objectiveId,@RequestParam(required=false) String type,@RequestParam(required=false) String difficulty,@RequestParam(required=false) Long unitId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@AuthenticationPrincipal UserPrincipal actor){return read(service.list(resource,new Filter(scope,q,status,topicId,objectiveId,type,difficulty,unitId,page,size),actor));}
    @GetMapping("/{id}") public ResponseEntity<?> get(@PathVariable String resource,@PathVariable long id,@AuthenticationPrincipal UserPrincipal actor){return read(service.get(resource,id,actor));}
    @GetMapping("/{id}/versions/{versionId}") public ResponseEntity<?> version(@PathVariable String resource,@PathVariable long id,@PathVariable long versionId,@AuthenticationPrincipal UserPrincipal actor){return read(service.version(resource,id,versionId,actor));}
    @PostMapping public ResponseEntity<?> create(@PathVariable String resource,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(resource.equals("questions")?service.create(AuthoringRequests.question(input,false),actor):service.create(AuthoringRequests.lesson(input,false),actor));}
    @PutMapping("/{id}/draft") public ResponseEntity<?> update(@PathVariable String resource,@PathVariable long id,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(resource.equals("questions")?service.update(id,AuthoringRequests.question(input,true),actor):service.update(id,AuthoringRequests.lesson(input,true),actor));}
    @PostMapping("/{id}/draft") public ResponseEntity<?> start(@PathVariable String resource,@PathVariable long id,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(service.start(resource,id,MaterialRequests.command(input,false),actor));}
    @DeleteMapping("/{id}/draft") public ResponseEntity<?> discard(@PathVariable String resource,@PathVariable long id,@RequestParam String requestId,@RequestParam long baseRevision,@RequestParam long draftVersionId,@AuthenticationPrincipal UserPrincipal actor){return result(service.discard(resource,id,MaterialRequests.discard(requestId,baseRevision,draftVersionId),actor));}
    @GetMapping("/{id}/preview") public ResponseEntity<?> preview(@PathVariable String resource,@PathVariable long id,@AuthenticationPrincipal UserPrincipal actor){return read(service.preview(resource,id,actor));}
    @PostMapping("/{id}/publish") public ResponseEntity<?> publish(@PathVariable String resource,@PathVariable long id,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(service.publish(resource,id,MaterialRequests.command(input,true),actor));}
    @PostMapping("/{id}/retire") public ResponseEntity<?> retire(@PathVariable String resource,@PathVariable long id,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(service.retire(resource,id,MaterialRequests.command(input,false),actor));}
}
