package vn.edu.ptit.english.repository;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import vn.edu.ptit.english.entity.*;
public interface QuestionExposureJpaRepository extends JpaRepository<QuestionExposureEntity,QuestionExposureKey> {
    List<QuestionExposureEntity> findByIdUserIdAndIdOriginKeyIn(long userId,Collection<String> origins);
}
