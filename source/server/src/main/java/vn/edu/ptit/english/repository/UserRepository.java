package vn.edu.ptit.english.repository;

import java.util.Optional;
import vn.edu.ptit.english.dto.UserRecord;
import vn.edu.ptit.english.enums.Role;

/** Contract injected by callers; implementation is in repository.impl. */
public interface UserRepository {
    Optional<UserRecord> findByEmail(String email);
    Optional<UserRecord> findById(long id);
    UserRecord create(String email, String passwordHash, String name, Role role);
}
