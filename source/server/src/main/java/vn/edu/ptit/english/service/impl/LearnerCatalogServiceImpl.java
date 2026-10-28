package vn.edu.ptit.english.service.impl;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.english.dto.AuthoringRequests;
import vn.edu.ptit.english.dto.CatalogViews.*;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.LearnerCatalogService;
import static vn.edu.ptit.english.service.impl.AuthoringAccess.missing;
import static vn.edu.ptit.english.dto.MaterialRequests.invalid;
@Service @Transactional(readOnly=true)
public class LearnerCatalogServiceImpl implements LearnerCatalogService {
    private final ContentRepository content;private final CatalogRepository catalog;private final AccountGuard accounts;
    public LearnerCatalogServiceImpl(ContentRepository content,CatalogRepository catalog,AccountGuard accounts){this.content=content;this.catalog=catalog;this.accounts=accounts;}
    private List<LessonEntity> published(long unit){return content.lessons(unit).stream().filter(l->l.getPublished()!=null&&l.getRetiredAt()==null).toList();}
    private Unit unitView(UnitEntity u){var b=u.view();var ids=published(u.getId()).stream().flatMap(l->l.getPublished().data().objectiveIds().stream()).distinct().sorted().toList();return new Unit(b.id(),b.ownerId(),b.topicId(),b.title(),b.description(),b.stage(),b.sortOrder(),b.roundNumber(),b.assignedTeacherId(),b.revision(),ids);}
    private Map<String,Object> summary(LessonEntity l){var v=l.getPublished();var d=v.data();var m=new LinkedHashMap<String,Object>();m.put("id",l.getId());m.put("unitId",l.getUnitId());m.put("lessonVersionId",v.getId());m.put("title",d.title());m.put("description",d.description());m.put("sortOrder",l.getSortOrder());m.put("objectiveIds",d.objectiveIds());var counts=new LinkedHashMap<String,Long>();counts.put("guided",d.questions().stream().filter(s->s.practiceRole().equals("GUIDED")).count());counts.put("independent",d.questions().stream().filter(s->s.practiceRole().equals("INDEPENDENT")).count());counts.put("reviewVariant",d.questions().stream().filter(s->s.practiceRole().equals("REVIEW_VARIANT")).count());m.put("questionCounts",counts);return m;}
    public Object list(String kind,String q,Long topic,int page,int size,UserPrincipal actor){accounts.requireCurrent(actor);if(q.length()>100||page<0||size<1||size>50)throw invalid("query","Bộ lọc hoặc trang không hợp lệ.");if(topic!=null)AuthoringRequests.id(topic);return switch(kind){case "topics"->CatalogPage.from(content.publicTopics(q,page,size).map(TopicEntity::view));case "objectives"->CatalogPage.from(content.publicObjectives(q,topic,page,size).map(ObjectiveEntity::view));default->CatalogPage.from(content.publicUnits(q,topic,page,size).map(this::unitView));};}
    public Object unit(long id,UserPrincipal actor){accounts.requireCurrent(actor);var unit=catalog.unit(AuthoringRequests.id(id),false).orElseThrow(AuthoringAccess::missing);var lessons=published(id);if(lessons.isEmpty())throw missing();return Map.of("unit",unitView(unit),"lessons",lessons.stream().map(this::summary).toList());}
    public Object lesson(long id,UserPrincipal actor){accounts.requireCurrent(actor);var l=content.lesson(AuthoringRequests.id(id),false).orElseThrow(AuthoringAccess::missing);if(l.getPublished()==null||l.getRetiredAt()!=null)throw missing();var d=l.getPublished().data();return Map.of("lesson",summary(l),"instructions",d.instructions(),"objectives",d.objectiveIds().stream().map(v->catalog.objective(v,false).orElseThrow(AuthoringAccess::missing).view()).toList());}
}
