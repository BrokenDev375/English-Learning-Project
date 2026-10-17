package vn.edu.ptit.english.bootstrap;

import vn.edu.ptit.english.security.AuthValidation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.repository.UserRepository;

@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoInitializer implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String teacherEmail, teacherPassword, studentEmail, studentPassword;
    public DemoInitializer(UserRepository users, PasswordEncoder passwords,
        @Value("${app.demo.teacher-email}") String teacherEmail, @Value("${app.demo.teacher-password}") String teacherPassword,
        @Value("${app.demo.student-email}") String studentEmail, @Value("${app.demo.student-password}") String studentPassword) {
        this.users = users; this.passwords = passwords;
        this.teacherEmail = teacherEmail; this.teacherPassword = teacherPassword;
        this.studentEmail = studentEmail; this.studentPassword = studentPassword;
    }
    @Override public void run(ApplicationArguments args) {
        create(teacherEmail, teacherPassword, "Nguyễn Minh Anh", Role.TEACHER);
        create(studentEmail, studentPassword, "Trần Hoàng Nam", Role.STUDENT);
    }
    private void create(String email, String password, String name, Role role) {
        if (password == null || password.length() < 8) throw new IllegalStateException("Set a demo password of at least 8 characters.");
        AuthValidation.checkPasswordLength(password);
        var existing = users.findByEmail(AuthValidation.normalizeEmail(email));
        if (existing.isPresent()) {
            if (existing.get().role() != role) throw new IllegalStateException("Demo account exists with a different role.");
            return;
        }
        users.create(AuthValidation.normalizeEmail(email), passwords.encode(password), name, role);
    }
}
