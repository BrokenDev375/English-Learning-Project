package vn.edu.ptit.english.service.impl;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.service.PublishedContentReader;
import static vn.edu.ptit.english.service.impl.AuthoringAccess.missing;
@Service @Transactional(readOnly=true)
public class PublishedContentReaderImpl implements PublishedContentReader {
    private final ContentRepository content;
    public PublishedContentReaderImpl(ContentRepository content){this.content=content;}
    private PublishedLesson snapshot(LessonEntity root,LessonVersionEntity version){if(!version.getState().equals("PUBLISHED"))throw missing();var d=version.data();return new PublishedLesson(root.getId(),version.getId(),root.getUnitId(),d.title(),d.instructions(),d.objectiveIds(),d.questions(),d.activities());}
    public PublishedLesson activeLesson(long id){var root=content.lesson(id,false).orElseThrow(AuthoringAccess::missing);if(root.getRetiredAt()!=null||root.getPublished()==null)throw missing();return snapshot(root,root.getPublished());}
    public List<PublishedLesson> activeLessons(Long topic){var all=new ArrayList<PublishedLesson>();int page=0;org.springframework.data.domain.Page<LessonEntity> result;do{result=content.lessons(new ContentRepository.Filter("PUBLISHED","",null,topic,null,null,null,null,page++,50),0,false);result.forEach(l->all.add(snapshot(l,l.getPublished())));}while(result.hasNext());return List.copyOf(all);}
    public PublishedLesson lessonVersion(long id){var version=content.lessonVersion(id).orElseThrow(AuthoringAccess::missing);return snapshot(content.lesson(version.getLessonId(),false).orElseThrow(AuthoringAccess::missing),version);}
    public PublishedQuestion questionVersion(long id){var version=content.questionVersion(id).orElseThrow(AuthoringAccess::missing);if(!version.getState().equals("PUBLISHED"))throw missing();var root=content.question(version.getQuestionId(),false).orElseThrow(AuthoringAccess::missing);return new PublishedQuestion(root.getId(),id,root.getOriginKey(),version.data());}
    public PublishedMaterial materialVersion(long id){var version=content.materialVersion(id).orElseThrow(AuthoringAccess::missing);if(!version.getState().equals("PUBLISHED"))throw missing();return new PublishedMaterial(version.getMaterialId(),id,version.data());}
}
