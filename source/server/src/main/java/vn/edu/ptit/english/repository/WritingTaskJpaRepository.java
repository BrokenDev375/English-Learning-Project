package vn.edu.ptit.english.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.ptit.english.entity.WritingTaskEntity;
public interface WritingTaskJpaRepository extends JpaRepository<WritingTaskEntity,Long> {}
