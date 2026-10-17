package vn.edu.ptit.english.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.edu.ptit.english.entity.MaterialVersionEntity;

public interface MaterialVersionJpaRepository extends JpaRepository<MaterialVersionEntity,Long> {
    Optional<MaterialVersionEntity> findByIdAndMaterialId(long id,long materialId);
    @Query("select count(v) from MaterialVersionEntity v,MaterialEntity m where v.materialId=m.id and v.assetId=:asset and (m.ownerId=:actor or (m.published.id=v.id and m.retiredAt is null))")
    long countReadableAsset(@Param("asset") long assetId,@Param("actor") long actorId);
    @Query("select count(q) from QuestionVersionReferenceEntity q where q.materialVersionId=:version")
    long countReferences(@Param("version") long versionId);
    @Query("select count(q) from QuestionRootReferenceEntity r,QuestionVersionReferenceEntity q,MaterialVersionEntity v where r.publishedVersionId=q.id and r.retiredAt is null and q.materialVersionId=v.id and v.materialId=:root")
    long countPublishedQuestionUsage(@Param("root") long materialId);
    @Query("select count(q) from LessonReferenceEntity l,LessonQuestionReferenceEntity slot,QuestionVersionReferenceEntity q,MaterialVersionEntity v where l.publishedVersionId=slot.lessonVersionId and l.retiredAt is null and slot.questionVersionId=q.id and q.materialVersionId=v.id and v.materialId=:root")
    long countPublishedLessonUsage(@Param("root") long materialId);
}
