package vn.edu.ptit.english.controller;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.LearningProgressService;

@RestController @RequestMapping("/api/me/learning-progress")
public class LearningProgressController {

    private final LearningProgressService service;

    public LearningProgressController(LearningProgressService service){
        this.service=service;
    }

    @GetMapping public ResponseEntity<?> list(
                            @RequestParam(required=false) Long unitId,
                            @RequestParam(defaultValue="0") int page,
                            @RequestParam(defaultValue="20") int size,
                            @AuthenticationPrincipal UserPrincipal actor) {

        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.list(unitId,page,size,actor));
    }
}
