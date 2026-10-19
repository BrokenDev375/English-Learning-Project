package vn.edu.ptit.english.repository;
import java.util.*;
import org.springframework.data.domain.Page;
import vn.edu.ptit.english.entity.*;
public interface ContentRepository {
    record Filter(String scope,String q,String status,Long topicId,Long objectiveId,String type,String difficulty,Long unitId,int page,int size) {}
    Optional<QuestionEntity> question(long id,boolean lock);
    Optional<LessonEntity> lesson(long id,boolean lock);
    Optional<QuestionVersionEntity> questionVersion(long id);
    Optional<LessonVersionEntity> lessonVersion(long id);
    Optional<MaterialVersionEntity> materialVersion(long id);
    MaterialEntity lockMaterial(long id);
    QuestionEntity save(QuestionEntity v); QuestionVersionEntity save(QuestionVersionEntity v);
    LessonEntity save(LessonEntity v); LessonVersionEntity save(LessonVersionEntity v);
    void delete(QuestionVersionEntity v); void delete(LessonVersionEntity v);
    void refresh(Object value);
    Page<QuestionEntity> questions(Filter filter,long actor,boolean admin);
    Page<LessonEntity> lessons(Filter filter,long actor,boolean admin);
    List<LessonEntity> lessons(long unit);
    List<QuestionEntity> questionsUsingMaterial(long materialId);
    List<LessonEntity> lessonsUsingMaterial(long materialId,long targetVersionId);
    boolean questionReferenced(long version); boolean questionInUse(long root);
    Page<TopicEntity> publicTopics(String q,int page,int size);
    Page<ObjectiveEntity> publicObjectives(String q,Long topic,int page,int size);
    Page<UnitEntity> publicUnits(String q,Long topic,int page,int size);
}
