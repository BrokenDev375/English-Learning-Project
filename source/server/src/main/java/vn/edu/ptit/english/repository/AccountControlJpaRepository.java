package vn.edu.ptit.english.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import vn.edu.ptit.english.entity.AccountControlEntity;

public interface AccountControlJpaRepository extends JpaRepository<AccountControlEntity, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AccountControlEntity c where c.id = 1")
    AccountControlEntity lockControl();
}
