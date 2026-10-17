package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import vn.edu.ptit.english.bootstrap.*;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"app.demo.enabled=false","app.bootstrap-admin.enabled=false"}) @Testcontainers
class CurriculumSeedTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("english_seed_test").withUsername("test_app").withPassword("test-password");
    static final Path FILES=temp();static Path temp(){try{return Files.createTempDirectory("english-seed-test-");}catch(IOException e){throw new UncheckedIOException(e);}}
    @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",()->MYSQL.getJdbcUrl()+"?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);r.add("app.assets.directory",FILES::toString);}
    @Autowired DemoCurriculum curriculum;@Autowired CurriculumSeedService seed;@Autowired ObjectMapper json;@Autowired EntityManager em;@Autowired PlatformTransactionManager transactions;
    @Autowired UserRepository users;@Autowired UserJpaRepository userEntities;@Autowired PasswordEncoder passwords;@Autowired AccountGuard accounts;
    @Autowired TopicJpaRepository topics;@Autowired ObjectiveJpaRepository objectives;@Autowired UnitJpaRepository units;
    @Autowired AssetJpaRepository assets;@Autowired MaterialJpaRepository materials;@Autowired QuestionJpaRepository questions;@Autowired LessonJpaRepository lessons;@Autowired RequestReceiptJpaRepository receipts;
    @Autowired AssetService files;@Autowired MaterialService materialService;@Autowired AuthoringService authoring;@Autowired PublishedContentReader reader;
    UserPrincipal actor;TransactionTemplate tx;
    <T> T inTx(Supplier<T> work){return tx.execute(s->work.get());}
    String key(){return UUID.randomUUID().toString();}
    @BeforeEach void prepare()throws Exception{
        tx=new TransactionTemplate(transactions);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        tx.executeWithoutResult(s->{
            em.createNativeQuery("DELETE FROM lesson_questions").executeUpdate();em.createNativeQuery("DELETE FROM lesson_objectives").executeUpdate();em.createNativeQuery("UPDATE lessons SET draft_version_id=NULL,published_version_id=NULL").executeUpdate();em.createNativeQuery("DELETE FROM lesson_versions").executeUpdate();em.createNativeQuery("DELETE FROM lessons").executeUpdate();
            em.createNativeQuery("UPDATE questions SET draft_version_id=NULL,published_version_id=NULL").executeUpdate();em.createNativeQuery("DELETE FROM question_versions").executeUpdate();em.createNativeQuery("DELETE FROM questions").executeUpdate();
            em.createNativeQuery("UPDATE materials SET draft_version_id=NULL,published_version_id=NULL").executeUpdate();em.createNativeQuery("DELETE FROM material_versions").executeUpdate();em.createNativeQuery("DELETE FROM materials").executeUpdate();assets.deleteAllInBatch();receipts.deleteAllInBatch();units.deleteAllInBatch();objectives.deleteAllInBatch();topics.deleteAllInBatch();userEntities.deleteAllInBatch();
        });
        try(var paths=Files.list(FILES)){for(var p:paths.toList())Files.delete(p);}
        actor=UserPrincipal.from(users.create("seed@test.local",passwords.encode("TestPass@123"),"Demo author",Role.TEACHER));
        tx.executeWithoutResult(s->new CatalogDemoInitializer(users,topics,objectives,units,accounts,"seed@test.local").run(new DefaultApplicationArguments()));
    }
    JsonNode snapshot(){return inTx(()->{
        var state=new LinkedHashMap<String,Object>();state.put("materials",materials.findAll().stream().sorted(Comparator.comparing(MaterialEntity::getId)).map(m->materialService.get(m.getId(),actor)).toList());
        state.put("questions",questions.findAll().stream().sorted(Comparator.comparing(QuestionEntity::getId)).map(q->authoring.get("questions",q.getId(),actor)).toList());state.put("lessons",lessons.findAll().stream().sorted(Comparator.comparing(LessonEntity::getId)).map(l->authoring.get("lessons",l.getId(),actor)).toList());state.put("receipts",receipts.count());state.put("assets",assets.findAll().stream().map(AssetEntity::view).toList());state.put("units",units.findAll().stream().map(u->authoring.unit(u.getId(),actor)).toList());return json.valueToTree(state);
    });}
    void assertCanonical(){inTx(()->{
        assertThat(topics.count()).isEqualTo(3);assertThat(objectives.count()).isEqualTo(6);assertThat(units.count()).isEqualTo(3);
        var origins=new HashSet<String>();var types=new HashMap<String,Integer>();
        for(var lesson:curriculum.data().lessons()){
            var root=lessons.findBySeedKey(lesson.seedKey()).orElseThrow();assertThat(root.getDraft()).isNull();assertThat(root.status()).isEqualTo("PUBLISHED");var data=root.getPublished().data();assertThat(data.questions()).hasSize(15);
            for(String role:List.of("GUIDED","INDEPENDENT","REVIEW_VARIANT"))assertThat(data.questions().stream().filter(s->s.practiceRole().equals(role)).map(s->s.position()).toList()).containsExactly(0,1,2,3,4);
            var topic=units.findById(root.getUnitId()).orElseThrow().getTopicId();
            for(var q:lesson.questions()){var qr=questions.findBySeedKey(q.seedKey()).orElseThrow();assertThat(qr.status()).isEqualTo("PUBLISHED");assertThat(qr.getDraft()).isNull();assertThat(origins.add(qr.getOriginKey())).isTrue();var qv=reader.questionVersion(qr.getPublished().getId());assertThat(qv.data().topicId()).isEqualTo(topic);assertThat(data.objectiveIds()).contains(qv.data().objectiveId());types.merge(qv.data().type(),1,Integer::sum);}
        }
        assertThat(origins).hasSize(90);assertThat(types).containsExactlyInAnyOrderEntriesOf(Map.of("SINGLE_CHOICE",30,"FILL_BLANK",30,"MATCHING",30));
        for(var m:curriculum.data().materials()){var root=materials.findBySeedKey(m.seedKey()).orElseThrow();assertThat(root.status()).isEqualTo("PUBLISHED");if(m.file()!=null){var asset=assets.findById(root.getPublished().data().assetId()).orElseThrow();assertThat(asset.view().sha256()).isEqualTo(m.sha256());assertThat(files.read(asset.getId(),actor).path()).exists();}}
        assertThat(reader.activeLessons(null)).hasSizeGreaterThanOrEqualTo(6);return null;
    });}
    @Test void freshDatabaseHasSixLessonsNinetyUniqueRootsAndRepeatIsNoOp(){
        var report=seed.seed(actor);assertThat(report.createdMaterials()).isEqualTo(13);assertThat(report.createdQuestions()).isEqualTo(90);assertThat(report.createdLessons()).isEqualTo(6);assertCanonical();assertThat(assets.count()).isEqualTo(7);
        var before=snapshot();var repeat=seed.seed(actor);assertThat(repeat.preservedLessons()).isEqualTo(6);assertThat(repeat.createdQuestions()).isZero();assertThat(snapshot()).isEqualTo(before);
    }
    ObjectNode updateBody(JsonNode root,String parent){ObjectNode data=root.path("draft").deepCopy();long revision=data.path("revision").asLong(),id=data.path("id").asLong();data.remove(List.of("id",parent,"versionNo","revision","state","schemaVersion"));data.put("requestId",key());data.put("baseRevision",revision);data.put("draftVersionId",id);return data;}
    JsonNode question(long id){return json.valueToTree(authoring.get("questions",id,actor));}JsonNode lesson(long id){return json.valueToTree(authoring.get("lessons",id,actor));}
    @Test void rerunPreservesEditedDraftsRetirementAndUserCreatedContent(){
        seed.seed(actor);var first=curriculum.data().lessons().get(0);long qid=inTx(()->questions.findBySeedKey(first.questions().get(0).seedKey()).orElseThrow().getId());
        var q=question(qid);authoring.start("questions",qid,new MaterialRequests.Command(key(),q.path("rootRevision").asLong(),null,null),actor);var edit=updateBody(question(qid),"questionId");edit.put("prompt","Teacher changed this question");authoring.update(qid,AuthoringRequests.question(edit,true),actor);
        long mid=inTx(()->materials.findBySeedKey(first.questions().get(0).materialKey()).orElseThrow().getId());var m=materialService.get(mid,actor);materialService.start(mid,new MaterialRequests.Command(key(),m.rootRevision(),null,null),actor);var body=updateBody(json.valueToTree(materialService.get(mid,actor)),"materialId");body.put("title","Teacher changed this material");materialService.update(mid,MaterialRequests.input(body,true),actor);
        long lid=inTx(()->lessons.findBySeedKey(first.seedKey()).orElseThrow().getId());var l=lesson(lid);authoring.retire("lessons",lid,new MaterialRequests.Command(key(),l.path("rootRevision").asLong(),null,null),actor);
        var copy=authoring.copy(qid,new AuthoringRequests.Copy(key(),q.path("publishedVersionId").asLong()),actor);assertThat(copy.status()).isEqualTo(201);
        var before=snapshot();seed.seed(actor);assertThat(snapshot()).isEqualTo(before);assertThat(question(qid).path("draft").path("prompt").asText()).isEqualTo("Teacher changed this question");assertThat(lesson(lid).path("status").asText()).isEqualTo("RETIRED");
    }
    Map<String,Long> legacy(){return inTx(()->{
        accounts.lockCurrent(actor);var ids=new LinkedHashMap<String,Long>();var versions=new HashMap<String,Long>();
        for(var spec:curriculum.data().materials())if(spec.legacyAlias()!=null){ObjectNode body=spec.data().deepCopy();body.put("requestId",key());if(spec.file()!=null){try{long asset=files.upload(new CurriculumSeedService.ResourceFile(spec.file(),curriculum.bytes(spec)),key(),actor).body().path("id").asLong();body.put("assetId",asset);}catch(IOException e){throw new UncheckedIOException(e);}}long id=materialService.create(MaterialRequests.input(body,false),actor).body().path("id").asLong();var p=materialService.preview(id,actor);materialService.publish(id,new MaterialRequests.Command(key(),p.baseRevision(),p.draftVersionId(),p.previewToken()),actor);var root=materials.findById(id).orElseThrow();root.setSeedKey(spec.legacyAlias());materials.saveAndFlush(root);ids.put(spec.legacyAlias(),id);versions.put(spec.seedKey(),root.getPublished().getId());}
        var first=curriculum.data().lessons().get(0);var objective=objectives.findByCode(first.objectiveCode()).orElseThrow().view();var slotVersions=new HashMap<String,Long>();
        for(var spec:first.questions())if(spec.legacyAlias()!=null){ObjectNode body=spec.data().deepCopy();body.put("requestId",key());body.put("topicId",objective.topicId());body.put("objectiveId",objective.id());body.put("materialVersionId",versions.get(spec.materialKey()));long id=authoring.create(AuthoringRequests.question(body,false),actor).body().path("id").asLong();var p=authoring.preview("questions",id,actor);authoring.publish("questions",id,new MaterialRequests.Command(key(),p.baseRevision(),p.draftVersionId(),p.previewToken()),actor);var root=questions.findById(id).orElseThrow();root.setSeedKey(spec.legacyAlias());questions.saveAndFlush(root);ids.put(spec.legacyAlias(),id);slotVersions.put(spec.legacyAlias(),root.getPublished().getId());}
        ObjectNode body=first.legacyBaseline().deepCopy();var unit=units.findBySeedKey(first.unitSeedKey()).orElseThrow();body.put("requestId",key());body.put("unitId",unit.getId());body.put("unitBaseRevision",unit.getRevision());body.set("objectiveIds",json.valueToTree(List.of(objective.id())));body.set("questions",json.valueToTree(List.of(new AuthoringData.Slot(slotVersions.get("m02-authoring-question-reading"),"GUIDED",0),new AuthoringData.Slot(slotVersions.get("m02-authoring-question-audio"),"INDEPENDENT",0),new AuthoringData.Slot(slotVersions.get("m02-authoring-question-image"),"REVIEW_VARIANT",0))));long id=authoring.create(AuthoringRequests.lesson(body,false),actor).body().path("id").asLong();var p=authoring.preview("lessons",id,actor);authoring.publish("lessons",id,new MaterialRequests.Command(key(),p.baseRevision(),p.draftVersionId(),p.previewToken()),actor);var root=lessons.findById(id).orElseThrow();root.setSeedKey(first.legacyAlias());lessons.saveAndFlush(root);ids.put(first.legacyAlias(),id);ids.put("oldLessonVersion",root.getPublished().getId());return ids;
    });}
    @Test void untouchedThreeQuestionWalkthroughUpgradesWithoutLosingIdsOriginsOrOldVersion(){
        var old=legacy();long id=old.get("m02-authoring-walkthrough");String origin=question(old.get("m02-authoring-question-reading")).path("originKey").asText();var report=seed.seed(actor);
        assertThat(report.adoptedMaterials()).isEqualTo(3);assertThat(report.adoptedQuestions()).isEqualTo(3);assertThat(report.createdQuestions()).isEqualTo(87);assertThat(report.upgradedLessons()).isEqualTo(1);assertThat(lessons.count()).isEqualTo(6);assertThat(questions.count()).isEqualTo(90);assertThat(materials.count()).isEqualTo(13);assertCanonical();
        assertThat(inTx(()->lessons.findBySeedKey(curriculum.data().lessons().get(0).seedKey()).orElseThrow().getId()).longValue()).isEqualTo(id);assertThat(lesson(id).path("published").path("versionNo").asInt()).isEqualTo(2);assertThat(reader.lessonVersion(old.get("oldLessonVersion")).questions()).hasSize(3);assertThat(question(old.get("m02-authoring-question-reading")).path("originKey").asText()).isEqualTo(origin);
    }
    @Test void customizedLegacyDraftsArePreservedBesideTheFullCanonicalDataset(){
        var old=legacy();long id=old.get("m02-authoring-walkthrough");var l=lesson(id);authoring.start("lessons",id,new MaterialRequests.Command(key(),l.path("rootRevision").asLong(),null,null),actor);var changed=updateBody(lesson(id),"lessonId");changed.put("title","My edited walkthrough");authoring.update(id,AuthoringRequests.lesson(changed,true),actor);
        long mid=old.get("m02-material-reading");var m=materialService.get(mid,actor);materialService.start(mid,new MaterialRequests.Command(key(),m.rootRevision(),null,null),actor);var edit=updateBody(json.valueToTree(materialService.get(mid,actor)),"materialId");edit.put("readingText","Teacher’s own new text.");materialService.update(mid,MaterialRequests.input(edit,true),actor);
        var beforeLesson=lesson(id);JsonNode beforeMaterial=json.valueToTree(materialService.get(mid,actor));seed.seed(actor);assertThat(lesson(id)).isEqualTo(beforeLesson);assertThat(json.<JsonNode>valueToTree(materialService.get(mid,actor))).isEqualTo(beforeMaterial);assertThat(lessons.count()).isEqualTo(7);assertCanonical();
    }
    @Test void failedLessonTransactionLeavesNoQuestionOrReceiptHalfSeedAndCanResume(){
        tx.executeWithoutResult(s->em.createNativeQuery("ALTER TABLE lessons ADD CONSTRAINT test_seed_failure CHECK (published_version_id IS NULL)").executeUpdate());
        try{assertThatThrownBy(()->seed.seed(actor)).isInstanceOf(RuntimeException.class);}finally{tx.executeWithoutResult(s->em.createNativeQuery("ALTER TABLE lessons DROP CHECK test_seed_failure").executeUpdate());}
        assertThat(questions.count()).isZero();assertThat(lessons.count()).isZero();assertThat(materials.count()).isEqualTo(13);assertThat(assets.count()).isEqualTo(7);assertThat(receipts.count()).isEqualTo(33);long assetsBefore=assets.count();seed.seed(actor);assertThat(assets.count()).isEqualTo(assetsBefore);assertCanonical();
    }
    @Test void simultaneousSeedRunsDoNotDuplicateRootsOrFiles()throws Exception{
        var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(()->{gate.await();return seed.seed(actor);});var b=pool.submit(()->{gate.await();return seed.seed(actor);});gate.countDown();var left=a.get(120,TimeUnit.SECONDS);var right=b.get(120,TimeUnit.SECONDS);assertThat(left.createdQuestions()+right.createdQuestions()).isEqualTo(90);assertThat(left.createdLessons()+right.createdLessons()).isEqualTo(6);assertThat(questions.count()).isEqualTo(90);assertThat(materials.count()).isEqualTo(13);assertThat(assets.count()).isEqualTo(7);assertCanonical();}finally{pool.shutdownNow();}
    }
}
