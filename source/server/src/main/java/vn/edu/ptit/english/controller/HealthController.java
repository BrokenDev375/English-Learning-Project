package vn.edu.ptit.english.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.english.repository.UserJpaRepository;

@RestController
public class HealthController {
    private final UserJpaRepository users;
    public HealthController(UserJpaRepository users) { this.users = users; }
    @GetMapping("/api/health")
    public Map<String, String> health() {
        users.count();
        return Map.of("status", "UP");
    }
}
