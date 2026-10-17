package vn.edu.ptit.english.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.repository.UserRepository;
import vn.edu.ptit.english.security.AuthValidation;

@Component
@Order(0)
@ConditionalOnProperty(name = "app.bootstrap-admin.enabled", havingValue = "true")
public class AdminInitializer implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String email, password;
    public AdminInitializer(UserRepository users, PasswordEncoder encoder,
        @Value("${app.bootstrap-admin.email}") String email,
        @Value("${app.bootstrap-admin.password}") String password) {
        this.users = users; this.encoder = encoder; this.email = email; this.password = password;
    }
    @Override public void run(ApplicationArguments args) {
        String normalized = AuthValidation.normalizeEmail(email);
        if (!normalized.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new IllegalStateException("Set a valid bootstrap admin email.");
        var existing = users.findByEmail(normalized);
        if (existing.isPresent()) {
            if (existing.get().role() != Role.ADMIN) throw new IllegalStateException("Bootstrap email is already used by a non-admin account.");
            return; // Never change an existing password, role, or account status at startup.
        }
        if (password == null || password.length() < 12) throw new IllegalStateException("Set a bootstrap admin password of at least 12 characters.");
        AuthValidation.checkPasswordLength(password);
        users.create(normalized, encoder.encode(password), "Quản trị viên", Role.ADMIN);
    }
}
