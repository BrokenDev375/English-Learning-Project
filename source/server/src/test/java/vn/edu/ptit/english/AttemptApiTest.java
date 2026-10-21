package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import jakarta.persistence.EntityManager;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.skyscreamer.jsonassert.JSONAssert;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.dto.AttemptData.*;
import vn.edu.ptit.english.dto.AuthoringData.*;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.*;
import vn.edu.ptit.english.enums.AttemptEnums.*;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.ReviewSelectionProvider;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"app.demo.enabled=false","app.bootstrap-admin.enabled=false"})
@Testcontainers
class AttemptApiTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("m03_attempt_api_test");
    static final Path FILES=temporary();
    static Path temporary(){try{return Files.createTempDirectory("m03-attempt-api-");}catch(IOException e){throw new UncheckedIOException(e);}}
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->MYSQL.getJdbcUrl()+"?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);r.add("app.assets.directory",FILES::toString);
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired UserJpaRepository userEntities;
    @Autowired PasswordEncoder passwords;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired AttemptRepository attempts;
    @Autowired RequestReceiptJpaRepository receipts;
    @MockitoBean ReviewSelectionProvider review;
    @MockitoSpyBean AccountGuard guard;
    TransactionTemplate tx;
    UserRecord student,other,teacher,admin;
    long topic,lesson,lessonVersion,materialVersion,materialRoot,asset;
    List<Long> questionVersions=new ArrayList<>();
    String prefix;
    @BeforeEach void fixture() throws Exception {
        when(review.select(anyLong(),anyString(),nullable(Long.class),anyInt())).thenReturn(null);
        tx=new TransactionTemplate(transactions); questionVersions.clear(); prefix=UUID.randomUUID().toString();
        String hash=passwords.encode("TestPass@123");
        tx.executeWithoutResult(s->{
            student=users.create(prefix+"s@test.local",hash,"Student",Role.STUDENT);
            other=users.create(prefix+"o@test.local",hash,"Other",Role.STUDENT);
            teacher=users.create(prefix+"t@test.local",hash,"Teacher",Role.TEACHER);
            admin=users.create(prefix+"a@test.local",hash,"Admin",Role.ADMIN);
            var t=new TopicEntity(teacher.id(),prefix,"Topic","");em.persist(t);em.flush();topic=t.getId();
            var obj=new ObjectiveEntity(teacher.id(),topic,prefix,"Objective","");em.persist(obj);em.flush();
            var unit=new UnitEntity(teacher.id(),topic,"Unit","",1,0);em.persist(unit);em.flush();
            var a=new AssetEntity(teacher.id(),prefix,"fixture.wav","audio/wav",64,"0".repeat(64));em.persist(a);em.flush();asset=a.getId();
            var m=new MaterialEntity(teacher.id());em.persist(m);em.flush();materialRoot=m.getId();
            var mv=new MaterialVersionEntity(m.getId(),1,new MaterialData("AUDIO","Audio",null,asset,"SECRET_TRANSCRIPT",null,null,java.math.BigDecimal.ZERO,null,new MaterialData.Source("Test",null,null)));
            em.persist(mv);em.flush();m.initialDraft(mv);m.publish();materialVersion=mv.getId();
            var slots=new ArrayList<Slot>();
            for(int i=0;i<15;i++) {
                String type=i%3==0?"SINGLE_CHOICE":i%3==1?"FILL_BLANK":"MATCHING";
                String c=type.equals("SINGLE_CHOICE")?"{\"options\":[{\"id\":\"a\",\"text\":\"A\"},{\"id\":\"b\",\"text\":\"B\"}]}":type.equals("FILL_BLANK")?
                    "{\"parts\":[{\"kind\":\"BLANK\",\"blankId\":\"x\"},{\"kind\":\"TEXT\",\"text\":\" and \"},{\"kind\":\"BLANK\",\"blankId\":\"y\"}]}":
                    "{\"leftItems\":[{\"id\":\"l1\",\"text\":\"One\"},{\"id\":\"l2\",\"text\":\"Two\"}],\"rightItems\":[{\"id\":\"r1\",\"text\":\"One\"},{\"id\":\"r2\",\"text\":\"Two\"}]}";
                String k=type.equals("SINGLE_CHOICE")?"{\"correctOptionId\":\"b\"}":type.equals("FILL_BLANK")?
                    "{\"blanks\":[{\"blankId\":\"x\",\"acceptedAnswers\":[\"hello\"]},{\"blankId\":\"y\",\"acceptedAnswers\":[\"world\"]}],\"caseSensitive\":false,\"normalizeWhitespace\":true}":
                    "{\"pairs\":[{\"leftId\":\"l1\",\"rightId\":\"r1\"},{\"leftId\":\"l2\",\"rightId\":\"r2\"}]}";
                var q=new QuestionEntity(teacher.id(),UUID.randomUUID().toString());em.persist(q);em.flush();
                var qv=new QuestionVersionEntity(q.getId(),1,new Question(type,topic,obj.getId(),"EASY","Question "+i,node(c),node(k),"SECRET_EXPLANATION","SECRET_HINT",materialVersion,new MaterialData.Source("Test",null,null)));
                em.persist(qv);em.flush();q.initialDraft(qv);q.publish();questionVersions.add(qv.getId());
                slots.add(new Slot(qv.getId(),i<5?"GUIDED":i<10?"INDEPENDENT":"REVIEW_VARIANT",i%5));
            }
            var l=new LessonEntity(teacher.id(),unit.getId(),0);em.persist(l);em.flush();lesson=l.getId();
            var lv=new LessonVersionEntity(lesson,1,new Lesson("Lesson","Description",node("[{\"kind\":\"TEXT\",\"text\":\"Read\"}]"),List.of(obj.getId()),slots));
            em.persist(lv);em.flush();l.initialDraft(lv);l.publish();em.flush();lessonVersion=lv.getId();
        });
        Files.write(FILES.resolve(prefix),new byte[64]);
    }
    @AfterAll static void cleanupFiles()throws IOException {
        try(var paths=Files.walk(FILES)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
    }
    String key(){return UUID.randomUUID().toString();}
    JsonNode node(String value){try{return json.readTree(value);}catch(Exception e){throw new IllegalArgumentException(e);}}
    JsonNode body(HttpResponse<String> r)throws Exception{return json.readTree(r.body());}
    JsonNode ok(HttpResponse<String> r,int code)throws Exception {assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(code);return body(r);}
    void error(HttpResponse<String> r,int status,String code)throws Exception {assertThat(ok(r,status).path("code").asText()).isEqualTo(code);}
    final class Client {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        String csrf;
        HttpRequest request(String method,String path,Object data)throws Exception {
            var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(30));
            if(!method.equals("GET")&&csrf!=null)b.header("X-CSRF-TOKEN",csrf);
            if(data!=null)b.header("Content-Type","application/json");
            return b.method(method,data==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data))).build();
        }
        HttpResponse<String> call(String method,String path,Object data)throws Exception{return http.send(request(method,path,data),HttpResponse.BodyHandlers.ofString());}
        Client login(UserRecord user)throws Exception {
            csrf=ok(call("GET","/api/auth/csrf",null),200).path("token").asText();
            ok(call("POST","/api/auth/login",Map.of("email",user.email(),"password","TestPass@123")),200);
            csrf=ok(call("GET","/api/auth/csrf",null),200).path("token").asText();return this;
        }
    }
    Map<String,Object> learn(){return Map.of("requestId",key(),"mode","LEARN","lessonId",lesson);}
    Map<String,Object> practice(int count){return Map.of("requestId",key(),"mode","PRACTICE","topicId",topic,"difficulty","EASY","count",count);}
    JsonNode start(Client client)throws Exception{return ok(client.call("POST","/api/attempts",learn()),201);}
    String path(JsonNode snapshot){return "/api/attempts/"+snapshot.path("attemptId").asLong();}
    ObjectNode mutation(JsonNode snapshot) {
        var m=json.createObjectNode().put("requestId",key()).put("baseRevision",snapshot.path("revision").asLong());
        var answers=m.putArray("answers");snapshot.path("items").forEach(i->answers.addObject().put("attemptQuestionId",i.path("attemptQuestionId").asLong()).set("response",i.path("response").deepCopy()));
        m.set("playerState",snapshot.path("playerState").deepCopy());return m;
    }
    void response(ObjectNode m,int index,String value){((ObjectNode)m.path("answers").get(index)).set("response",node(value));}
    void noTrials(JsonNode snapshot) {tx.executeWithoutResult(s->{for(var item:snapshot.path("items"))assertThat(attempts.trials(item.path("attemptQuestionId").asLong())).isEmpty();});}

    String itemPath(JsonNode s,int index){return path(s)+"/items/"+s.path("items").get(index).path("attemptQuestionId").asLong();}
    JsonNode refresh(Client c,JsonNode s)throws Exception{return ok(c.call("GET",path(s),null),200);}
    ObjectNode command(JsonNode s){return json.createObjectNode().put("requestId",key()).put("baseRevision",s.path("revision").asLong());}
    JsonNode correctAnswer(JsonNode item) {
        return switch(item.path("question").path("type").asText()) {
            case "SINGLE_CHOICE" -> node("{\"type\":\"SINGLE_CHOICE\",\"optionId\":\"b\"}");
            case "FILL_BLANK" -> node("{\"type\":\"FILL_BLANK\",\"values\":[{\"blankId\":\"x\",\"value\":\"hello\"},{\"blankId\":\"y\",\"value\":\"world\"}]}");
            default -> node("{\"type\":\"MATCHING\",\"pairs\":[{\"leftId\":\"l1\",\"rightId\":\"r1\"},{\"leftId\":\"l2\",\"rightId\":\"r2\"}]}");
        };
    }
    ObjectNode fullAnswers(JsonNode s) {
        var m=mutation(s);
        for(int i=0;i<s.path("items").size();i++)((ObjectNode)m.path("answers").get(i)).set("response",correctAnswer(s.path("items").get(i)));
        return m;
    }
    JsonNode checkedGuided(Client c,JsonNode snapshot)throws Exception {
        var s=snapshot;
        for(int i=0;i<s.path("items").size();i++)if(s.path("items").get(i).path("practiceRole").asText().equals("GUIDED")) {
            var input=command(s); input.set("response",correctAnswer(s.path("items").get(i)));
            ok(c.call("POST",itemPath(s,i)+"/check",input),200); s=refresh(c,s);
        }
        return s;
    }
    String progressPath(){return "/api/me/learning-progress?unitId="+tx.execute(t->em.find(LessonEntity.class,lesson).getUnitId());}
    JsonNode progress(Client c)throws Exception{return ok(c.call("GET",progressPath(),null),200).path("items").get(0);}

    @Test void socketInvalidatesOnlyCommittedOwnedMutationsAndNotReplays()throws Exception {
        var c=new Client().login(student);var s=start(c);long attemptId=s.path("attemptId").asLong();
        var messages=new LinkedBlockingQueue<String>();
        var listener=new WebSocket.Listener(){
            final StringBuilder buffer=new StringBuilder();
            public void onOpen(WebSocket ws){ws.request(1);}
            public CompletionStage<?> onText(WebSocket ws,CharSequence data,boolean last){buffer.append(data);if(last){messages.add(buffer.toString());buffer.setLength(0);}ws.request(1);return null;}
        };
        String cookie=((CookieManager)c.http.cookieHandler().orElseThrow()).getCookieStore().getCookies().stream().map(v->v.getName()+"="+v.getValue()).collect(java.util.stream.Collectors.joining("; "));
        var ws=HttpClient.newHttpClient().newWebSocketBuilder().header("Cookie",cookie).header("Origin","http://localhost:5173").buildAsync(URI.create("ws://127.0.0.1:"+port+"/ws/events"),listener).join();
        try {
            assertThat(messages.poll(5,TimeUnit.SECONDS)).contains("CONNECTED");
            ws.sendText("{\"version\":1,\"type\":\"SUBSCRIBE\",\"requestId\":\"bad\",\"attemptId\":9223372036854775806}",true).join();
            assertThat(messages.poll(5,TimeUnit.SECONDS)).contains("RESOURCE_NOT_FOUND");
            ws.sendText("{\"version\":1,\"type\":\"SUBSCRIBE\",\"requestId\":\"own\",\"attemptId\":"+attemptId+"}",true).join();
            assertThat(messages.poll(5,TimeUnit.SECONDS)).contains("ACK");
            var input=mutation(s);ok(c.call("PUT",path(s)+"/draft",input),200);
            assertThat(messages.poll(5,TimeUnit.SECONDS)).contains("ATTEMPT_CHANGED","\"revision\":1").doesNotContain("response","answerKey");
            ok(c.call("PUT",path(s)+"/draft",input),200);assertThat(messages.poll(300,TimeUnit.MILLISECONDS)).isNull();
            var next=mutation(refresh(c,s));String requestId=next.path("requestId").asText();
            tx.executeWithoutResult(t->em.createNativeQuery("ALTER TABLE request_receipts ADD CONSTRAINT m06_reject_receipt CHECK (request_id <> '"+requestId+"')").executeUpdate());
            try { error(c.call("PUT",path(s)+"/draft",next),503,"DATABASE_UNAVAILABLE"); }
            finally { tx.executeWithoutResult(t->em.createNativeQuery("ALTER TABLE request_receipts DROP CHECK m06_reject_receipt").executeUpdate()); }
            assertThat(messages.poll(300,TimeUnit.MILLISECONDS)).isNull();assertThat(refresh(c,s).path("revision").asLong()).isEqualTo(1);
        } finally {ws.abort();}
    }
    @Test void guidedHintReplayAndFirstSupportAreRecordedWithoutTranscript()throws Exception {
        var c=new Client().login(student);var s=start(c);var hint=command(s);
        var h=ok(c.call("POST",itemPath(s,0)+"/hint",hint),200);assertThat(h.path("revision").asLong()).isEqualTo(1);
        var replay=c.call("POST",itemPath(s,0)+"/hint",hint);assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");assertThat(ok(replay,200)).isEqualTo(h);
        s=refresh(c,s);assertThat(ok(c.call("POST",itemPath(s,0)+"/hint",command(s)),200).path("revision").asLong()).isEqualTo(1);
        error(c.call("POST",itemPath(s,0)+"/hint",command(startRevision(s,0))),409,"REVISION_CONFLICT");
        var input=command(s);input.set("response",correctAnswer(s.path("items").get(0)));
        var f=ok(c.call("POST",itemPath(s,0)+"/check",input),200);
        assertThat(f.path("kind").asText()).isEqualTo("FIRST");assertThat(f.path("supportUsed").asBoolean()).isTrue();
        assertThat(f.path("outcome").asText()).isEqualTo("CORRECT");assertThat(f.toString()).contains("answerKey","SECRET_EXPLANATION").doesNotContain("transcript","SECRET_TRANSCRIPT");
        assertThat(refresh(c,s).path("items").get(0).path("response")).isEqualTo(input.path("response"));
    }
    JsonNode startRevision(JsonNode s,long revision){var copy=s.deepCopy();((ObjectNode)copy).put("revision",revision);return copy;}

    @Test void checkKeepsUnassistedFirstWhileLaterFeedbackIsSupported()throws Exception {
        var c=new Client().login(student);var s=start(c);var input=command(s);input.set("response",node("{\"type\":\"SINGLE_CHOICE\",\"optionId\":\"a\"}"));
        var first=ok(c.call("POST",itemPath(s,0)+"/check",input),200);assertThat(first.path("supportUsed").asBoolean()).isFalse();assertThat(first.path("outcome").asText()).isEqualTo("INCORRECT");
        s=refresh(c,s);var second=command(s);second.set("response",correctAnswer(s.path("items").get(0)));
        var retry=ok(c.call("POST",itemPath(s,0)+"/check",second),200);assertThat(retry.path("kind").asText()).isEqualTo("GUIDED_RETRY");assertThat(retry.path("trialNo").asInt()).isEqualTo(2);assertThat(retry.path("supportUsed").asBoolean()).isTrue();
        s=refresh(c,s);var result=ok(c.call("POST",path(s)+"/submit",fullAnswers(s)),200);var fact=result.path("items").get(0);
        assertThat(fact.path("firstOutcome").asText()).isEqualTo("INCORRECT");assertThat(fact.path("firstSupportUsed").asBoolean()).isFalse();assertThat(fact.path("finalOutcome").asText()).isEqualTo("CORRECT");
        assertThat(ok(c.call("POST",itemPath(s,0)+"/check",input),200)).isEqualTo(first);
    }
    @Test void feedbackRejectsIndependentForeignAndIncompleteAnswersWithoutMutation()throws Exception {
        var c=new Client().login(student);var s=start(c);var own=new Client().login(other);var foreign=start(own);
        var input=command(s);input.set("response",correctAnswer(s.path("items").get(5)));
        error(c.call("POST",itemPath(s,5)+"/check",input),409,"FEEDBACK_NOT_AVAILABLE");
        error(c.call("POST",itemPath(s,5)+"/hint",command(s)),409,"FEEDBACK_NOT_AVAILABLE");
        error(own.call("POST",itemPath(s,0)+"/hint",command(s)),404,"NOT_FOUND");
        error(c.call("POST",path(s)+"/items/"+foreign.path("items").get(0).path("attemptQuestionId").asLong()+"/hint",command(s)),404,"NOT_FOUND");
        input=command(s);input.set("response",node("{\"type\":\"FILL_BLANK\",\"values\":[{\"blankId\":\"x\",\"value\":\"hello\"}]}"));
        error(c.call("POST",itemPath(s,1)+"/check",input),400,"INVALID_ANSWER");
        error(c.call("POST",itemPath(s,1)+"/retry",input),409,"RESULT_NOT_READY");
        assertThat(refresh(c,s)).isEqualTo(s);noTrials(s);
    }
    @Test void missingHintHasNoEffect()throws Exception {
        tx.executeWithoutResult(t->{var old=em.find(QuestionVersionEntity.class,questionVersions.get(0));var d=old.data();
            var q=em.find(QuestionEntity.class,old.getQuestionId());var v=new QuestionVersionEntity(q.getId(),2,new Question(d.type(),d.topicId(),d.objectiveId(),d.difficulty(),d.prompt(),d.content(),d.answerKey(),d.explanation(),null,d.materialVersionId(),d.source()));
            em.persist(v);em.flush();q.startDraft(v);q.publish();var root=em.find(LessonEntity.class,lesson);var ld=em.find(LessonVersionEntity.class,lessonVersion).data();
            var slots=ld.questions().stream().map(slot->slot.questionVersionId()==old.getId()?new Slot(v.getId(),slot.practiceRole(),slot.position()):slot).toList();
            var lv=new LessonVersionEntity(lesson,2,new Lesson(ld.title(),ld.description(),ld.instructions(),ld.objectiveIds(),slots));em.persist(lv);em.flush();root.startDraft(lv);root.publish();});
        var c=new Client().login(student);var s=start(c);error(c.call("POST",itemPath(s,0)+"/hint",command(s)),404,"HINT_NOT_AVAILABLE");assertThat(refresh(c,s)).isEqualTo(s);
    }
    @Test void earlySubmitAcceptsPartialAndBlankFinalAnswersAndPersistsFacts()throws Exception {
        var c=new Client().login(student);var s=start(c);error(c.call("GET",path(s)+"/result",null),409,"RESULT_NOT_READY");
        var m=mutation(s);response(m,0,"{\"type\":\"SINGLE_CHOICE\",\"optionId\":\"b\"}");response(m,1,"{\"type\":\"FILL_BLANK\",\"values\":[{\"blankId\":\"x\",\"value\":\"hello\"}]}");
        ((ObjectNode)m.path("playerState")).put("currentAttemptQuestionId",s.path("items").get(1).path("attemptQuestionId").asLong());
        var result=ok(c.call("POST",path(s)+"/submit",m),200);
        assertThat(result.path("submissionId").asLong()).isPositive();assertThat(result.path("factsSchemaVersion").asInt()).isEqualTo(1);assertThat(result.path("submittedRevision").asLong()).isEqualTo(1);
        assertThat(result.path("answeredCount").asInt()).isEqualTo(2);assertThat(result.path("unansweredCount").asInt()).isEqualTo(8);assertThat(result.path("correctCount").asInt()).isEqualTo(1);assertThat(result.path("scorePercent").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(result.path("lessonCompleted").asBoolean()).isFalse();assertThat(result.path("items").get(1).path("finalResponseComplete").asBoolean()).isFalse();assertThat(result.path("items").get(2).path("firstOutcome").asText()).isEqualTo("UNANSWERED");
        assertThat(result.path("materialFeedback")).hasSize(1);assertThat(result.toString()).contains("SECRET_TRANSCRIPT");assertThat(progress(c).path("state").asText()).isEqualTo("STARTED");
        var restored=refresh(c,s);assertThat(restored.path("state").asText()).isEqualTo("SUBMITTED");JSONAssert.assertEquals(m.path("playerState").toString(),restored.path("playerState").toString(),true);assertThat(restored.toString()).doesNotContain("answerKey","SECRET_");
        JSONAssert.assertEquals(result.toString(),ok(c.call("GET",path(s)+"/result",null),200).toString(),true);
        tx.executeWithoutResult(t->{var stored=attempts.submission(s.path("attemptId").asLong()).orElseThrow();assertThat(stored.getResult().submissionId()).isEqualTo(stored.getId());assertThat(stored.getScorePercent()).isEqualByComparingTo("10.00");
            for(var item:s.path("items")){var history=attempts.trials(item.path("attemptQuestionId").asLong());assertThat(history).hasSize(1);assertThat(history.get(0).getSource()).isEqualTo(TrialSource.SUBMIT);}});
    }
    @Test void completeAnswersRequireGuidedChecksButDoNotRequireCorrectness()throws Exception {
        var c=new Client().login(student);var first=start(c);var unchecked=ok(c.call("POST",path(first)+"/submit",fullAnswers(first)),200);assertThat(unchecked.path("lessonCompleted").asBoolean()).isFalse();
        var s=checkedGuided(c,start(c));var m=fullAnswers(s);response(m,0,"{\"type\":\"SINGLE_CHOICE\",\"optionId\":\"a\"}");
        var result=ok(c.call("POST",path(s)+"/submit",m),200);assertThat(result.path("lessonCompleted").asBoolean()).isTrue();assertThat(result.path("correctCount").asInt()).isEqualTo(9);
        assertThat(result.path("independentFreshAnsweredCount").asInt()).isZero();assertThat(progress(c).path("state").asText()).isEqualTo("COMPLETED");
        var completed=progress(c);s=start(c);assertThat(progress(c).path("state").asText()).isEqualTo("COMPLETED");ok(c.call("POST",path(s)+"/submit",mutation(s)),200);
        var repeat=progress(c);assertThat(repeat.path("state").asText()).isEqualTo("COMPLETED");assertThat(repeat.path("completedAt")).isEqualTo(completed.path("completedAt"));assertThat(repeat.path("lastCompletedVersionId")).isEqualTo(completed.path("lastCompletedVersionId"));assertThat(repeat.path("lastScorePercent").asDouble()).isZero();
    }
    @Test void correctionsAndReplaysNeverChangeSubmissionFinalAnswersOrProgress()throws Exception {
        var c=new Client().login(student);var s=start(c);var result=ok(c.call("POST",path(s)+"/submit",mutation(s)),200);var beforeProgress=progress(c);s=refresh(c,s);
        var input=command(s);input.set("response",correctAnswer(s.path("items").get(5)));
        var feedback=ok(c.call("POST",itemPath(s,5)+"/retry",input),200);assertThat(feedback.path("kind").asText()).isEqualTo("CORRECTION");assertThat(feedback.path("trialNo").asInt()).isEqualTo(2);assertThat(feedback.path("supportUsed").asBoolean()).isTrue();
        assertThat(feedback.toString()).doesNotContain("transcript");var replay=c.call("POST",itemPath(s,5)+"/retry",input);assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");assertThat(ok(replay,200)).isEqualTo(feedback);
        JSONAssert.assertEquals(result.toString(),ok(c.call("GET",path(s)+"/result",null),200).toString(),true);assertThat(progress(c)).isEqualTo(beforeProgress);assertThat(refresh(c,s).path("items").get(5).path("response").isNull()).isTrue();
        var fresh=refresh(c,s);var incomplete=command(fresh);incomplete.set("response",node("{\"type\":\"SINGLE_CHOICE\",\"optionId\":null}"));error(c.call("POST",itemPath(s,0)+"/retry",incomplete),400,"INVALID_ANSWER");
        error(c.call("POST",itemPath(s,0)+"/hint",command(fresh)),409,"ATTEMPT_FINISHED");error(c.call("PUT",path(s)+"/draft",mutation(fresh)),409,"ATTEMPT_FINISHED");
    }
    @Test void freshCountsExcludeGuidedSeenAndEmptyButIncludePartialIndependent()throws Exception {
        var c=new Client().login(student);var s=start(c);var m=fullAnswers(s);response(m,5,"{\"type\":\"MATCHING\",\"pairs\":[{\"leftId\":\"l1\",\"rightId\":\"r1\"}]}");((ObjectNode)m.path("answers").get(6)).putNull("response");
        var result=ok(c.call("POST",path(s)+"/submit",m),200);assertThat(result.path("independentFreshAnsweredCount").asInt()).isEqualTo(4);assertThat(result.path("independentFreshCorrectCount").asInt()).isEqualTo(3);
        var repeat=start(c);result=ok(c.call("POST",path(repeat)+"/submit",fullAnswers(repeat)),200);assertThat(result.path("independentFreshAnsweredCount").asInt()).isZero();
    }
    @Test void practiceAndReviewUseActualDenominatorAndNeverCompleteLessons()throws Exception {
        var c=new Client().login(student);var s=ok(c.call("POST","/api/attempts",practice(3)),201);var m=fullAnswers(s);((ObjectNode)m.path("answers").get(0)).putNull("response");
        var result=ok(c.call("POST",path(s)+"/submit",m),200);assertThat(result.path("scorePercent").decimalValue()).isEqualByComparingTo("66.67");assertThat(result.path("lessonCompleted").asBoolean()).isFalse();assertThat(progress(c).path("state").asText()).isEqualTo("NOT_STARTED");
        when(review.select(student.id(),"DUE",topic,2)).thenReturn(List.of(questionVersions.get(10),questionVersions.get(11)));
        s=ok(c.call("POST","/api/attempts",Map.of("requestId",key(),"mode","REVIEW","reviewKind","DUE","topicId",topic,"count",2)),201);
        error(c.call("POST",itemPath(s,0)+"/hint",command(s)),409,"FEEDBACK_NOT_AVAILABLE");
        result=ok(c.call("POST",path(s)+"/submit",fullAnswers(s)),200);assertThat(result.path("independentFreshCorrectCount").asInt()).isEqualTo(2);assertThat(result.path("lessonCompleted").asBoolean()).isFalse();
    }
    @Test void simultaneousSubmitsWithSameKeyReplayOneSubmission()throws Exception {
        var a=new Client().login(student);var b=new Client().login(student);var s=start(a);var m=fullAnswers(s);
        var responses=concurrent(()->a.call("POST",path(s)+"/submit",m),()->b.call("POST",path(s)+"/submit",m));assertThat(ok(responses.get(0),200)).isEqualTo(ok(responses.get(1),200));assertThat(refresh(a,s).path("revision").asLong()).isEqualTo(1);
        tx.executeWithoutResult(t->assertThat(em.createQuery("select count(s) from SubmissionEntity s where s.attemptId=:id",Long.class).setParameter("id",s.path("attemptId").asLong()).getSingleResult()).isEqualTo(1));
    }
    @Test void simultaneousDifferentSubmitsAndDraftOrCheckHaveOneWinner()throws Exception {
        for(String operation:List.of("submit","draft","check")) {
            var a=new Client().login(student);var b=new Client().login(student);var s=start(a);var m=fullAnswers(s);var other=fullAnswers(s);var check=command(s);check.set("response",correctAnswer(s.path("items").get(0)));
            var responses=concurrent(()->a.call("POST",path(s)+"/submit",m),()->b.call(operation.equals("draft")?"PUT":"POST",operation.equals("check")?itemPath(s,0)+"/check":path(s)+"/"+operation,operation.equals("check")?check:other));
            assertThat(responses.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(200,409);
            var rejected=responses.stream().filter(r->r.statusCode()==409).findFirst().orElseThrow();assertThat(body(rejected).path("code").asText()).isIn("ATTEMPT_FINISHED","REVISION_CONFLICT");
            var current=refresh(a,s);if(current.path("state").asText().equals("IN_PROGRESS"))ok(a.call("POST",path(s)+"/submit",fullAnswers(current)),200);
            tx.executeWithoutResult(t->assertThat(em.createQuery("select count(s) from SubmissionEntity s where s.attemptId=:id",Long.class).setParameter("id",s.path("attemptId").asLong()).getSingleResult()).isEqualTo(1));
        }
    }
    @Test void discardedSubmitBodyCanBeReplayedAfterFurtherCorrection()throws Exception {
        var c=new Client().login(student);var s=start(c);var m=fullAnswers(s);var response=c.http.send(c.request("POST",path(s)+"/submit",m),HttpResponse.BodyHandlers.ofInputStream());assertThat(response.statusCode()).isEqualTo(200);response.body().close();
        var result=ok(c.call("GET",path(s)+"/result",null),200);var current=refresh(c,s);var retry=command(current);retry.set("response",correctAnswer(current.path("items").get(0)));ok(c.call("POST",itemPath(s,0)+"/retry",retry),200);
        var replay=c.call("POST",path(s)+"/submit",m);assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");assertThat(ok(replay,200)).isEqualTo(result);
        m.put("baseRevision",999);error(c.call("POST",path(s)+"/submit",m),409,"REQUEST_ID_REUSED");
    }
    @Test void submitRollsBackSubmissionTrialsProgressAndReceiptWhenLateWritesFail()throws Exception {
        for(String stage:List.of("progress","receipt")) {
            var c=new Client().login(student);var s=start(c);var m=fullAnswers(s);String requestId=m.path("requestId").asText();
            String table=stage.equals("progress")?"lesson_progress":"request_receipts";
            String condition=stage.equals("progress")?"(user_id <> "+student.id()+" OR last_score_percent IS NULL)":"(request_id <> '"+requestId+"')";
            tx.executeWithoutResult(t->em.createNativeQuery("ALTER TABLE "+table+" ADD CONSTRAINT m03_reject_submit CHECK "+condition).executeUpdate());
            try{error(c.call("POST",path(s)+"/submit",m),503,"DATABASE_UNAVAILABLE");}finally{tx.executeWithoutResult(t->em.createNativeQuery("ALTER TABLE "+table+" DROP CHECK m03_reject_submit").executeUpdate());}
            assertThat(refresh(c,s)).isEqualTo(s);noTrials(s);assertThat(progress(c).path("lastScorePercent").isNull()).isTrue();assertThat(receipts.findByActorIdAndRequestId(student.id(),requestId)).isEmpty();
            tx.executeWithoutResult(t->assertThat(attempts.submission(s.path("attemptId").asLong())).isEmpty());
            ok(c.call("POST",path(s)+"/submit",m),200);
            // A new actor avoids the previous completed row affecting the next failure fixture.
            student=other;other=tx.execute(t->users.create(key()+"@test.local",passwords.encode("TestPass@123"),"Other",Role.STUDENT));
        }
    }
    @Test void progressPagesIncludeNotStartedAndOwnHistoryButExcludeDraftsAndOthers()throws Exception {
        var c=new Client().login(student);var o=new Client().login(other);assertThat(progress(c).path("state").asText()).isEqualTo("NOT_STARTED");var s=start(c);assertThat(progress(c).path("state").asText()).isEqualTo("STARTED");assertThat(progress(o).path("state").asText()).isEqualTo("NOT_STARTED");
        long[] versions=tx.execute(t->{var root=em.find(LessonEntity.class,lesson);var old=em.find(LessonVersionEntity.class,lessonVersion).data();var v=new LessonVersionEntity(lesson,2,old);em.persist(v);em.flush();root.startDraft(v);root.publish();
            var draft=new LessonEntity(teacher.id(),root.getUnitId(),1);em.persist(draft);em.flush();return new long[]{v.getId(),root.getUnitId()};});
        assertThat(progress(c).path("updateAvailable").asBoolean()).isTrue();assertThat(progress(o).path("updateAvailable").asBoolean()).isFalse();assertThat(progress(c).path("currentPublishedVersionId").asLong()).isEqualTo(versions[0]);
        var page=ok(c.call("GET",progressPath()+"&size=1",null),200);assertThat(page.path("totalElements").asInt()).isEqualTo(1);assertThat(ok(c.call("GET",progressPath()+"&size=1&page=1",null),200).path("items")).isEmpty();
        assertThat(ok(c.call("GET","/api/me/learning-progress?unitId=9007199254740991",null),200).path("items")).isEmpty();
        ok(c.call("GET",progressPath()+"&size=51",null),400);ok(c.call("GET",progressPath()+"&page=-1",null),400);
        tx.executeWithoutResult(t->em.find(LessonEntity.class,lesson).retire());assertThat(progress(c).path("currentPublishedVersionId").isNull()).isTrue();assertThat(progress(c).path("updateAvailable").asBoolean()).isFalse();assertThat(ok(o.call("GET",progressPath(),null),200).path("items")).isEmpty();
        ok(c.call("POST",path(s)+"/submit",fullAnswers(s)),200);assertThat(ok(c.call("GET",path(s)+"/result",null),200).path("lessonVersionId").asLong()).isEqualTo(lessonVersion);
    }
    @Test void feedbackSubmissionAndProgressEnforceRolesOwnershipAndCsrf()throws Exception {
        var c=new Client().login(student);var s=start(c);var o=new Client().login(other);
        error(o.call("POST",path(s)+"/submit",mutation(s)),404,"NOT_FOUND");error(o.call("GET",path(s)+"/result",null),404,"NOT_FOUND");
        for(var user:List.of(teacher,admin)){var privileged=new Client().login(user);ok(privileged.call("GET",progressPath(),null),403);ok(privileged.call("POST",itemPath(s,0)+"/hint",command(s)),403);ok(privileged.call("POST",path(s)+"/submit",mutation(s)),403);ok(privileged.call("GET",path(s)+"/result",null),403);}
        c.csrf=null;error(c.call("POST",path(s)+"/submit",mutation(s)),403,"CSRF_INVALID");ok(new Client().call("GET",progressPath(),null),401);assertThat(refresh(c,s)).isEqualTo(s);
    }
    @Test void invalidFinalSnapshotCannotPartiallySubmit()throws Exception {
        var c=new Client().login(student);var s=start(c);var m=fullAnswers(s);((ArrayNode)m.path("answers")).remove(9);
        error(c.call("POST",path(s)+"/submit",m),400,"VALIDATION_FAILED");
        m=fullAnswers(s);response(m,9,"{\"type\":\"SINGLE_CHOICE\",\"optionId\":\"unknown\"}");error(c.call("POST",path(s)+"/submit",m),400,"INVALID_ANSWER");
        m=fullAnswers(s);((ObjectNode)m.path("playerState")).put("currentAttemptQuestionId",9007199254740991L);error(c.call("POST",path(s)+"/submit",m),400,"VALIDATION_FAILED");
        assertThat(refresh(c,s)).isEqualTo(s);noTrials(s);assertThat(progress(c).path("lastScorePercent").isNull()).isTrue();
        tx.executeWithoutResult(t->assertThat(attempts.submission(s.path("attemptId").asLong())).isEmpty());
    }
    @Test void publishingNewKeysPreservesPinnedGradingSavedResultAndCompletedProgress()throws Exception {
        var c=new Client().login(student);var s=checkedGuided(c,start(c));
        long newer=tx.execute(t->{
            var old=em.find(QuestionVersionEntity.class,questionVersions.get(0));var d=old.data();var root=em.find(QuestionEntity.class,old.getQuestionId());
            var qv=new QuestionVersionEntity(root.getId(),2,new Question(d.type(),d.topicId(),d.objectiveId(),d.difficulty(),"New prompt",d.content(),node("{\"correctOptionId\":\"a\"}"),"NEW_EXPLANATION",d.hint(),d.materialVersionId(),d.source()));
            em.persist(qv);em.flush();root.startDraft(qv);root.publish();var lessonRoot=em.find(LessonEntity.class,lesson);var ld=em.find(LessonVersionEntity.class,lessonVersion).data();
            var slots=ld.questions().stream().map(slot->slot.questionVersionId()==old.getId()?new Slot(qv.getId(),slot.practiceRole(),slot.position()):slot).toList();
            var lv=new LessonVersionEntity(lesson,2,new Lesson(ld.title(),ld.description(),ld.instructions(),ld.objectiveIds(),slots));em.persist(lv);em.flush();lessonRoot.startDraft(lv);lessonRoot.publish();return lv.getId();
        });
        var result=ok(c.call("POST",path(s)+"/submit",fullAnswers(s)),200);assertThat(result.path("correctCount").asInt()).isEqualTo(10);assertThat(result.path("lessonCompleted").asBoolean()).isTrue();assertThat(result.toString()).doesNotContain("NEW_EXPLANATION");
        var p=progress(c);assertThat(p.path("state").asText()).isEqualTo("COMPLETED");assertThat(p.path("lastCompletedVersionId").asLong()).isEqualTo(lessonVersion);assertThat(p.path("currentPublishedVersionId").asLong()).isEqualTo(newer);assertThat(p.path("updateAvailable").asBoolean()).isTrue();
        tx.executeWithoutResult(t->em.find(LessonEntity.class,lesson).retire());assertThat(ok(c.call("GET",path(s)+"/result",null),200)).isEqualTo(result);
    }

    @Test void learnPinsTenOrderedItemsAndSafeSnapshot()throws Exception {
        var c=new Client().login(student);var s=start(c);
        assertThat(s.path("items")).hasSize(10); assertThat(s.path("materials")).hasSize(1);
        for(int i=0;i<10;i++) {
            assertThat(s.path("items").get(i).path("practiceRole").asText()).isEqualTo(i<5?"GUIDED":"INDEPENDENT");
            assertThat(s.path("items").get(i).path("position").asInt()).isEqualTo(i);
        }
        assertThat(s.toString()).doesNotContain("answerKey","explanation","transcript","storageKey","SECRET_");
        var get=c.call("GET",path(s),null);assertThat(get.headers().firstValue("Cache-Control").orElse("")).contains("private","no-store");
        assertThat(ok(get,200)).isEqualTo(s);noTrials(s);
    }
    @Test void createReplayAndResumeKeepSnapshotAndCountExposureOnce()throws Exception {
        var c=new Client().login(student);var command=learn();var first=c.call("POST","/api/attempts",command);var s=ok(first,201);
        var m=mutation(s);response(m,0,"{\"type\":\"SINGLE_CHOICE\",\"optionId\":\"a\"}");ok(c.call("PUT",path(s)+"/draft",m),200);
        var replay=c.call("POST","/api/attempts",command);assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");assertThat(ok(replay,201)).isEqualTo(s);
        var resumed=ok(c.call("POST","/api/attempts",learn()),200);assertThat(resumed.path("attemptId")).isEqualTo(s.path("attemptId"));assertThat(resumed.path("revision").asLong()).isEqualTo(1);
        tx.executeWithoutResult(t->{assertThat(attempts.active(student.id(),Mode.LEARN)).hasSize(1);for(var item:s.path("items"))assertThat(attempts.exposure(new QuestionExposureKey(student.id(),item.path("originKey").asText()),false).orElseThrow().getSeenCount()).isEqualTo(1);});
    }
    @Test void practiceFiltersCapsShortageAndPrefersUnseen()throws Exception {
        var c=new Client().login(student);var first=ok(c.call("POST","/api/attempts",practice(1)),201);
        var next=ok(c.call("POST","/api/attempts",practice(2)),201);assertThat(next.path("items")).hasSize(2);
        for(var item:next.path("items")){assertThat(item.path("originKey")).isNotEqualTo(first.path("items").get(0).path("originKey"));assertThat(item.path("practiceRole").asText()).isEqualTo("INDEPENDENT");}
        var all=ok(c.call("POST","/api/attempts",practice(10)),201);assertThat(all.path("items")).hasSize(5);
        var ids=new HashSet<String>();all.path("items").forEach(i->assertThat(ids.add(i.path("originKey").asText())).isTrue());
        var hard=new HashMap<>(practice(3));hard.put("difficulty","HARD");error(c.call("POST","/api/attempts",hard),409,"NO_QUESTIONS_AVAILABLE");
        ok(c.call("POST","/api/attempts",practice(11)),400);
    }
    @Test void reviewIsNotReadyUntilProviderReturnsValidVersions()throws Exception {
        var c=new Client().login(student);var command=new LinkedHashMap<String,Object>();command.put("requestId",key());command.put("mode","REVIEW");command.put("reviewKind","DUE");command.put("topicId",topic);command.put("count",2);
        error(c.call("POST","/api/attempts",command),409,"REVIEW_NOT_READY");
        when(review.select(student.id(),"DUE",topic,2)).thenReturn(List.of(questionVersions.get(0)));
        error(c.call("POST","/api/attempts",command),409,"NO_QUESTIONS_AVAILABLE");
        when(review.select(student.id(),"DUE",topic,2)).thenReturn(List.of(questionVersions.get(10),questionVersions.get(11)));
        var s=ok(c.call("POST","/api/attempts",command),201);assertThat(s.path("items")).hasSize(2);assertThat(s.path("mode").asText()).isEqualTo("REVIEW");
    }
    @Test void partialAndNullAnswersAndPlayerStateRestoreWithoutTrials()throws Exception {
        var c=new Client().login(student);var s=start(c);var m=mutation(s);
        response(m,0,"{\"type\":\"SINGLE_CHOICE\",\"optionId\":null}");
        response(m,1,"{\"type\":\"FILL_BLANK\",\"values\":[{\"blankId\":\"x\",\"value\":\"hel\"}]}");
        response(m,2,"{\"type\":\"MATCHING\",\"pairs\":[{\"leftId\":\"l1\",\"rightId\":\"r2\"}]}");
        ((ObjectNode)m.get("playerState")).put("currentAttemptQuestionId",s.path("items").get(2).path("attemptQuestionId").asLong())
            .putArray("audioPositions").addObject().put("materialVersionId",materialVersion).put("seconds",12.5);
        var ack=ok(c.call("PUT",path(s)+"/draft",m),200);assertThat(ack.path("revision").asLong()).isEqualTo(1);assertThat(ack.path("answeredCount").asInt()).isEqualTo(2);
        var restored=ok(new Client().login(student).call("GET",path(s),null),200);JSONAssert.assertEquals(m.path("playerState").toString(),restored.path("playerState").toString(),true);
        for(int i=0;i<10;i++)assertThat(restored.path("items").get(i).path("response")).isEqualTo(m.path("answers").get(i).path("response"));
        assertThat(restored.toString()).doesNotContain("answerKey","explanation","transcript","SECRET_");noTrials(s);
        var cleared=mutation(restored);((ArrayNode)cleared.get("answers")).forEach(a->((ObjectNode)a).putNull("response"));
        assertThat(ok(c.call("PUT",path(s)+"/draft",cleared),200).path("answeredCount").asInt()).isZero();
        var page=ok(c.call("GET","/api/attempts?mode=LEARN&state=IN_PROGRESS&page=0&size=1",null),200);assertThat(page.path("totalElements").asInt()).isEqualTo(1);assertThat(page.path("items").get(0).path("answeredCount").asInt()).isZero();
    }
    @Test void invalidFullSnapshotDoesNotChangeAnyRowsOrWriteReceipt()throws Exception {
        var c=new Client().login(student);var s=start(c);var base=mutation(s);var bad=new ArrayList<ObjectNode>();
        var missing=base.deepCopy();((ArrayNode)missing.get("answers")).remove(9);bad.add(missing);
        var duplicate=base.deepCopy();((ObjectNode)duplicate.path("answers").get(1)).put("attemptQuestionId",s.path("items").get(0).path("attemptQuestionId").asLong());bad.add(duplicate);
        var foreign=base.deepCopy();((ObjectNode)foreign.path("answers").get(0)).put("attemptQuestionId",999999999);bad.add(foreign);
        var option=base.deepCopy();response(option,0,"{\"type\":\"SINGLE_CHOICE\",\"optionId\":\"unknown\"}");bad.add(option);
        var type=base.deepCopy();response(type,0,"{\"type\":\"FILL_BLANK\",\"values\":[]}");bad.add(type);
        var blank=base.deepCopy();response(blank,1,"{\"type\":\"FILL_BLANK\",\"values\":[{\"blankId\":\"foreign\",\"value\":\"x\"}]}");bad.add(blank);
        var pair=base.deepCopy();response(pair,2,"{\"type\":\"MATCHING\",\"pairs\":[{\"leftId\":\"l1\",\"rightId\":\"missing\"}]}");bad.add(pair);
        for(var input:bad){ok(c.call("PUT",path(s)+"/draft",input),400);assertThat(receipts.findByActorIdAndRequestId(student.id(),input.path("requestId").asText())).isEmpty();}
        var restored=ok(c.call("GET",path(s),null),200);assertThat(restored.path("revision").asLong()).isZero();for(var item:restored.path("items"))assertThat(item.path("response").isNull()).isTrue();noTrials(s);
    }
    @Test void invalidPlayerStateIsRejectedAtomically()throws Exception {
        var c=new Client().login(student);var s=start(c);
        for(int kind=0;kind<4;kind++) {
            var m=mutation(s);var p=(ObjectNode)m.get("playerState");
            if(kind==0)p.put("currentAttemptQuestionId",999999999);
            else {var array=p.putArray("audioPositions");array.addObject().put("materialVersionId",kind==1?999999999:materialVersion).put("seconds",kind==2?-1:10);if(kind==3)array.add(array.get(0).deepCopy());}
            ok(c.call("PUT",path(s)+"/draft",m),400);
        }
        assertThat(ok(c.call("GET",path(s),null),200).path("revision").asLong()).isZero();
    }
    @Test void discardedAckReplaysEvenAfterAnotherSaveAdvancedRevision()throws Exception {
        var c=new Client().login(student);var s=start(c);var m=mutation(s);response(m,0,"{\"type\":\"SINGLE_CHOICE\",\"optionId\":\"b\"}");
        // Discard the response body after the server has committed, so the client has no business ACK.
        var lost=c.http.send(c.request("PUT",path(s)+"/draft",m),HttpResponse.BodyHandlers.ofInputStream());assertThat(lost.statusCode()).isEqualTo(200);lost.body().close();
        var one=ok(c.call("GET",path(s),null),200);var newer=mutation(one);response(newer,0,"{\"type\":\"SINGLE_CHOICE\",\"optionId\":\"a\"}");ok(c.call("PUT",path(s)+"/draft",newer),200);
        var replay=c.call("PUT",path(s)+"/draft",m);assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");assertThat(ok(replay,200).path("revision").asLong()).isEqualTo(1);
        var current=ok(c.call("GET",path(s),null),200);assertThat(current.path("revision").asLong()).isEqualTo(2);assertThat(current.path("items").get(0).path("response").path("optionId").asText()).isEqualTo("a");
        var changed=m.deepCopy();response(changed,0,"{\"type\":\"SINGLE_CHOICE\",\"optionId\":\"a\"}");error(c.call("PUT",path(s)+"/draft",changed),409,"REQUEST_ID_REUSED");
        var stale=m.deepCopy().put("requestId",key());error(c.call("PUT",path(s)+"/draft",stale),409,"REVISION_CONFLICT");noTrials(s);
    }
    List<HttpResponse<String>> concurrent(Callable<HttpResponse<String>> a,Callable<HttpResponse<String>> b)throws Exception {
        var barrier=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        try{var fa=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return a.call();});var fb=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return b.call();});return List.of(fa.get(30,TimeUnit.SECONDS),fb.get(30,TimeUnit.SECONDS));}finally{pool.shutdownNow();}
    }
    @Test void twoTabsCannotOverwriteEachOther()throws Exception {
        var c=new Client().login(student);var tab=new Client().login(student);var s=start(c);var a=mutation(s);var b=mutation(s);
        response(a,0,"{\"type\":\"SINGLE_CHOICE\",\"optionId\":\"a\"}");response(b,0,"{\"type\":\"SINGLE_CHOICE\",\"optionId\":\"b\"}");
        var result=concurrent(()->c.call("PUT",path(s)+"/draft",a),()->tab.call("PUT",path(s)+"/draft",b));
        assertThat(result.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(200,409);
        int winner=result.get(0).statusCode()==200?0:1;error(result.get(1-winner),409,"REVISION_CONFLICT");
        var restored=ok(c.call("GET",path(s),null),200);assertThat(restored.path("revision").asLong()).isEqualTo(1);
        assertThat(restored.path("items").get(0).path("response")).isEqualTo((winner==0?a:b).path("answers").get(0).path("response"));
    }
    @Test void concurrentDuplicateSaveAndCreateDoNotDuplicateEffects()throws Exception {
        var c=new Client().login(student);var tab=new Client().login(student);var command=learn();
        var created=concurrent(()->c.call("POST","/api/attempts",command),()->tab.call("POST","/api/attempts",command));
        var s=ok(created.get(0),201);assertThat(ok(created.get(1),201)).isEqualTo(s);
        var m=mutation(s);var saved=concurrent(()->c.call("PUT",path(s)+"/draft",m),()->tab.call("PUT",path(s)+"/draft",m));
        assertThat(ok(saved.get(0),200)).isEqualTo(ok(saved.get(1),200));
        assertThat(ok(c.call("GET",path(s),null),200).path("revision").asLong()).isEqualTo(1);
        tx.executeWithoutResult(t->assertThat(attempts.active(student.id(),Mode.LEARN)).hasSize(1));noTrials(s);
    }
    @Test void concurrentDifferentCreateKeysResumeOneAttempt()throws Exception {
        var a=new Client().login(student);var b=new Client().login(student);
        var created=concurrent(()->a.call("POST","/api/attempts",learn()),()->b.call("POST","/api/attempts",learn()));
        assertThat(created.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(200,201);
        assertThat(body(created.get(0)).path("attemptId")).isEqualTo(body(created.get(1)).path("attemptId"));
        tx.executeWithoutResult(t->assertThat(attempts.active(student.id(),Mode.LEARN)).hasSize(1));
    }
    @Test void practiceDoesNotRepeatCopiedOriginsAcrossLessons()throws Exception {
        tx.executeWithoutResult(t->{
            var source=em.find(QuestionVersionEntity.class,questionVersions.get(5));
            var original=em.find(QuestionEntity.class,source.getQuestionId());
            var copy=new QuestionEntity(teacher.id(),original.getOriginKey());em.persist(copy);em.flush();
            var v=new QuestionVersionEntity(copy.getId(),1,source.data());em.persist(v);em.flush();copy.initialDraft(v);copy.publish();
            var originalLesson=em.find(LessonEntity.class,lesson);
            var l=new LessonEntity(teacher.id(),originalLesson.getUnitId(),1);em.persist(l);em.flush();
            var lv=new LessonVersionEntity(l.getId(),1,new Lesson("Copy lesson","",node("[{\"kind\":\"TEXT\",\"text\":\"Read\"}]"),List.of(source.data().objectiveId()),
                List.of(new Slot(questionVersions.get(0),"GUIDED",0),new Slot(v.getId(),"INDEPENDENT",0),new Slot(questionVersions.get(10),"REVIEW_VARIANT",0))));
            em.persist(lv);em.flush();l.initialDraft(lv);l.publish();em.flush();
        });
        var c=new Client().login(student);var s=ok(c.call("POST","/api/attempts",practice(10)),201);
        assertThat(s.path("items")).hasSize(5);var origins=new HashSet<String>();s.path("items").forEach(i->assertThat(origins.add(i.path("originKey").asText())).isTrue());
    }
    @Test void databaseFailureRollsBackAnswersPlayerRevisionAndReceipt()throws Exception {
        var c=new Client().login(student);var s=start(c);var m=mutation(s);response(m,0,"{\"type\":\"SINGLE_CHOICE\",\"optionId\":\"a\"}");
        String requestId=m.path("requestId").asText();
        tx.executeWithoutResult(t->em.createNativeQuery("ALTER TABLE request_receipts ADD CONSTRAINT m03_reject_receipt CHECK (request_id <> '"+requestId+"')").executeUpdate());
        try{error(c.call("PUT",path(s)+"/draft",m),503,"DATABASE_UNAVAILABLE");}finally{tx.executeWithoutResult(t->em.createNativeQuery("ALTER TABLE request_receipts DROP CHECK m03_reject_receipt").executeUpdate());}
        assertThat(receipts.findByActorIdAndRequestId(student.id(),requestId)).isEmpty();var restored=ok(c.call("GET",path(s),null),200);
        assertThat(restored.path("revision").asLong()).isZero();assertThat(restored.path("playerState")).isEqualTo(s.path("playerState"));
        for(var item:restored.path("items"))assertThat(item.path("response").isNull()).isTrue();ok(c.call("PUT",path(s)+"/draft",m),200);noTrials(s);
    }
    @Test void permissionsAndCsrfProtectPrivateAttempts()throws Exception {
        var c=new Client().login(student);var s=start(c);var otherClient=new Client().login(other);var m=mutation(s);
        error(otherClient.call("GET",path(s),null),404,"NOT_FOUND");error(otherClient.call("PUT",path(s)+"/draft",m),404,"NOT_FOUND");
        for(var user:List.of(teacher,admin)){var privileged=new Client().login(user);ok(privileged.call("GET",path(s),null),403);ok(privileged.call("POST","/api/attempts",learn()),403);ok(privileged.call("PUT",path(s)+"/draft",m),403);}
        c.csrf=null;error(c.call("PUT",path(s)+"/draft",m),403,"CSRF_INVALID");ok(new Client().call("GET",path(s),null),401);
        assertThat(ok(otherClient.call("GET","/api/attempts",null),200).path("totalElements").asInt()).isZero();
        ok(otherClient.call("GET","/api/attempts?mode=INVALID",null),400);ok(otherClient.call("GET","/api/attempts?size=51",null),400);
    }
    @Test void accountLockedWhileSaveWaitsCannotCommitDraft()throws Exception {
        var c=new Client().login(student);var s=start(c);var m=mutation(s);var entered=new CountDownLatch(1);var locked=new CountDownLatch(1);
        doAnswer(invocation->{if(((UserPrincipal)invocation.getArgument(0)).id()==student.id())entered.countDown();return invocation.callRealMethod();}).when(guard).lockCurrent(any(UserPrincipal.class));
        var pool=Executors.newFixedThreadPool(2);
        try {
            var lock=pool.submit(()->tx.execute(t->{var user=userEntities.lockById(student.id()).orElseThrow();locked.countDown();try{if(!entered.await(15,TimeUnit.SECONDS))throw new AssertionError("Save did not reach actor lock");}catch(InterruptedException e){throw new RuntimeException(e);}user.changeStatus(AccountStatus.LOCKED);userEntities.saveAndFlush(user);return null;}));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();var save=pool.submit(()->c.call("PUT",path(s)+"/draft",m));lock.get(25,TimeUnit.SECONDS);ok(save.get(25,TimeUnit.SECONDS),401);
            assertThat(receipts.findByActorIdAndRequestId(student.id(),m.path("requestId").asText())).isEmpty();
            tx.executeWithoutResult(t->assertThat(attempts.attempt(s.path("attemptId").asLong(),student.id(),false).orElseThrow().getRevision()).isZero());
        } finally{pool.shutdownNow();}
    }
    @Test void historicalVersionsAndMediaRemainReadableAfterRetirementAndSubmission()throws Exception {
        var c=new Client().login(student);var s=start(c);var others=new Client().login(other);
        var rangeRequest=c.request("GET","/api/assets/"+asset,null);rangeRequest=HttpRequest.newBuilder(rangeRequest,(name,value)->true).header("Range","bytes=0-15").build();
        var range=c.http.send(rangeRequest,HttpResponse.BodyHandlers.ofByteArray());assertThat(range.statusCode()).isEqualTo(206);assertThat(range.body()).hasSize(16);
        ok(others.call("GET","/api/assets/"+asset,null),404);
        tx.executeWithoutResult(t->{
            var root=em.find(LessonEntity.class,lesson);root.retire();em.find(MaterialEntity.class,materialRoot).retire();
            var version=em.find(QuestionVersionEntity.class,questionVersions.get(0));em.find(QuestionEntity.class,version.getQuestionId()).retire();
        });
        assertThat(ok(c.call("GET",path(s),null),200)).isEqualTo(s);assertThat(ok(c.call("POST","/api/attempts",learn()),200).path("attemptId")).isEqualTo(s.path("attemptId"));
        ok(others.call("POST","/api/attempts",learn()),404);assertThat(c.call("GET","/api/assets/"+asset,null).statusCode()).isEqualTo(200);
        var saved=mutation(s);ok(c.call("PUT",path(s)+"/draft",saved),200);
        tx.executeWithoutResult(t->{var a=attempts.attempt(s.path("attemptId").asLong(),student.id(),true).orElseThrow();a.submit(Instant.now());a.changed();attempts.save(a);});
        assertThat(c.call("GET","/api/assets/"+asset,null).statusCode()).isEqualTo(200);var submitted=ok(c.call("GET",path(s),null),200);assertThat(submitted.toString()).doesNotContain("answerKey","SECRET_");
        var replay=c.call("PUT",path(s)+"/draft",saved);assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");assertThat(ok(replay,200).path("revision").asLong()).isEqualTo(1);
        error(c.call("PUT",path(s)+"/draft",mutation(submitted)),409,"ATTEMPT_FINISHED");
    }
    @Test void aSubmittedAttemptAllowsNewLearnWithPreviousExposureFlags()throws Exception {
        var c=new Client().login(student);var first=start(c);
        tx.executeWithoutResult(t->{var a=attempts.attempt(first.path("attemptId").asLong(),student.id(),true).orElseThrow();a.submit(Instant.now());a.changed();attempts.save(a);});
        var next=start(c);assertThat(next.path("attemptId")).isNotEqualTo(first.path("attemptId"));
        for(var item:next.path("items"))assertThat(item.path("wasSeenBefore").asBoolean()).isTrue();
    }
    @Test void practicePrioritizesFewestThenOldestExposures()throws Exception {
        var c=new Client().login(student);var all=ok(c.call("POST","/api/attempts",practice(5)),201);
        var origins=new ArrayList<String>();all.path("items").forEach(i->origins.add(i.path("originKey").asText()));
        tx.executeWithoutResult(t->{for(int i=0;i<origins.size();i++)em.createNativeQuery("UPDATE question_exposures SET seen_count=:count,last_seen_at=:time WHERE user_id=:user AND origin_key=:origin")
            .setParameter("count",i<2?1:3).setParameter("time",i==0?"2026-01-02 00:00:00":i==1?"2026-01-03 00:00:00":"2026-01-01 00:00:00")
            .setParameter("user",student.id()).setParameter("origin",origins.get(i)).executeUpdate();});
        var next=ok(c.call("POST","/api/attempts",practice(1)),201);
        assertThat(next.path("items").get(0).path("originKey").asText()).isEqualTo(origins.get(0));
    }
    @Test void republishingContentDoesNotReplacePinnedVersionsOrGrantNewAudio()throws Exception {
        var c=new Client().login(student);var before=start(c);String newFile=key();Files.write(FILES.resolve(newFile),new byte[64]);
        long newAsset=tx.execute(t->{
            var a=new AssetEntity(teacher.id(),newFile,"new.wav","audio/wav",64,"1".repeat(64));em.persist(a);em.flush();
            var m=em.find(MaterialEntity.class,materialRoot);
            var mv=new MaterialVersionEntity(materialRoot,2,new MaterialData("AUDIO","New audio",null,a.getId(),"NEW_SECRET_TRANSCRIPT",null,null,java.math.BigDecimal.ZERO,null,new MaterialData.Source("Test",null,null)));
            em.persist(mv);em.flush();m.startDraft(mv);m.publish();
            var old=em.find(QuestionVersionEntity.class,questionVersions.get(0));var d=old.data();var q=em.find(QuestionEntity.class,old.getQuestionId());
            var qv=new QuestionVersionEntity(q.getId(),2,new Question(d.type(),d.topicId(),d.objectiveId(),d.difficulty(),"Updated prompt",d.content(),d.answerKey(),d.explanation(),d.hint(),mv.getId(),d.source()));
            em.persist(qv);em.flush();q.startDraft(qv);q.publish();
            var root=em.find(LessonEntity.class,lesson);var previous=em.find(LessonVersionEntity.class,lessonVersion).data();
            var slots=previous.questions().stream().map(s->s.questionVersionId()==old.getId()?new Slot(qv.getId(),s.practiceRole(),s.position()):s).toList();
            var lv=new LessonVersionEntity(lesson,2,new Lesson("Updated lesson",previous.description(),previous.instructions(),previous.objectiveIds(),slots));
            em.persist(lv);em.flush();root.startDraft(lv);root.publish();em.flush();return a.getId();
        });
        assertThat(ok(c.call("GET",path(before),null),200)).isEqualTo(before);
        assertThat(ok(c.call("POST","/api/attempts",learn()),200).path("lessonVersionId")).isEqualTo(before.path("lessonVersionId"));
        var newer=new Client().login(other);var after=start(newer);
        assertThat(after.path("lessonVersionId")).isNotEqualTo(before.path("lessonVersionId"));assertThat(after.path("items").get(0).path("question").path("prompt").asText()).isEqualTo("Updated prompt");
        ok(c.call("GET","/api/assets/"+newAsset,null),404);assertThat(newer.call("GET","/api/assets/"+newAsset,null).statusCode()).isEqualTo(200);
        ok(c.call("PUT",path(before)+"/draft",mutation(before)),200);
    }
}
