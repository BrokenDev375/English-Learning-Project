package vn.edu.ptit.english.controller;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.english.dto.WritingData;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.WritingService;
import vn.edu.ptit.english.service.RequestReceiptService.Result;
@RestController @RequestMapping("/api")
public class WritingController {
    private final WritingService writing;
    public WritingController(WritingService writing){this.writing=writing;}
    private ResponseEntity<?> read(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(value);}
    private ResponseEntity<?> result(Result value){var response=ResponseEntity.status(value.status()).cacheControl(CacheControl.noStore().cachePrivate());if(value.replayed())response.header("Idempotent-Replayed","true");return response.body(value.body());}
    @GetMapping("/author/units/{unit}/writing") public ResponseEntity<?> task(@PathVariable long unit,@AuthenticationPrincipal UserPrincipal actor){return read(writing.task(unit,actor));}
    @PutMapping("/author/units/{unit}/writing/draft") public ResponseEntity<?> configure(@PathVariable long unit,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(writing.configure(unit,"definition",WritingData.parse(input,"definition"),actor));}
    @PostMapping("/author/units/{unit}/writing/{action:publish|retire}") public ResponseEntity<?> publish(@PathVariable long unit,@PathVariable String action,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(writing.configure(unit,action,WritingData.parse(input,action),actor));}
    @GetMapping("/me/units/{unit}/writing") public ResponseEntity<?> learner(@PathVariable long unit,@AuthenticationPrincipal UserPrincipal actor){return read(writing.learner(unit,actor));}
    @PostMapping("/me/units/{unit}/writing/{action:start|draft|submit}") public ResponseEntity<?> mutate(@PathVariable long unit,@PathVariable String action,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(writing.mutate(unit,action,WritingData.parse(input,action),actor));}
    @GetMapping("/author/writing-submissions") public ResponseEntity<?> queue(@RequestParam long unitId,@RequestParam(defaultValue="PENDING") String state,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@AuthenticationPrincipal UserPrincipal actor){return read(writing.queue(unitId,state,page,size,actor));}
    @GetMapping("/author/writing-submissions/{id}") public ResponseEntity<?> grading(@PathVariable long id,@AuthenticationPrincipal UserPrincipal actor){return read(writing.grading(id,actor));}
    @PostMapping("/author/writing-submissions/{id}/grade") public ResponseEntity<?> grade(@PathVariable long id,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(writing.grade(id,WritingData.parse(input,"grade"),actor));}
}
