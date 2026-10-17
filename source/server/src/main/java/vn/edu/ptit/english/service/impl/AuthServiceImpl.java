package vn.edu.ptit.english.service.impl;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.edu.ptit.english.dto.AuthRequests;
import vn.edu.ptit.english.dto.UserView;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.UserRepository;
import vn.edu.ptit.english.security.AuthValidation;
import vn.edu.ptit.english.service.AuthService;

@Service
public class AuthServiceImpl implements AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    public AuthServiceImpl(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Override
    public UserView register(AuthRequests.Register input) {
        String name = input.fullName().strip();
        if (name.length() < 2) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NAME", "Họ tên cần ít nhất 2 ký tự.");
        AuthValidation.checkPasswordLength(input.password());
        try {
            return users.create(AuthValidation.normalizeEmail(input.email()), passwords.encode(input.password()), name, Role.STUDENT).view();
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "Email đã được đăng ký. Bạn có thể đăng nhập bằng tài khoản này.");
        }
    }
    @Override
    public UserView current(long id) {
        var user = users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Vui lòng đăng nhập lại."));
        if (user.status() != vn.edu.ptit.english.enums.AccountStatus.ACTIVE) throw vn.edu.ptit.english.security.AccountGuard.revoked();
        return user.view();
    }
}

