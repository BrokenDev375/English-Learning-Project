package vn.edu.ptit.english.security;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import vn.edu.ptit.english.exception.ApiException;

public final class AuthValidation {
    private AuthValidation() {}
    public static String normalizeEmail(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    public static void checkPasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG", "Mật khẩu tối đa 72 byte UTF-8. Vui lòng dùng mật khẩu ngắn hơn.");
        }
    }
}
