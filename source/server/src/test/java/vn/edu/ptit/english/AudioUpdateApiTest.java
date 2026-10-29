package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import vn.edu.ptit.english.dto.UserRecord;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.service.PublishedContentReader;
import vn.edu.ptit.english.service.impl.MediaInspector;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"app.demo.enabled=false","app.bootstrap-admin.enabled=false"})
@Testcontainers
class AudioUpdateApiTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("english_audio_update_test").withUsername("test_app").withPassword("test-password");
    static final Path FILES=temporary();
    static Path temporary(){try{return Files.createTempDirectory("english-audio-update-test-");}catch(IOException e){throw new UncheckedIOException(e);}}
    @DynamicPropertySource static void database(DynamicPropertyRegistry r){
        r.add("spring.datasource.url",()->MYSQL.getJdbcUrl()+"?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
        r.add("app.assets.directory",FILES::toString);
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired RequestReceiptJpaRepository receipts;
    @Autowired QuestionVersionJpaRepository questionVersions;
    @Autowired PublishedContentReader reader;
    // Audio decoding is tested by MaterialApiTest; these tests isolate publication/authorization transactions.
    @MockitoBean MediaInspector media;
    UserRecord teacher,other,admin,student;
    @BeforeEach void setup(){
        new TransactionTemplate(transactions).executeWithoutResult(s->{
            for(String sql:List.of("DELETE FROM lesson_questions","UPDATE lessons SET draft_version_id=NULL,published_version_id=NULL",
                "DELETE FROM lesson_objectives","DELETE FROM lesson_versions","DELETE FROM lessons",
                "UPDATE questions SET draft_version_id=NULL,published_version_id=NULL","DELETE FROM question_versions","DELETE FROM questions",
                "UPDATE materials SET draft_version_id=NULL,published_version_id=NULL","DELETE FROM material_versions","DELETE FROM materials",
                "DELETE FROM assets","DELETE FROM request_receipts","DELETE FROM units","DELETE FROM learning_objectives","DELETE FROM topics","DELETE FROM users"))
                em.createNativeQuery(sql).executeUpdate();
        });
        when(media.inspect(any(Path.class))).thenReturn(new MediaInspector.Media("audio/wav",60d));
        String hash=passwords.encode("TestPass@123");
        teacher=users.create("teacher@audio.test",hash,"Teacher",Role.TEACHER);
        other=users.create("other@audio.test",hash,"Other",Role.TEACHER);
        admin=users.create("admin@audio.test",hash,"Admin",Role.ADMIN);
        student=users.create("student@audio.test",hash,"Student",Role.STUDENT);
    }
    String key(){return UUID.randomUUID().toString();}
    JsonNode body(HttpResponse<String> r)throws Exception{return json.readTree(r.body());}
    JsonNode ok(HttpResponse<String> r,int expected)throws Exception{
        assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(expected);
        return r.body().isBlank()?json.nullNode():body(r);
    }
    final class Client {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        String csrf;
        HttpResponse<String> call(String method,String path,Object data)throws Exception{
            var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(30));
            if(!method.equals("GET"))b.header("X-CSRF-TOKEN",csrf);
            if(data!=null)b.header("Content-Type","application/json");
            return http.send(b.method(method,data==null?HttpRequest.BodyPublishers.noBody():
                HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data))).build(),HttpResponse.BodyHandlers.ofString());
        }
        Client login(UserRecord user)throws Exception{
            csrf=body(call("GET","/api/auth/csrf",null)).path("token").asText();
            ok(call("POST","/api/auth/login",Map.of("email",user.email(),"password","TestPass@123")),200);
            csrf=body(call("GET","/api/auth/csrf",null)).path("token").asText();return this;
        }
        long upload()throws Exception{
            String boundary="audio-"+key();
            var out=new ByteArrayOutputStream();
            out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"requestId\"\r\n\r\n"+key()
                +"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"demo.wav\"\r\nContent-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(new byte[48]);out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
            var r=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/assets"))
                .header("X-CSRF-TOKEN",csrf).header("Content-Type","multipart/form-data; boundary="+boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
            return ok(r,201).path("id").asLong();
        }
    }
    Map<String,Object> audio(long asset){
        var d=new LinkedHashMap<String,Object>();d.put("requestId",key());d.put("kind","AUDIO");d.put("title","Morning audio");
        d.put("assetId",asset);d.put("transcript","Linh gets up at seven.");d.put("startSeconds",0);d.put("endSeconds",null);
        d.put("source",Map.of("label","Test audio","url","https://example.test","license","Test"));return d;
    }
    JsonNode publish(Client c,String kind,long id)throws Exception{
        var p=ok(c.call("GET","/api/author/"+kind+"/"+id+"/preview",null),200);
        assertThat(p.path("errors").size()).withFailMessage(p.toString()).isZero();
        return ok(new Client().login(admin).call("POST","/api/author/"+kind+"/"+id+"/publish",Map.of("requestId",key(),
            "draftVersionId",p.path("draftVersionId").asLong(),"baseRevision",p.path("baseRevision").asLong(),
            "previewToken",p.path("previewToken").asText())),200);
    }
    record Fixture(long topic,long objective,long unit,long material,long oldAudio,long newAudio,long lesson,long oldLesson,
        List<Long> questions,List<Long> oldQuestions,List<String> origins) {}
    long[] catalog(Client c)throws Exception{
        var manager=new Client().login(admin);var me=ok(c.call("GET","/api/auth/me",null),200);
        long topic=ok(manager.call("POST","/api/author/topics",Map.of("requestId",key(),"code","topic-"+key(),"name","Daily","description","")),201).path("id").asLong();
        long objective=ok(manager.call("POST","/api/author/objectives",Map.of("requestId",key(),"code","obj-"+key(),"topicId",topic,"name","Daily","description","")),201).path("id").asLong();
        var unitInput=new java.util.HashMap<String,Object>(Map.of("requestId",key(),"topicId",topic,"title","Unit","description","","stage",1,"sortOrder",0));if(me.path("role").asText().equals("TEACHER"))unitInput.put("assignedTeacherId",me.path("id").asLong());
        long unit=ok(manager.call("POST","/api/author/units",unitInput),201).path("id").asLong();
        return new long[]{topic,objective,unit};
    }
    Map<String,Object> question(long topic,long objective,long material,String prompt){
        return new LinkedHashMap<>(Map.ofEntries(Map.entry("requestId",key()),Map.entry("type","SINGLE_CHOICE"),
            Map.entry("topicId",topic),Map.entry("objectiveId",objective),Map.entry("difficulty","EASY"),Map.entry("prompt",prompt),
            Map.entry("explanation","Linh gets up at seven."),Map.entry("hint","Listen for the time."),
            Map.entry("materialVersionId",material),Map.entry("source",Map.of("label","Test","url","https://example.test","license","Test")),
            Map.entry("content",Map.of("options",List.of(Map.of("id","a","text","Seven"),Map.of("id","b","text","Eight")))),
            Map.entry("answerKey",Map.of("correctOptionId","a"))));
    }
    Fixture lesson(Client c,long material,long audio)throws Exception{
        var cat=catalog(c);var ids=new ArrayList<Long>();var versions=new ArrayList<Long>();var origins=new ArrayList<String>();
        var slots=new ArrayList<Map<String,Object>>();
        var roles=List.of("GUIDED","INDEPENDENT","REVIEW_VARIANT");
        for(int i=0;i<3;i++){
            var q=ok(c.call("POST","/api/author/questions",question(cat[0],cat[1],audio,"Time "+i)),201);
            ids.add(q.path("id").asLong());versions.add(q.path("draftVersionId").asLong());origins.add(q.path("originKey").asText());
            slots.add(Map.of("questionVersionId",q.path("draftVersionId").asLong(),"practiceRole",roles.get(i),"position",0));
        }
        var l=ok(c.call("POST","/api/author/lessons",Map.of("requestId",key(),"unitId",cat[2],"unitBaseRevision",0,
            "title","Morning lesson","description","Listen","instructions",List.of(Map.of("kind","TEXT","text","Listen.")),
            "objectiveIds",List.of(cat[1]),"questions",slots)),201);
        var pub=publish(c,"lessons",l.path("id").asLong());
        return new Fixture(cat[0],cat[1],cat[2],material,audio,0,l.path("id").asLong(),pub.path("publishedVersionId").asLong(),ids,versions,origins);
    }
    Fixture fixture(Client c)throws Exception{
        var m=ok(c.call("POST","/api/author/materials",audio(c.upload())),201);
        m=publish(c,"materials",m.path("id").asLong());
        var f=lesson(c,m.path("id").asLong(),m.path("publishedVersionId").asLong());
        var draft=ok(c.call("POST","/api/author/materials/"+f.material()+"/draft",Map.of("requestId",key(),"baseRevision",m.path("rootRevision").asLong())),200);
        var d=audio(c.upload());d.put("draftVersionId",draft.path("draftVersionId").asLong());d.put("baseRevision",draft.path("draft").path("revision").asLong());
        ok(c.call("PUT","/api/author/materials/"+f.material()+"/draft",d),200);
        var updated=publish(c,"materials",f.material());
        return new Fixture(f.topic(),f.objective(),f.unit(),f.material(),f.oldAudio(),updated.path("publishedVersionId").asLong(),
            f.lesson(),f.oldLesson(),f.questions(),f.oldQuestions(),f.origins());
    }
    JsonNode preview(Client c,Fixture f)throws Exception{return ok(new Client().login(admin).call("GET","/api/author/materials/"+f.material()+"/audio-update",null),200);}
    Map<String,Object> command(JsonNode p,List<Long> questions,List<Long> lessons){
        return new LinkedHashMap<>(Map.of("requestId",key(),"targetVersionId",p.path("targetVersionId").asLong(),
            "previewToken",p.path("previewToken").asText(),"questionIds",questions,"lessonIds",lessons,"contentConfirmed",true));
    }
    HttpResponse<String> apply(Client c,Fixture f,Object data)throws Exception{return new Client().login(admin).call("POST","/api/author/materials/"+f.material()+"/audio-update",data);}

    @Test void updatesSelectedQuestionsAndLessonAtomicallyAndPreservesHistoryAndOrigin()throws Exception{
        var c=new Client().login(teacher);var f=fixture(c);var p=preview(c,f);
        assertThat(p.path("questions").size()).isEqualTo(3);assertThat(p.path("lessons").size()).isEqualTo(1);
        var command=command(p,f.questions(),List.of(f.lesson()));
        var first=ok(apply(c,f,command),200);
        assertThat(first.path("questions").size()).isEqualTo(3);assertThat(first.path("lessons").size()).isEqualTo(1);
        for(int i=0;i<3;i++){
            var root=ok(c.call("GET","/api/author/questions/"+f.questions().get(i),null),200);
            assertThat(root.path("ownerId").asLong()).isEqualTo(teacher.id());
            assertThat(root.path("originKey").asText()).isEqualTo(f.origins().get(i));
            assertThat(root.path("published").path("materialVersionId").asLong()).isEqualTo(f.newAudio());
            assertThat(reader.questionVersion(f.oldQuestions().get(i)).data().materialVersionId()).isEqualTo(f.oldAudio());
        }
        assertThat(reader.lessonVersion(f.oldLesson()).questions().stream().map(s->s.questionVersionId()).toList()).isEqualTo(f.oldQuestions());
        assertThat(reader.activeLesson(f.lesson()).versionId()).isNotEqualTo(f.oldLesson());
        long count=questionVersions.count();
        var replay=apply(c,f,command);assertThat(ok(replay,200)).isEqualTo(first);
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(questionVersions.count()).isEqualTo(count);
        var remaining=preview(c,f);assertThat(remaining.path("questions").isEmpty()).isTrue();assertThat(remaining.path("lessons").isEmpty()).isTrue();
        command.put("requestId",key());assertThat(apply(c,f,command).statusCode()).isEqualTo(409);
    }
    @Test void administratorCanUpdateAnyAuthorButCannotOverwriteExistingDraftsAndTeacherCannotApply()throws Exception{
        var a=new Client().login(teacher);var b=new Client().login(other);var f=fixture(a);
        var borrowed=lesson(b,f.material(),f.oldAudio());
        var root=ok(a.call("GET","/api/author/questions/"+f.questions().get(0),null),200);
        var draft=ok(a.call("POST","/api/author/questions/"+f.questions().get(0)+"/draft",
            Map.of("requestId",key(),"baseRevision",root.path("rootRevision").asLong())),200);
        var p=preview(a,f);
        assertThat(p.path("foreignQuestionCount").asInt()).isZero();assertThat(p.path("foreignLessonCount").asInt()).isZero();
        assertThat(p.path("questions").get(0).path("reason").isTextual()).isTrue();
        assertThat(p.path("lessons").get(0).path("reason").isTextual()).isTrue();
        assertThat(apply(a,f,command(p,List.of(f.questions().get(0)),List.of())).statusCode()).isEqualTo(409);
        assertThat(a.call("POST","/api/author/materials/"+f.material()+"/audio-update",command(p,List.of(borrowed.questions().get(0)),List.of())).statusCode()).isEqualTo(403);
        ok(apply(b,f,command(preview(b,f),borrowed.questions(),List.of(borrowed.lesson()))),200);
        assertThat(reader.activeLesson(f.lesson()).versionId()).isEqualTo(f.oldLesson());
        assertThat(ok(a.call("GET","/api/author/questions/"+f.questions().get(0),null),200).path("draftVersionId")).isEqualTo(draft.path("draftVersionId"));
        assertThat(new Client().login(student).call("GET","/api/author/materials/"+f.material()+"/audio-update",null).statusCode()).isEqualTo(403);
    }
    @Test void rejectsStalePreviewAndRollsBackEveryVersionWhenLessonPublicationFails()throws Exception{
        var c=new Client().login(teacher);var f=fixture(c);var old=preview(c,f);
        var root=ok(c.call("GET","/api/author/questions/"+f.questions().get(0),null),200);
        var draft=ok(c.call("POST","/api/author/questions/"+f.questions().get(0)+"/draft",
            Map.of("requestId",key(),"baseRevision",root.path("rootRevision").asLong())),200);
        assertThat(apply(c,f,command(old,f.questions(),List.of(f.lesson()))).statusCode()).isEqualTo(409);
        ok(c.call("DELETE","/api/author/questions/"+root.path("id").asLong()+"/draft?requestId="+key()
            +"&baseRevision="+draft.path("draft").path("revision").asLong()+"&draftVersionId="+draft.path("draftVersionId").asLong(),null),204);
        var command=command(preview(c,f),f.questions(),List.of(f.lesson()));
        long beforeVersions=questionVersions.count(),beforeReceipts=receipts.count();
        new TransactionTemplate(transactions).executeWithoutResult(s->em.createNativeQuery(
            "ALTER TABLE lessons ADD CONSTRAINT audio_update_failure CHECK (published_version_id IS NULL OR published_version_id="+f.oldLesson()+")").executeUpdate());
        try{assertThat(apply(c,f,command).statusCode()).isGreaterThanOrEqualTo(400);}
        finally{new TransactionTemplate(transactions).executeWithoutResult(s->em.createNativeQuery("ALTER TABLE lessons DROP CHECK audio_update_failure").executeUpdate());}
        assertThat(questionVersions.count()).isEqualTo(beforeVersions);assertThat(receipts.count()).isEqualTo(beforeReceipts);
        assertThat(reader.activeLesson(f.lesson()).versionId()).isEqualTo(f.oldLesson());
        for(int i=0;i<3;i++)assertThat(ok(c.call("GET","/api/author/questions/"+f.questions().get(i),null),200)
            .path("publishedVersionId").asLong()).isEqualTo(f.oldQuestions().get(i));
        ok(apply(c,f,command),200);
    }
    @Test void canUpdateOnlyLessonWhenQuestionsAlreadyUseNewAudioAndRequiresConfirmation()throws Exception{
        var c=new Client().login(teacher);var f=fixture(c);
        for(int i=0;i<3;i++){
            var root=ok(c.call("GET","/api/author/questions/"+f.questions().get(i),null),200);
            var draft=ok(c.call("POST","/api/author/questions/"+root.path("id").asLong()+"/draft",Map.of("requestId",key(),"baseRevision",root.path("rootRevision").asLong())),200);
            var data=question(f.topic(),f.objective(),f.newAudio(),"Time "+i);
            data.put("draftVersionId",draft.path("draftVersionId").asLong());data.put("baseRevision",draft.path("draft").path("revision").asLong());
            ok(c.call("PUT","/api/author/questions/"+root.path("id").asLong()+"/draft",data),200);publish(c,"questions",root.path("id").asLong());
        }
        var p=preview(c,f);assertThat(p.path("questions").isEmpty()).isTrue();
        assertThat(p.path("lessons").get(0).path("requiredQuestionIds").isEmpty()).isTrue();
        long count=questionVersions.count();
        var command=command(p,List.of(),List.of(f.lesson()));command.put("contentConfirmed",false);
        assertThat(apply(c,f,command).statusCode()).isEqualTo(400);
        command.put("contentConfirmed",true);
        var result=ok(apply(c,f,command),200);assertThat(result.path("questions").isEmpty()).isTrue();
        assertThat(result.path("lessons").size()).isEqualTo(1);assertThat(questionVersions.count()).isEqualTo(count);
    }
    @Test void concurrentAdministratorSessionsCreateOnlyOneSetOfVersions()throws Exception{
        var c=new Client().login(teacher);var administrator=new Client().login(admin);var f=fixture(c);
        var a=command(preview(c,f),f.questions(),List.of(f.lesson()));
        var b=command(preview(administrator,f),f.questions(),List.of(f.lesson()));
        var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var one=pool.submit(()->{gate.await();return apply(c,f,a);});
            var two=pool.submit(()->{gate.await();return apply(administrator,f,b);});
            gate.countDown();
            assertThat(List.of(one.get(30,TimeUnit.SECONDS).statusCode(),two.get(30,TimeUnit.SECONDS).statusCode()))
                .containsExactlyInAnyOrder(200,409);
            assertThat(questionVersions.count()).isEqualTo(6);
            assertThat(reader.activeLesson(f.lesson()).versionId()).isNotEqualTo(f.oldLesson());
        }finally{pool.shutdownNow();}
    }
}
