package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import jakarta.persistence.EntityManager;
import java.net.*;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.dto.AuthoringData.*;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.service.GamificationService;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"app.demo.enabled=false","app.bootstrap-admin.enabled=false","app.gamification.worker-enabled=false"})
@Import(GamificationFixture.TimeConfig.class) @Testcontainers
abstract class GamificationFixture {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("m05_rewards_test");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->MYSQL.getJdbcUrl()+"?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        r.add("spring.datasource.username",MYSQL::getUsername); r.add("spring.datasource.password",MYSQL::getPassword);
    }
    static class TestClock extends Clock {
        final AtomicReference<Instant> current=new AtomicReference<>();
        public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return current.get();} void set(String at){current.set(Instant.parse(at));}
    }
    @TestConfiguration static class TimeConfig {
        @Bean @Primary TestClock rewardTestClock(){return new TestClock();}
        // These scenarios advance business dates by days/weeks, not real user inactivity.
        @Bean @Primary vn.edu.ptit.english.security.SessionActivityRegistry rewardSessionActivity() {
            return new vn.edu.ptit.english.security.SessionActivityRegistry(Clock.systemUTC(),300,30);
        }
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired TestClock clock;
    @Autowired GamificationService service;
    @MockitoSpyBean GamificationRepository rewards;
    TransactionTemplate tx;
    UserRecord alice,bob,teacher;
    long topic,objective,unit,lesson;
    static String hash;
    static final String PASSWORD="Reward@Test123";
    @BeforeEach void fixture() {
        tx=new TransactionTemplate(transactions); clock.set("2026-10-21T12:00:00Z");
        if(hash==null) hash=passwords.encode(PASSWORD); String prefix=UUID.randomUUID().toString();
        tx.executeWithoutResult(s->{
            // Keep the published journey isolated from earlier test fixtures.
            em.createQuery("select l from LessonEntity l where l.retiredAt is null",LessonEntity.class).getResultList().forEach(LessonEntity::retire);
            em.createNativeQuery("UPDATE gamification_policies SET activated_at='2026-10-01 00:00:00',effective_at='2026-10-01 00:00:00'").executeUpdate();
            alice=users.create(prefix+"a@rewards.test",hash,"Alice",Role.STUDENT);
            bob=users.create(prefix+"b@rewards.test",hash,"Bob",Role.STUDENT);
            teacher=users.create(prefix+"t@rewards.test",hash,"Teacher",Role.TEACHER);
            var t=new TopicEntity(teacher.id(),prefix,"Topic",""); em.persist(t); em.flush(); topic=t.getId();
            var o=new ObjectiveEntity(teacher.id(),topic,prefix+"o","Objective",""); em.persist(o); em.flush(); objective=o.getId();
            var u=new UnitEntity(teacher.id(),topic,"Unit","",1,0,((Number)em.createQuery("select coalesce(max(u.roundNumber),0) from UnitEntity u").getSingleResult()).intValue()+1); em.persist(u); em.flush(); unit=u.getId();
            lesson=bank(6);
        });
    }
    JsonNode node(String value){try{return json.readTree(value);}catch(Exception ex){throw new IllegalArgumentException(ex);}}
    String key(){return UUID.randomUUID().toString();}
    long bank(int count) {
        var slots=new ArrayList<Slot>();
        for(int i=0;i<count+4;i++) {
            var root=new QuestionEntity(teacher.id(),key()); em.persist(root); em.flush();
            var q=new Question("SINGLE_CHOICE",topic,objective,"EASY","Fixture question "+i,
                node("{\"options\":[{\"id\":\"yes\",\"text\":\"Yes\"},{\"id\":\"no\",\"text\":\"No\"}]}"),
                node("{\"correctOptionId\":\"yes\"}"),"Explanation","Hint",null,new MaterialData.Source("Test",null,null));
            var v=new QuestionVersionEntity(root.getId(),1,q); em.persist(v); em.flush(); root.initialDraft(v); root.publish();
            slots.add(new Slot(v.getId(),i==0?"GUIDED":i<=count?"INDEPENDENT":"REVIEW_VARIANT",i==0?0:i<=count?i-1:i-count-1));
        }
        var root=new LessonEntity(teacher.id(),unit,0); em.persist(root); em.flush();
        var v=new LessonVersionEntity(root.getId(),1,new Lesson("Test lesson","",node("[{\"kind\":\"TEXT\",\"text\":\"Read\"}]"),List.of(objective),slots));
        em.persist(v); em.flush(); root.initialDraft(v); root.publish(); return root.getId();
    }
    final class Client {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build(); String csrf;
        HttpResponse<String> call(String method,String path,Object data)throws Exception {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(30));
            if(!method.equals("GET")&&csrf!=null) request.header("X-CSRF-TOKEN",csrf);
            if(data!=null) request.header("Content-Type","application/json");
            return http.send(request.method(method,data==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data))).build(),HttpResponse.BodyHandlers.ofString());
        }
        Client login(UserRecord user)throws Exception { csrf=ok(call("GET","/api/auth/csrf",null),200).path("token").asText();
            ok(call("POST","/api/auth/login",Map.of("email",user.email(),"password",PASSWORD)),200);
            csrf=ok(call("GET","/api/auth/csrf",null),200).path("token").asText(); return this; }
    }
    JsonNode ok(HttpResponse<String> response,int status)throws Exception { assertThat(response.statusCode()).as(response.body()).isEqualTo(status); return json.readTree(response.body()); }
    JsonNode practice(Client c,int count)throws Exception { return ok(c.call("POST","/api/attempts",Map.of("requestId",key(),"mode","PRACTICE","topicId",topic,"difficulty","EASY","count",count)),201); }
    JsonNode submit(Client c,JsonNode snapshot,String option)throws Exception {
        var command=json.createObjectNode().put("requestId",key()).put("baseRevision",snapshot.path("revision").asLong());
        command.set("playerState",snapshot.path("playerState")); var answers=command.putArray("answers");
        snapshot.path("items").forEach(i->{var entry=answers.addObject().put("attemptQuestionId",i.path("attemptQuestionId").asLong());
            if(option==null) entry.putNull("response"); else entry.set("response",node("{\"type\":\"SINGLE_CHOICE\",\"optionId\":\""+option+"\"}"));});
        return ok(c.call("POST","/api/attempts/"+snapshot.path("attemptId").asLong()+"/submit",command),200);
    }
    JsonNode sync(Client c)throws Exception {return ok(c.call("POST","/api/me/gamification/sync",Map.of("requestId",key())),200);}
    JsonNode summary(Client c)throws Exception {return ok(c.call("GET","/api/me/gamification",null),200);}
    long count(String table){return tx.execute(t->((Number)em.createNativeQuery("SELECT COUNT(*) FROM "+table+" WHERE user_id=:u").setParameter("u",alice.id()).getSingleResult()).longValue());}

}
