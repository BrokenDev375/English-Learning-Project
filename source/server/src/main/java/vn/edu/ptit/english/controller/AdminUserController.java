package vn.edu.ptit.english.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.enums.*;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.AdminUserService;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserService service;
    public AdminUserController(AdminUserService service) { this.service = service; }
    @GetMapping
    public UserPage list(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) Role role,
                         @RequestParam(required = false) AccountStatus status,
                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                         @AuthenticationPrincipal UserPrincipal actor) {
        return service.search(q, role, status, page, size, actor);
    }
    @PatchMapping("/{id}/role")
    public UserView role(@PathVariable long id, @Valid @RequestBody AdminRequests.ChangeRole input,
                         @AuthenticationPrincipal UserPrincipal actor) {
        return service.changeRole(id, input.role(), actor);
    }
    @PatchMapping("/{id}/status")
    public UserView status(@PathVariable long id, @Valid @RequestBody AdminRequests.ChangeStatus input,
                           @AuthenticationPrincipal UserPrincipal actor) {
        return service.changeStatus(id, input.status(), actor);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, @AuthenticationPrincipal UserPrincipal actor) {
        service.delete(id, actor);
        return ResponseEntity.noContent().build();
    }
}
