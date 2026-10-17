package vn.edu.ptit.english.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.english.dto.ClassroomRequests;
import vn.edu.ptit.english.dto.ClassroomView;
import vn.edu.ptit.english.dto.MemberView;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.ClassroomService;

@RestController
@RequestMapping("/api/classes")
public class ClassroomController {
    private final ClassroomService service;
    public ClassroomController(ClassroomService service) { this.service = service; }
    @GetMapping public List<ClassroomView> list(@AuthenticationPrincipal UserPrincipal user) { return service.list(user); }
    @PostMapping public ResponseEntity<ClassroomView> create(@Valid @RequestBody ClassroomRequests.Create input, @AuthenticationPrincipal UserPrincipal user) {
        var result = service.create(input, user);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.classroom());
    }
    @GetMapping("/{id}") public ClassroomView get(@PathVariable long id, @AuthenticationPrincipal UserPrincipal user) { return service.get(id, user); }
    @PatchMapping("/{id}") public ClassroomView edit(@PathVariable long id, @Valid @RequestBody ClassroomRequests.Edit input, @AuthenticationPrincipal UserPrincipal user) { return service.edit(id, input, user); }
    @PostMapping("/join") public ClassroomService.Joined join(@Valid @RequestBody ClassroomRequests.Join input, @AuthenticationPrincipal UserPrincipal user) { return service.join(input, user); }
    @GetMapping("/{id}/members") public List<MemberView> members(@PathVariable long id, @AuthenticationPrincipal UserPrincipal user) { return service.members(id, user); }
    @DeleteMapping("/{id}/members/{studentId}") public ResponseEntity<Void> remove(@PathVariable long id, @PathVariable long studentId, @AuthenticationPrincipal UserPrincipal user) {
        service.remove(id, studentId, user); return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/{id}/membership") public ResponseEntity<Void> leave(@PathVariable long id, @AuthenticationPrincipal UserPrincipal user) {
        service.leave(id, user); return ResponseEntity.noContent().build();
    }
}
