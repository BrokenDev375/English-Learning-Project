package vn.edu.ptit.english.service.impl;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.edu.ptit.english.dto.AuthoringData.*;
import vn.edu.ptit.english.dto.MaterialRequests.Command;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.security.UserPrincipal;
import static vn.edu.ptit.english.service.impl.AuthoringAccess.*;

/** Publication closure locks Material -> Question -> Lesson; all dependency writes share the caller's transaction. */
@Component
public class AuthoringPublication {
    private final ContentRepository content;private final MaterialRepository materials;private final CatalogRepository catalog;
    private final QuestionValidator validator;private final MaterialServiceImpl materialService;
    public AuthoringPublication(ContentRepository content,MaterialRepository materials,CatalogRepository catalog,QuestionValidator validator,MaterialServiceImpl materialService){this.content=content;this.materials=materials;this.catalog=catalog;this.validator=validator;this.materialService=materialService;}
    private record Closure(String kind,CatalogEntity root,AuthoredVersion draft,Map<Long,QuestionVersionEntity> questions,Map<Long,MaterialVersionEntity> materials) {}
    private Closure closure(String kind,long id,UserPrincipal actor){
        var questions=new TreeMap<Long,QuestionVersionEntity>();AuthoredVersion draft;CatalogEntity root;
        if(kind.equals("questions")){var q=content.question(id,false).orElseThrow(AuthoringAccess::missing);root=q;draft=q.getDraft();if(draft!=null)questions.put(draft.getId(),q.getDraft());}
        else{var l=content.lesson(id,false).orElseThrow(AuthoringAccess::missing);root=l;draft=l.getDraft();if(draft!=null)for(var slot:l.getDraft().data().questions()){var q=content.questionVersion(slot.questionVersionId()).orElseThrow(AuthoringAccess::missing);questions.put(q.getId(),q);}}
        editable(root,actor);if(draft==null)throw conflict("DRAFT_MISMATCH","Nội dung không còn bản nháp.");
        var dependencies=new TreeMap<Long,MaterialVersionEntity>();for(var q:questions.values()){checkQuestion(q,actor);Long material=q.data().materialVersionId();if(material!=null){var m=content.materialVersion(material).orElseThrow(AuthoringAccess::missing);checkMaterial(m,actor);dependencies.put(m.getId(),m);}}
        return new Closure(kind,root,draft,questions,dependencies);
    }
    private void checkQuestion(QuestionVersionEntity v,UserPrincipal actor){var root=content.question(v.getQuestionId(),false).orElseThrow(AuthoringAccess::missing);if(!own(root,actor)&&(!v.getState().equals("PUBLISHED")||root.getRetiredAt()!=null||root.getPublished()==null))throw missing();if(v.getState().equals("DRAFT")&&(root.getDraft()==null||!root.getDraft().getId().equals(v.getId())))throw missing();}
    private void checkMaterial(MaterialVersionEntity v,UserPrincipal actor){var root=materials.find(v.getMaterialId(),false).orElseThrow(AuthoringAccess::missing);if(!own(root,actor)&&(!v.getState().equals("PUBLISHED")||root.getRetiredAt()!=null||root.getPublished()==null))throw missing();if(v.getState().equals("DRAFT")&&(root.getDraft()==null||!root.getDraft().getId().equals(v.getId())))throw missing();}
    public void validateQuestionReferences(Question data,UserPrincipal actor){
        catalog.topic(data.topicId(),false).orElseThrow(AuthoringAccess::missing);var objective=catalog.objective(data.objectiveId(),false).orElseThrow(AuthoringAccess::missing);if(objective.view().topicId()!=data.topicId())throw vn.edu.ptit.english.dto.MaterialRequests.invalid("objectiveId","Mục tiêu phải thuộc chủ đề đã chọn.");if(data.materialVersionId()!=null)checkMaterial(content.materialVersion(data.materialVersionId()).orElseThrow(AuthoringAccess::missing),actor);
    }
    public void validateLessonReferences(Lesson data,long unitId,UserPrincipal actor){var unit=catalog.unit(unitId,false).orElseThrow(AuthoringAccess::missing);for(long id:data.objectiveIds()){var o=catalog.objective(id,false).orElseThrow(AuthoringAccess::missing);if(o.view().topicId()!=unit.getTopicId())throw vn.edu.ptit.english.dto.MaterialRequests.invalid("objectiveIds","Mục tiêu bài học phải thuộc chủ đề của Unit.");}for(var s:data.questions())checkQuestion(content.questionVersion(s.questionVersionId()).orElseThrow(AuthoringAccess::missing),actor);}
    private String token(Closure c){var text=new StringBuilder("v1|").append(c.kind.equals("questions")?"QUESTION":"LESSON").append('|').append(c.root.getId()).append('|').append(c.draft.getId()).append('|').append(c.draft.getRevision());if(!c.kind.equals("questions"))c.questions.forEach((id,v)->text.append("\nQ|").append(id).append('|').append(v.getRevision()));c.materials.forEach((id,v)->text.append("\nM|").append(id).append('|').append(v.getRevision()));try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private List<Issue> errors(Closure c,UserPrincipal actor){
        var errors=new ArrayList<Issue>();for(var m:c.materials.values()){var root=materials.find(m.getMaterialId(),false).orElseThrow(AuthoringAccess::missing);if(root.getRetiredAt()!=null)errors.add(new Issue("materials["+m.getId()+"]","CONTENT_RETIRED","Tài liệu đã ngừng cấp mới."));for(var issue:materialService.errors(m.data(),actor))errors.add(new Issue("materials["+m.getId()+"]."+issue.path(),issue.code(),issue.message()));}
        for(var q:c.questions.values()){validateQuestionReferences(q.data(),actor);var root=content.question(q.getQuestionId(),false).orElseThrow(AuthoringAccess::missing);if(root.getRetiredAt()!=null&&!(c.kind.equals("questions")&&root.getId().equals(c.root.getId())))errors.add(new Issue("questions["+q.getId()+"]","CONTENT_RETIRED","Câu hỏi đã ngừng cấp mới."));String prefix=c.kind.equals("questions")?"":"questions["+q.getId()+"].";for(var issue:validator.errors(q.data()))errors.add(new Issue(prefix+issue.path(),issue.code(),issue.message()));}
        if(c.draft instanceof LessonVersionEntity version){var lesson=version.data();var root=(LessonEntity)c.root;var unit=catalog.unit(root.getUnitId(),false).orElseThrow(AuthoringAccess::missing);validateLessonReferences(lesson,root.getUnitId(),actor);
            if(lesson.title().isBlank())errors.add(new Issue("title","REQUIRED","Cần nhập tên bài học."));if(lesson.instructions().isEmpty()||java.util.stream.StreamSupport.stream(lesson.instructions().spliterator(),false).allMatch(i->i.path("text").asText().isBlank()))errors.add(new Issue("instructions","REQUIRED","Cần ít nhất một khối hướng dẫn có nội dung."));if(lesson.objectiveIds().isEmpty())errors.add(new Issue("objectiveIds","REQUIRED","Chọn ít nhất một mục tiêu cho bài học."));
            var origins=new HashSet<String>();for(var slot:lesson.questions()){var q=c.questions.get(slot.questionVersionId());var qr=content.question(q.getQuestionId(),false).orElseThrow(AuthoringAccess::missing);if(!origins.add(qr.getOriginKey()))errors.add(new Issue("questions","DUPLICATE_ORIGIN","Một câu gốc chỉ được xuất hiện một lần trong bài, kể cả bản sao/phiên bản khác."));if(q.data().topicId()!=unit.getTopicId()||!lesson.objectiveIds().contains(q.data().objectiveId()))errors.add(new Issue("questions","TOPIC_OBJECTIVE_MISMATCH","Câu hỏi phải cùng chủ đề Unit và thuộc mục tiêu của bài."));if(slot.practiceRole().equals("GUIDED")&&(q.data().hint()==null||q.data().hint().isBlank()))errors.add(new Issue("questions["+q.getId()+"].hint","REQUIRED","Câu hướng dẫn cần có gợi ý."));}
            for(String role:List.of("GUIDED","INDEPENDENT","REVIEW_VARIANT")){var positions=lesson.questions().stream().filter(s->s.practiceRole().equals(role)).map(Slot::position).sorted().toList();if(positions.isEmpty())errors.add(new Issue("questions","MISSING_GROUP","Cần ít nhất một câu trong nhóm "+role+"."));for(int i=0;i<positions.size();i++)if(positions.get(i)!=i){errors.add(new Issue("questions","INVALID_ORDER","Thứ tự câu trong mỗi nhóm phải liên tiếp từ 0."));break;}}
        }
        return errors.stream().limit(100).toList();
    }
    public Preview preview(String kind,long id,UserPrincipal actor){var c=closure(kind,id,actor);Object data=c.draft instanceof QuestionVersionEntity q?q.view():Map.of("lesson",((LessonVersionEntity)c.draft).view(),"questions",c.questions.values().stream().map(QuestionVersionEntity::view).toList(),"materials",c.materials.values().stream().map(MaterialVersionEntity::view).toList());return new Preview(id,c.draft.getId(),c.draft.getRevision(),token(c),errors(c,actor),List.of(),data);}
    public void publish(String kind,long id,Command command,UserPrincipal actor){
        var before=closure(kind,id,actor);var lockedMaterials=new TreeSet<Long>();var lockedQuestions=new TreeSet<Long>();before.materials.values().forEach(v->lockedMaterials.add(v.getMaterialId()));before.questions.values().forEach(v->lockedQuestions.add(v.getQuestionId()));
        for(long material:lockedMaterials)content.lockMaterial(material);
        for(long question:lockedQuestions)content.question(question,true).orElseThrow(AuthoringAccess::missing);
        if(kind.equals("lessons"))content.lesson(id,true).orElseThrow(AuthoringAccess::missing);
        // Refresh cached version/collection state after root locks, including rows read during discovery.
        before.materials.values().forEach(content::refresh);before.questions.values().forEach(content::refresh);content.refresh(before.draft);
        var c=closure(kind,id,actor);boolean changed=c.materials.values().stream().anyMatch(v->!lockedMaterials.contains(v.getMaterialId()))||c.questions.values().stream().anyMatch(v->!lockedQuestions.contains(v.getQuestionId()));
        if(changed||!c.draft.getId().equals(command.draftVersionId())||!token(c).equals(command.previewToken()))throw conflict("PREVIEW_STALE","Bài/câu hoặc tài liệu đã đổi sau lần xem trước. Hãy xem trước lại.");revision(c.draft.getRevision(),command.baseRevision());var errors=errors(c,actor);if(!errors.isEmpty()){var fields=new LinkedHashMap<String,String>();errors.forEach(e->fields.put(e.path(),e.message()));throw new ApiException(HttpStatus.BAD_REQUEST,"PUBLISH_VALIDATION_FAILED","Nội dung chưa đủ điều kiện xuất bản.",fields);}
        for(var m:c.materials.values())if(m.getState().equals("DRAFT")){var root=materials.find(m.getMaterialId(),false).orElseThrow(AuthoringAccess::missing);editable(root,actor);root.publish();materials.save(m);materials.save(root);}
        for(var q:c.questions.values())if(q.getState().equals("DRAFT")){var root=content.question(q.getQuestionId(),false).orElseThrow(AuthoringAccess::missing);editable(root,actor);root.publish();content.save(q);content.save(root);}
        if(c.root instanceof LessonEntity root){root.publish();content.save((LessonVersionEntity)c.draft);content.save(root);}
    }
}
