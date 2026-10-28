package vn.edu.ptit.english.repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import vn.edu.ptit.english.entity.ContentControlEntity;
public interface ContentControlJpaRepository extends JpaRepository<ContentControlEntity,Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from ContentControlEntity c where c.id=1")
    ContentControlEntity lockContent();
}
