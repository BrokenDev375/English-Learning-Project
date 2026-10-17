package vn.edu.ptit.english.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.edu.ptit.english.entity.UserEntity;
import vn.edu.ptit.english.enums.AccountStatus;
import vn.edu.ptit.english.enums.Role;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> lockById(@Param("id") long id);
    long countByRoleAndStatus(Role role, AccountStatus status);
    @Query("""
        select u from UserEntity u
        where (lower(u.email) like :query escape '!' or lower(u.fullName) like :query escape '!')
        and (:role is null or u.role = :role) and (:status is null or u.status = :status)
        """)
    Page<UserEntity> search(@Param("query") String query, @Param("role") Role role,
                           @Param("status") AccountStatus status, Pageable pageable);
}
