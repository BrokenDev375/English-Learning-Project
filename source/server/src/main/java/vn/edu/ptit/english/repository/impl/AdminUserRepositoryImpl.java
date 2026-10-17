package vn.edu.ptit.english.repository.impl;

import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.english.entity.UserEntity;
import vn.edu.ptit.english.enums.AccountStatus;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.repository.*;

@Repository
@Transactional(readOnly = true)
public class AdminUserRepositoryImpl implements AdminUserRepository {
    private final UserJpaRepository users;
    private final AccountControlJpaRepository control;
    public AdminUserRepositoryImpl(UserJpaRepository users, AccountControlJpaRepository control) {
        this.users = users;
        this.control = control;
    }
    @Override @Transactional public void lockControl() { control.lockControl(); }
    @Override @Transactional public Optional<UserEntity> lockById(long id) { return users.lockById(id); }
    @Override public Page<UserEntity> search(String query, Role role, AccountStatus status, int page, int size) {
        String escaped = query.toLowerCase(java.util.Locale.ROOT)
            .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return users.search("%" + escaped + "%", role, status,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
    }
    @Override public long countActiveAdmins() { return users.countByRoleAndStatus(Role.ADMIN, AccountStatus.ACTIVE); }
    @Override @Transactional public UserEntity save(UserEntity user) { return users.saveAndFlush(user); }
}
