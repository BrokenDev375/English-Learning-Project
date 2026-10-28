package vn.edu.ptit.english.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.ptit.english.dto.AudioUpdateData;
import vn.edu.ptit.english.dto.AuthoringData.*;
import vn.edu.ptit.english.dto.AuthoringRequests;
import vn.edu.ptit.english.dto.MaterialRequests.Command;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.*;
import static vn.edu.ptit.english.service.impl.AuthoringAccess.*;
import static vn.edu.ptit.english.dto.MaterialRequests.invalid;

@Service
@Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
public class AudioUpdateServiceImpl implements AudioUpdateService {
    private final ContentRepository content;
    private final ContentGovernance governance;
    private final MaterialRepository materials;
    private final AccountGuard accounts;
    private final RequestReceiptService receipts;
    private final AuthoringPublication publication;
    public AudioUpdateServiceImpl(ContentRepository content,MaterialRepository materials,AccountGuard accounts,
        RequestReceiptService receipts,AuthoringPublication publication,ContentGovernance governance){this.governance=governance;
        this.content=content;this.materials=materials;this.accounts=accounts;this.receipts=receipts;this.publication=publication;
    }
    private record Plan(MaterialEntity material,MaterialVersionEntity target,AudioUpdateData.Preview view,
        Map<Long,QuestionEntity> questions,Map<Long,LessonEntity> lessons) {}
    private void author(UserPrincipal actor){accounts.requireCurrent(actor);AssetServiceImpl.author(actor);}
    private Question withAudio(Question data,long target){
        return new Question(data.type(),data.topicId(),data.objectiveId(),data.difficulty(),data.prompt(),
            data.content(),data.answerKey(),data.explanation(),data.hint(),target,data.source());
    }
    private boolean needsUpdate(QuestionVersionEntity question,long material,long target){
        Long id=question.data().materialVersionId();
        if(id==null||id==target)return false;
        var version=content.materialVersion(id).orElseThrow(AuthoringAccess::missing);
        return version.getMaterialId()==material&&version.data().kind().equals("AUDIO");
    }
    private String questionReason(QuestionEntity root,UserPrincipal actor){
        if(!own(root,actor))return "Câu hỏi thuộc người biên soạn khác.";
        if(root.getRetiredAt()!=null||root.getPublished()==null)return "Câu hỏi đã ngừng cấp.";
        if(root.getDraft()!=null)return "Câu hỏi đang có bản nháp; hoàn tất hoặc bỏ nháp trước.";
        return null;
    }
    private Plan plan(long materialId,UserPrincipal actor){
        AuthoringRequests.id(materialId);
        var material=materials.find(materialId,false).orElseThrow(AuthoringAccess::missing);
        if(!own(material,actor)&&(material.getPublished()==null||material.getRetiredAt()!=null))throw missing();
        if(material.getPublished()==null||material.getRetiredAt()!=null)
            throw conflict("CONTENT_NOT_PUBLISHED","Cần tài liệu audio đang được xuất bản.");
        var target=material.getPublished();
        if(!target.data().kind().equals("AUDIO"))throw invalid("materialId","Chức năng này dành cho tài liệu audio.");

        var roots=new TreeMap<Long,QuestionEntity>();
        content.questionsUsingMaterial(materialId).forEach(q->roots.put(q.getId(),q));
        var lessons=new TreeMap<Long,LessonEntity>();
        content.lessonsUsingMaterial(materialId,target.getId()).forEach(l->lessons.put(l.getId(),l));
        var questionRows=new ArrayList<AudioUpdateData.QuestionRow>();
        int foreignQuestions=0,foreignLessons=0;
        for(var root:roots.values()){
            if(!needsUpdate(root.getPublished(),materialId,target.getId()))continue;
            if(!own(root,actor)){foreignQuestions++;continue;}
            questionRows.add(new AudioUpdateData.QuestionRow(root.getId(),root.getPublished().data().prompt(),
                root.getPublished().getId(),questionReason(root,actor)));
        }
        var lessonRows=new ArrayList<AudioUpdateData.LessonRow>();
        for(var lesson:lessons.values()){
            if(!own(lesson,actor)){foreignLessons++;continue;}
            var required=new TreeSet<Long>();
            String reason=lesson.getDraft()==null?null:"Bài học đang có bản nháp; hoàn tất hoặc bỏ nháp trước.";
            for(var slot:lesson.getPublished().data().questions()){
                var old=content.questionVersion(slot.questionVersionId()).orElseThrow(AuthoringAccess::missing);
                if(!needsUpdate(old,materialId,target.getId()))continue;
                var root=content.question(old.getQuestionId(),false).orElseThrow(AuthoringAccess::missing);
                roots.put(root.getId(),root);
                var current=root.getPublished();
                if(current==null||root.getRetiredAt()!=null){
                    reason="Bài có câu hỏi đã ngừng cấp.";continue;
                }
                if(!withAudio(old.data(),target.getId()).equals(withAudio(current.data(),target.getId()))){
                    reason="Bài đang dùng câu hỏi cũ khác nội dung bản công bố; cần chỉnh bài thủ công.";continue;
                }
                if(Objects.equals(current.data().materialVersionId(),target.getId()))continue;
                if(!needsUpdate(current,materialId,target.getId())){
                    reason="Câu hỏi đã chuyển sang tài liệu khác; cần chỉnh bài thủ công.";continue;
                }
                String blocked=questionReason(root,actor);
                if(blocked!=null){reason=blocked;continue;}
                required.add(root.getId());
            }
            lessonRows.add(new AudioUpdateData.LessonRow(lesson.getId(),lesson.getPublished().data().title(),
                lesson.getPublished().getId(),List.copyOf(required),reason));
        }
        StringBuilder state=new StringBuilder("v1|AUDIO-UPDATE|").append(actor.id()).append('|').append(materialId)
            .append('|').append(material.getRevision()).append('|').append(target.getId());
        roots.values().forEach(q->state.append("\nQ|").append(q.getId()).append('|').append(q.getRevision())
            .append('|').append(q.getPublished()==null?0:q.getPublished().getId())
            .append('|').append(q.getDraft()==null?0:q.getDraft().getId()));
        lessons.values().forEach(l->state.append("\nL|").append(l.getId()).append('|').append(l.getRevision())
            .append('|').append(l.getPublished().getId()).append('|').append(l.getDraft()==null?0:l.getDraft().getId()));
        String token;
        try{token=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(state.toString().getBytes(StandardCharsets.UTF_8)));}
        catch(Exception ex){throw new IllegalStateException(ex);}
        return new Plan(material,target,new AudioUpdateData.Preview(materialId,target.getId(),target.getVersionNo(),token,
            List.copyOf(questionRows),List.copyOf(lessonRows),foreignQuestions,foreignLessons),roots,lessons);
    }
    @Override
    public AudioUpdateData.Preview preview(long id,UserPrincipal actor){author(actor);return plan(id,actor).view();}

    private record Locks(Set<Long> materials,Set<Long> questions,Set<Long> lessons) {}
    private Locks dependencies(Plan plan,AudioUpdateData.Request input){
        var materialIds=new TreeSet<Long>();materialIds.add(plan.material().getId());
        var questionIds=new TreeSet<Long>(input.questionIds());
        var lessonIds=new TreeSet<Long>(input.lessonIds());
        for(long id:lessonIds){
            var lesson=plan.lessons().get(id);
            if(lesson==null)throw conflict("PREVIEW_STALE","Danh sách bài đã thay đổi. Hãy xem lại danh sách.");
            for(var slot:lesson.getPublished().data().questions()){
                var q=content.questionVersion(slot.questionVersionId()).orElseThrow(AuthoringAccess::missing);
                questionIds.add(q.getQuestionId());
                Long material=q.data().materialVersionId();
                if(material!=null)materialIds.add(content.materialVersion(material).orElseThrow(AuthoringAccess::missing).getMaterialId());
            }
        }
        return new Locks(materialIds,questionIds,lessonIds);
    }
    private void lock(Locks ids){
        ids.materials().forEach(content::lockMaterial);
        for(long id:ids.questions()){
            var root=content.question(id,true).orElseThrow(AuthoringAccess::missing);
            if(root.getPublished()!=null)content.refresh(root.getPublished());
            if(root.getDraft()!=null)content.refresh(root.getDraft());
        }
        for(long id:ids.lessons()){
            var root=content.lesson(id,true).orElseThrow(AuthoringAccess::missing);
            content.refresh(root.getPublished());
        }
    }
    private void validateSelection(Plan plan,AudioUpdateData.Request input){
        if(plan.target().getId()!=input.targetVersionId()||!plan.view().previewToken().equals(input.previewToken()))
            throw conflict("PREVIEW_STALE","Audio hoặc nội dung liên quan đã thay đổi. Hãy xem lại danh sách.");
        for(long id:input.questionIds()){
            var row=plan.view().questions().stream().filter(q->q.id()==id).findFirst().orElseThrow(AuthoringAccess::missing);
            if(row.reason()!=null)throw conflict("CONTENT_IN_USE",row.reason());
        }
        for(long id:input.lessonIds()){
            var row=plan.view().lessons().stream().filter(l->l.id()==id).findFirst().orElseThrow(AuthoringAccess::missing);
            if(row.reason()!=null)throw conflict("CONTENT_IN_USE",row.reason());
            if(!input.questionIds().containsAll(row.requiredQuestionIds()))
                throw invalid("questionIds","Cần chọn đủ câu hỏi liên quan cho mỗi bài đã chọn.");
        }
    }
    private void publish(String kind,long id,String requestId,UserPrincipal actor){
        var preview=publication.preview(kind,id,actor);
        publication.publish(kind,id,new Command(requestId,preview.baseRevision(),preview.draftVersionId(),preview.previewToken()),actor);
    }
    @Override
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public RequestReceiptService.Result apply(long id,AudioUpdateData.Request input,UserPrincipal actor){
        AssetServiceImpl.author(actor);
        return receipts.execute(actor,input.requestId(),"POST /api/author/materials/"+id+"/audio-update",input,200,()->{
            ContentGovernance.admin(actor);governance.lock();
            var before=plan(id,actor);
            validateSelection(before,input);
            var locked=dependencies(before,input);
            lock(locked);
            var current=plan(id,actor);
            validateSelection(current,input);
            var after=dependencies(current,input);
            if(!locked.materials().containsAll(after.materials())||!locked.questions().containsAll(after.questions()))
                throw conflict("PREVIEW_STALE","Tham chiếu học liệu đã đổi. Hãy xem lại danh sách.");
            var updatedQuestions=new ArrayList<AudioUpdateData.Updated>();
            for(long questionId:new TreeSet<>(input.questionIds())){
                var root=content.question(questionId,false).orElseThrow(AuthoringAccess::missing);
                var version=content.save(new QuestionVersionEntity(root.getId(),root.getLastVersionNo()+1,
                    withAudio(root.getPublished().data(),current.target().getId())));
                root.startDraft(version);content.save(root);
                publish("questions",root.getId(),input.requestId(),actor);
                updatedQuestions.add(new AudioUpdateData.Updated(root.getId(),version.getId()));
            }
            var updatedLessons=new ArrayList<AudioUpdateData.Updated>();
            for(long lessonId:new TreeSet<>(input.lessonIds())){
                var root=content.lesson(lessonId,false).orElseThrow(AuthoringAccess::missing);
                var original=root.getPublished().data();
                var slots=new ArrayList<Slot>();
                for(var slot:original.questions()){
                    var old=content.questionVersion(slot.questionVersionId()).orElseThrow(AuthoringAccess::missing);
                    if(needsUpdate(old,id,current.target().getId())){
                        var question=content.question(old.getQuestionId(),false).orElseThrow(AuthoringAccess::missing);
                        var next=question.getPublished();
                        if(!Objects.equals(next.data().materialVersionId(),current.target().getId())
                            ||!withAudio(old.data(),current.target().getId()).equals(next.data()))
                            throw conflict("PREVIEW_STALE","Phiên bản câu hỏi không còn phù hợp để thay audio.");
                        slots.add(new Slot(next.getId(),slot.practiceRole(),slot.position()));
                    }else slots.add(slot);
                }
                var data=new Lesson(original.title(),original.description(),original.instructions(),original.objectiveIds(),List.copyOf(slots));
                var version=content.save(new LessonVersionEntity(root.getId(),root.getLastVersionNo()+1,data));
                root.startDraft(version);content.save(root);
                publish("lessons",root.getId(),input.requestId(),actor);
                updatedLessons.add(new AudioUpdateData.Updated(root.getId(),version.getId()));
            }
            return new AudioUpdateData.Applied(id,current.target().getId(),List.copyOf(updatedQuestions),List.copyOf(updatedLessons));
        });
    }
}
