package vn.edu.ptit.english.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import vn.edu.ptit.english.entity.UserEntity;
import vn.edu.ptit.english.enums.AccountStatus;
import vn.edu.ptit.english.enums.Role;

public interface AdminUserRepository {
    void lockControl();
    Optional<UserEntity> lockById(long id);
    Page<UserEntity> search(String query, Role role, AccountStatus status, int page, int size);
    long countActiveAdmins();
    UserEntity save(UserEntity user);
}
