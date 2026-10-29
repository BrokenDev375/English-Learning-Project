package vn.edu.ptit.english.controller;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import vn.edu.ptit.english.dto.AttemptAudioData;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.AttemptAudioService;
@RestController @RequestMapping("/api/attempts/{id}/audio/{material}")
public class AttemptAudioController {
    private final AttemptAudioService service;
    public AttemptAudioController(AttemptAudioService service){this.service=service;}
    @GetMapping public ResponseEntity<?> read(@PathVariable long id,@PathVariable long material,@AuthenticationPrincipal UserPrincipal actor){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.read(id,material,actor));
    }
    @PostMapping("/start") public ResponseEntity<?> start(@PathVariable long id,@PathVariable long material,@RequestBody JsonNode body,@AuthenticationPrincipal UserPrincipal actor){
        var result=service.start(id,material,AttemptAudioData.start(body),actor);
        var response=ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore().cachePrivate());
        if(result.replayed())response.header("Idempotent-Replayed","true");return response.body(result.body());
    }
}
