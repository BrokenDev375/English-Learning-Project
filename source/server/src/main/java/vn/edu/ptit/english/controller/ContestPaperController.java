package vn.edu.ptit.english.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.english.dto.ContestPaperData;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.*;

@RestController @RequestMapping("/api/author")
public class ContestPaperController {
    private final ContestPaperService papers;
    public ContestPaperController(ContestPaperService papers){this.papers=papers;}
    private ResponseEntity<?> read(Object v){return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(v);}
    private ResponseEntity<?> result(RequestReceiptService.Result v){var response=ResponseEntity.status(v.status()).cacheControl(CacheControl.noStore().cachePrivate());if(v.replayed())response.header("Idempotent-Replayed","true");return response.body(v.body());}
    @GetMapping("/contest-permissions/mine") public ResponseEntity<?> mine(@AuthenticationPrincipal UserPrincipal actor){return read(papers.capabilities(actor));}
    @GetMapping("/contest-permissions") public ResponseEntity<?> permissions(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="false") boolean proctorOnly,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@AuthenticationPrincipal UserPrincipal actor){return read(papers.permissions(q,proctorOnly,page,size,actor));}
    @PutMapping("/contest-permissions/{user}") public ResponseEntity<?> grant(@PathVariable long user,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(papers.grant(user,ContestPaperData.parse(input,"permissions"),actor));}
    @GetMapping("/contest-papers") public ResponseEntity<?> list(@RequestParam(defaultValue="ALL") String state,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@AuthenticationPrincipal UserPrincipal actor){return read(papers.list(state,q,page,size,actor));}
    @GetMapping("/contest-papers/{id}") public ResponseEntity<?> detail(@PathVariable long id,@AuthenticationPrincipal UserPrincipal actor){return read(papers.detail(id,actor));}
    @PostMapping("/contest-papers") public ResponseEntity<?> create(@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(papers.create(ContestPaperData.parse(input,"create"),actor));}
    @PutMapping("/contest-papers/{id}/draft") public ResponseEntity<?> edit(@PathVariable long id,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(papers.change(id,"draft",ContestPaperData.parse(input,"draft"),actor));}
    @PostMapping("/contest-papers/{id}/{action:submit|approve|return|archive|clone}") public ResponseEntity<?> change(@PathVariable long id,@PathVariable String action,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){return result(papers.change(id,action,ContestPaperData.parse(input,action),actor));}
}
