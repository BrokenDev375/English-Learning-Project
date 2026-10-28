package vn.edu.ptit.english.bootstrap;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.dto.AuthoringData.Slot;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.*;

/** Seed keys are checkpoints, not an instruction to reset an existing root. */
@Service
public class CurriculumSeedService {
    private static final Logger log=LoggerFactory.getLogger(CurriculumSeedService.class);
    public record Report(int createdMaterials,int adoptedMaterials,int createdQuestions,int adoptedQuestions,int createdLessons,int upgradedLessons,int preservedLessons) {}
    private final DemoCurriculum curriculum;private final MaterialJpaRepository materials;private final AssetJpaRepository assets;
    private final QuestionJpaRepository questions;private final QuestionVersionJpaRepository questionVersions;private final LessonJpaRepository lessons;private final UnitJpaRepository units;
    private final ObjectiveJpaRepository objectives;private final AssetService files;private final MaterialService materialService;
    private final AuthoringService authoring;private final AccountGuard accounts;private final ObjectMapper json;private final TransactionTemplate tx;
    private final vn.edu.ptit.english.service.impl.AuthoringPublication publication;
    private final vn.edu.ptit.english.service.impl.ContentGovernance governance;
    private final RequestReceiptService receipts;
    public CurriculumSeedService(DemoCurriculum curriculum,MaterialJpaRepository materials,AssetJpaRepository assets,QuestionJpaRepository questions,QuestionVersionJpaRepository questionVersions,LessonJpaRepository lessons,UnitJpaRepository units,ObjectiveJpaRepository objectives,AssetService files,MaterialService materialService,AuthoringService authoring,AccountGuard accounts,ObjectMapper json,PlatformTransactionManager transactions,vn.edu.ptit.english.service.impl.AuthoringPublication publication,vn.edu.ptit.english.service.impl.ContentGovernance governance,RequestReceiptService receipts){
        this.publication=publication;this.governance=governance;this.receipts=receipts;
        this.curriculum=curriculum;this.materials=materials;this.assets=assets;this.questions=questions;this.questionVersions=questionVersions;this.lessons=lessons;this.units=units;this.objectives=objectives;this.files=files;this.materialService=materialService;this.authoring=authoring;this.accounts=accounts;this.json=json;tx=new TransactionTemplate(transactions);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    private String key(String stage,String seed){return UUID.nameUUIDFromBytes(("m02-demo-1.0.0|"+stage+"|"+seed).getBytes(StandardCharsets.UTF_8)).toString();}
    public Report seed(UserPrincipal actor){
        int[] counts=new int[7];
        for(var spec:curriculum.data().lessons())tx.executeWithoutResult(status->{accounts.lockCurrent(actor);if(objectives.findByCode(spec.objectiveCode()).isEmpty()){var unit=units.findBySeedKey(spec.unitSeedKey()).orElseThrow();objectives.saveAndFlush(new ObjectiveEntity(actor.id(),unit.getTopicId(),spec.objectiveCode(),spec.objectiveName(),spec.data().path("description").asText()));}});
        for(var spec:curriculum.data().materials())tx.executeWithoutResult(status->{accounts.lockCurrent(actor);seedMaterial(spec,actor,counts);});
        // All questions + the lesson + publication + their receipts commit together per lesson.
        for(var spec:curriculum.data().lessons())tx.executeWithoutResult(status->{accounts.lockCurrent(actor);governance.lock();seedLesson(spec,actor,counts);});
        return new Report(counts[0],counts[1],counts[2],counts[3],counts[4],counts[5],counts[6]);
    }
    private MaterialRequests.Input materialInput(DemoCurriculum.Material spec,Long asset){ObjectNode input=spec.data().deepCopy();input.put("requestId",key("material-create",spec.seedKey()));if(asset!=null)input.put("assetId",asset);return MaterialRequests.input(input,false);}
    private boolean pristine(MaterialEntity root,UserPrincipal actor){return root.getOwnerId()==actor.id()&&root.getRevision()==1&&root.getLastVersionNo()==1&&root.getRetiredAt()==null&&root.getDraft()==null&&root.getPublished()!=null&&root.getPublished().getVersionNo()==1;}
    private boolean pristine(QuestionEntity root,UserPrincipal actor){return root.getOwnerId()==actor.id()&&root.getRevision()==1&&root.getLastVersionNo()==1&&root.getRetiredAt()==null&&root.getDraft()==null&&root.getPublished()!=null&&root.getPublished().getVersionNo()==1;}
    private void seedMaterial(DemoCurriculum.Material spec,UserPrincipal actor,int[] counts){
        if(materials.findBySeedKey(spec.seedKey()).isPresent())return;
        if(spec.legacyAlias()!=null){var legacy=materials.findBySeedKey(spec.legacyAlias());if(legacy.isPresent()){
            var root=legacy.get();if(pristine(root,actor)){
                var data=root.getPublished().data();boolean same=materialInput(spec,data.assetId()).data().equals(data);
                if(spec.file()!=null)same=same&&assets.findById(data.assetId()).map(a->a.view().sha256().equals(spec.sha256())).orElse(false);
                if(same){root.setSeedKey(spec.seedKey());materials.saveAndFlush(root);counts[1]++;return;}
            }
            log.info("Preserving customized legacy material {}; creating separate canonical seed",spec.legacyAlias());
        }}
        Long asset=null;if(spec.file()!=null){try{asset=files.upload(new ResourceFile(spec.file(),curriculum.bytes(spec)),key("asset-upload",spec.seedKey()),actor).body().path("id").asLong();}catch(IOException e){throw new UncheckedIOException(e);}}
        long id=materialService.create(materialInput(spec,asset),actor).body().path("id").asLong();
        var p=materialService.preview(id,actor);materialService.publish(id,new MaterialRequests.Command(key("material-publish",spec.seedKey()),p.baseRevision(),p.draftVersionId(),p.previewToken()),actor);
        var root=materials.findById(id).orElseThrow();root.setSeedKey(spec.seedKey());materials.saveAndFlush(root);counts[0]++;
    }
    private Long materialVersion(String seed){if(seed==null)return null;var root=materials.findBySeedKey(seed).orElseThrow();if(root.getPublished()==null||root.getRetiredAt()!=null)throw new IllegalStateException("Seed dependency is unavailable: "+seed+". Existing content was preserved.");return root.getPublished().getId();}
    private AuthoringRequests.QuestionInput questionInput(DemoCurriculum.Question spec,long topic,long objective){ObjectNode input=spec.data().deepCopy();input.put("requestId",key("question-create",spec.seedKey()));input.put("topicId",topic);input.put("objectiveId",objective);Long material=materialVersion(spec.materialKey());if(material==null)input.putNull("materialVersionId");else input.put("materialVersionId",material);return AuthoringRequests.question(input,false);}
    private QuestionVersionEntity seedQuestion(DemoCurriculum.Question spec,long topic,long objective,UserPrincipal actor,int[] counts){
        var existing=questions.findBySeedKey(spec.seedKey());if(existing.isPresent()){
            var root=existing.get();if(root.getPublished()==null||root.getRetiredAt()!=null)throw new IllegalStateException("Seed question is unavailable: "+spec.seedKey()+". Existing content was preserved.");return root.getPublished();
        }
        var input=questionInput(spec,topic,objective);
        if(spec.legacyAlias()!=null){var legacy=questions.findBySeedKey(spec.legacyAlias());if(legacy.isPresent()){
            var root=legacy.get();if(pristine(root,actor)&&root.getPublished().data().equals(input.data())){root.setSeedKey(spec.seedKey());questions.saveAndFlush(root);counts[3]++;return root.getPublished();}
            log.info("Preserving customized legacy question {}; creating separate canonical seed",spec.legacyAlias());
        }}
        long id=authoring.create(input,actor).body().path("id").asLong();var root=questions.findById(id).orElseThrow();root.setSeedKey(spec.seedKey());questions.saveAndFlush(root);counts[2]++;return root.getDraft();
    }
    private boolean legacyLesson(DemoCurriculum.Lesson spec,LessonEntity root,long objective,UserPrincipal actor){
        if(root.getOwnerId()!=actor.id()||root.getRevision()!=1||root.getLastVersionNo()!=1||root.getRetiredAt()!=null||root.getDraft()!=null||root.getPublished()==null||root.getPublished().getVersionNo()!=1)return false;
        var data=root.getPublished().data();var baseline=spec.legacyBaseline();if(baseline==null||baseline.isNull()||!data.title().equals(baseline.path("title").asText())||!data.description().equals(baseline.path("description").asText())||!data.instructions().equals(baseline.get("instructions"))||!data.objectiveIds().equals(List.of(objective))||data.questions().size()!=3)return false;
        var oldRoles=Map.of("m02-authoring-question-reading","GUIDED","m02-authoring-question-audio","INDEPENDENT","m02-authoring-question-image","REVIEW_VARIANT");
        for(var entry:oldRoles.entrySet()){var q=questions.findBySeedKey(entry.getKey());if(q.isEmpty())return false;long qid=q.get().getId();boolean found=data.questions().stream().anyMatch(slot->slot.practiceRole().equals(entry.getValue())&&slot.position()==0&&questionVersions.findById(slot.questionVersionId()).map(v->v.getQuestionId()==qid).orElse(false));if(!found)return false;}
        return true;
    }
    private void seedLesson(DemoCurriculum.Lesson spec,UserPrincipal actor,int[] counts){
        if(lessons.findBySeedKey(spec.seedKey()).isPresent()){counts[6]++;return;}
        var unit=units.findBySeedKey(spec.unitSeedKey()).orElseThrow();var objective=objectives.findByCode(spec.objectiveCode()).orElseThrow().view();
        if(unit.getTopicId()!=objective.topicId())throw new IllegalStateException("Seed Unit/Objective was changed; refusing to overwrite: "+spec.seedKey());
        LessonEntity old=spec.legacyAlias()==null?null:lessons.findBySeedKey(spec.legacyAlias()).orElse(null);
        boolean upgrade=old!=null&&old.getUnitId()==unit.getId()&&legacyLesson(spec,old,objective.id(),actor);
        var slots=new ArrayList<Slot>();for(var question:spec.questions()){var version=seedQuestion(question,objective.topicId(),objective.id(),actor,counts);slots.add(new Slot(version.getId(),question.practiceRole(),question.position()));}
        var body=(ObjectNode)spec.data().deepCopy();body.put("requestId",key(upgrade?"lesson-update":"lesson-create",spec.seedKey()));body.set("objectiveIds",json.valueToTree(List.of(objective.id())));body.set("questions",json.valueToTree(slots));long id;
        if(upgrade){
            id=old.getId();var draft=authoring.start("lessons",id,new MaterialRequests.Command(key("lesson-start",spec.seedKey()),old.getRevision(),null,null),actor).body().path("draft");
            body.put("draftVersionId",draft.path("id").asLong());body.put("baseRevision",draft.path("revision").asLong());authoring.update(id,AuthoringRequests.lesson(body,true),actor);counts[5]++;
        }else{
            if(old!=null)log.info("Preserving customized legacy lesson {}; creating separate canonical seed",spec.legacyAlias());
            body.put("unitId",unit.getId());body.put("unitBaseRevision",units.findById(unit.getId()).orElseThrow().getRevision());id=authoring.create(AuthoringRequests.lesson(body,false),actor).body().path("id").asLong();counts[4]++;
        }
        var preview=authoring.preview("lessons",id,actor);
        var command=new MaterialRequests.Command(key("lesson-publish",spec.seedKey()),preview.baseRevision(),preview.draftVersionId(),preview.previewToken());
        // Bundled curriculum is a trusted bootstrap import, not a teacher HTTP publication.
        // Keep the original receipt operation/key so older successful imports replay unchanged.
        receipts.execute(actor,command.requestId(),"POST /api/author/lessons/"+id+"/publish",command,200,()->{
            publication.publish("lessons",id,command,actor);return authoring.get("lessons",id,actor);
        });
        var root=lessons.findById(id).orElseThrow();root.setSeedKey(spec.seedKey());lessons.saveAndFlush(root);
    }
    public record ResourceFile(String name,byte[] bytes) implements MultipartFile {
        public String getName(){return "file";}public String getOriginalFilename(){return name;}public String getContentType(){return "application/octet-stream";}
        public boolean isEmpty(){return bytes.length==0;}public long getSize(){return bytes.length;}public byte[] getBytes(){return bytes.clone();}
        public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}public void transferTo(File target)throws IOException{java.nio.file.Files.write(target.toPath(),bytes);}
    }
}
