package vn.edu.ptit.english.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.english.dto.AudioUpdateData;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.AudioUpdateService;

@RestController
@RequestMapping("/api/author/materials/{id}/audio-update")
public class AudioUpdateController {
    private final AudioUpdateService service;
    public AudioUpdateController(AudioUpdateService service){this.service=service;}
    @GetMapping
    public ResponseEntity<?> preview(@PathVariable long id,@AuthenticationPrincipal UserPrincipal actor){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.preview(id,actor));
    }
    @PostMapping
    public ResponseEntity<?> apply(@PathVariable long id,@RequestBody JsonNode input,@AuthenticationPrincipal UserPrincipal actor){
        var result=service.apply(id,AudioUpdateData.request(input),actor);
        var response=ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore().cachePrivate());
        if(result.replayed())response.header("Idempotent-Replayed","true");
        return response.body(result.body());
    }
}
