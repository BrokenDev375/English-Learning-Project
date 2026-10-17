package vn.edu.ptit.english.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.edu.ptit.english.dto.UserRecord;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.UserJpaRepository;
import vn.edu.ptit.english.repository.UserRepository;

@Component
public class AccountGuard {
    private final UserRepository users;
    private final UserJpaRepository entities;
    public AccountGuard(UserRepository users, UserJpaRepository entities) {
        this.users = users;
        this.entities = entities;
    }
    public UserRecord requireCurrent(UserPrincipal actor) {
        var current = users.findById(actor.id()).orElseThrow(AccountGuard::revoked);
        if (!actor.matches(current)) throw revoked();
        return current;
    }
    // Call inside a write transaction, before taking other resource locks.
    // Account changes wait for this transaction; old in-flight writes cannot commit after a lock returns.
    public UserRecord lockCurrent(UserPrincipal actor) {
        var current = entities.lockById(actor.id()).orElseThrow(AccountGuard::revoked).record();
        if (!actor.matches(current)) throw revoked();
        return current;
    }
    public static ApiException revoked() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_REVOKED",
            "Phiên đăng nhập đã bị thu hồi. Vui lòng đăng nhập lại.");
    }
}
