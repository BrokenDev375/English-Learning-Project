package vn.edu.ptit.english.repository;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.edu.ptit.english.entity.ContentReviewEntity;
public interface ContentReviewJpaRepository extends JpaRepository<ContentReviewEntity,Long>,JpaSpecificationExecutor<ContentReviewEntity> {
    @Query("select r from ContentReviewEntity r where r.kind=:kind and (r.questionId=:id or r.lessonId=:id) order by r.id desc")
    List<ContentReviewEntity> history(@Param("kind") String kind,@Param("id") long id,org.springframework.data.domain.Pageable page);
    @Query("select count(r) from ContentReviewEntity r where r.kind=:kind and (r.questionId=:id or r.lessonId=:id) and r.state='PENDING'")
    long pending(@Param("kind") String kind,@Param("id") long id);
    List<ContentReviewEntity> findByKindAndState(String kind,String state);
}
