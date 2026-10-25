package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import vn.edu.ptit.english.bootstrap.CatalogDemoInitializer;
import vn.edu.ptit.english.dto.UserRecord;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.security.AccountGuard;
import static org.assertj.core.api.Assertions.*;

@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"app.demo.enabled=false","app.bootstrap-admin.enabled=false"})
@Testcontainers
class CatalogApiTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("english_catalog_test").withUsername("test_app").withPassword("test-password");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",() -> MYSQL.getJdbcUrl()+"?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        registry.add("spring.datasource.username",MYSQL::getUsername);
        registry.add("spring.datasource.password",MYSQL::getPassword);
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired UserJpaRepository userEntities;
    @Autowired TopicJpaRepository topics;
    @Autowired ObjectiveJpaRepository objectives;
    @Autowired UnitJpaRepository units;
    @Autowired RequestReceiptJpaRepository receipts;
    @Autowired PasswordEncoder passwords;
    @Autowired AccountGuard guard;
    @Autowired PlatformTransactionManager transactions;
    UserRecord author, other, admin, learner;
    @BeforeEach void fixtures() {
        receipts.deleteAllInBatch(); units.deleteAllInBatch(); objectives.deleteAllInBatch(); topics.deleteAllInBatch(); userEntities.deleteAllInBatch();
        String hash = passwords.encode("TestPass@123");
        author=users.create("author@test.local",hash,"Tác giả Một",Role.TEACHER);
        other=users.create("other@test.local",hash,"Tác giả Hai",Role.TEACHER);
        admin=users.create("admin@test.local",hash,"Quản trị viên",Role.ADMIN);
        learner=users.create("learner@test.local",hash,"Người học",Role.STUDENT);
    }
    final class Client {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        String token;
        HttpResponse<String> request(String method,String path,Object payload,boolean csrf) throws Exception {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20));
            if(csrf)request.header("X-CSRF-TOKEN",token);
            if(payload!=null)request.header("Content-Type","application/json");
            request.method(method,payload==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)));
            return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> send(String method,String path,Object payload) throws Exception {return request(method,path,payload,!method.equals("GET"));}
        void csrf() throws Exception {token=body(request("GET","/api/auth/csrf",null,false)).path("token").asText();}
        Client login(UserRecord user) throws Exception {
            csrf(); assertThat(send("POST","/api/auth/login",Map.of("email",user.email(),"password","TestPass@123")).statusCode()).isEqualTo(200);
            csrf();return this;
        }
    }
    JsonNode body(HttpResponse<String> response) throws Exception {return json.readTree(response.body());}
    String key(){return UUID.randomUUID().toString();}
    Map<String,Object> topic(String code){return Map.of("requestId",key(),"code",code,"name","Sinh hoạt hằng ngày","description","Nội dung tiếng Việt");}
    long createTopic(Client client,String code) throws Exception {
        var response=client.send("POST","/api/author/topics",topic(code));assertThat(response.statusCode()).isEqualTo(201);return body(response).path("id").asLong();
    }
    Map<String,Object> unit(long topicId,String title,int stage,int order){return Map.of("requestId",key(),"topicId",topicId,"title",title,"description","Mô tả","stage",stage,"sortOrder",order);}
    Map<String,Object> edit(String requestId,long revision,String name){return Map.of("requestId",requestId,"baseRevision",revision,"name",name,"description","Đã chỉnh sửa");}

    @Test void authenticationRolesCsrfAndStrictInputAreEnforced() throws Exception {
        assertThat(new Client().request("GET","/api/author/topics",null,false).statusCode()).isEqualTo(401);
        var student=new Client().login(learner);
        for(String resource:List.of("topics","objectives","units"))assertThat(student.send("GET","/api/author/"+resource,null).statusCode()).isEqualTo(403);
        var client=new Client().login(author);
        assertThat(client.request("POST","/api/author/topics",topic("test-topic"),false).statusCode()).isEqualTo(403);
        var forged=new HashMap<>(topic("test-topic"));forged.put("ownerId",other.id());
        assertThat(client.send("POST","/api/author/topics",forged).statusCode()).isEqualTo(400);
        var missing=new HashMap<>(topic("test-topic"));missing.remove("description");
        assertThat(client.send("POST","/api/author/topics",missing).statusCode()).isEqualTo(400);
        assertThat(client.send("POST","/api/author/topics",topic("BAD CODE")).statusCode()).isEqualTo(400);
        var fractional=new HashMap<>(unit(9007199254740991L,"Unit",1,0));fractional.put("stage",1.5);
        assertThat(client.send("POST","/api/author/units",fractional).statusCode()).isEqualTo(400);
        for(String query:List.of("size=51","page=-1","scope=PUBLISHED","topicId=0")) {
            String resource=query.startsWith("topicId")?"units":"topics";
            assertThat(client.send("GET","/api/author/"+resource+"?"+query,null).statusCode()).isEqualTo(400);
        }
        assertThat(topics.count()).isZero();assertThat(receipts.count()).isZero();
    }
    @Test void sharedCategoriesKeepOwnershipAndAdminCanEditWithoutTakingOwnership() throws Exception {
        var one=new Client().login(author);var two=new Client().login(other);var manager=new Client().login(admin);
        long id=createTopic(one,"shared-topic");
        assertThat(body(two.send("GET","/api/author/topics",null)).path("totalElements").asLong()).isZero();
        var shared=two.send("GET","/api/author/topics?scope=ALL",null);
        assertThat(body(shared).path("totalElements").asLong()).isEqualTo(1);
        assertThat(shared.headers().firstValue("Cache-Control").orElse("")).contains("no-store","private");
        assertThat(two.send("PUT","/api/author/topics/"+id,edit(key(),0,"Không được sửa")).statusCode()).isEqualTo(404);
        var updated=manager.send("PUT","/api/author/topics/"+id,edit(key(),0,"Quản trị sửa"));
        assertThat(updated.statusCode()).isEqualTo(200);assertThat(body(updated).path("ownerId").asLong()).isEqualTo(author.id());
        assertThat(two.send("GET","/api/author/units?scope=ALL",null).statusCode()).isEqualTo(403);
    }
    @Test void retriesReplayCommittedBodyBeforeCheckingOldRevisionAndRejectKeyReuse() throws Exception {
        var client=new Client().login(author);var create=topic("retry-topic");
        var first=client.send("POST","/api/author/topics",create);long id=body(first).path("id").asLong();
        var replay=client.send("POST","/api/author/topics",create);
        assertThat(replay.statusCode()).isEqualTo(201);assertThat(body(replay)).isEqualTo(body(first));
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        var changed=new HashMap<>(create);changed.put("name","Payload khác");
        var rejected=client.send("POST","/api/author/topics",changed);
        assertThat(body(rejected).path("code").asText()).isEqualTo("REQUEST_ID_REUSED");
        var edit=edit(key(),0,"Bản một");var v1=client.send("PUT","/api/author/topics/"+id,edit);
        assertThat(v1.statusCode()).isEqualTo(200);
        assertThat(client.send("PUT","/api/author/topics/"+id,edit(key(),1,"Bản hai")).statusCode()).isEqualTo(200);
        assertThat(body(client.send("PUT","/api/author/topics/"+id,edit))).isEqualTo(body(v1));
        assertThat(topics.findById(id).orElseThrow().view().name()).isEqualTo("Bản hai");
        assertThat(topics.count()).isEqualTo(1);assertThat(receipts.count()).isEqualTo(3);
    }
    @Test void objectiveAndUnitSupportFilteringOrderingAndImmutableCodes() throws Exception {
        var client=new Client().login(author);long topicId=createTopic(client,"daily");
        var objective=Map.of("requestId",key(),"topicId",topicId,"code","daily-verbs","name","Các động từ hằng ngày","description","");
        var created=client.send("POST","/api/author/objectives",objective);assertThat(created.statusCode()).isEqualTo(201);
        long objectiveId=body(created).path("id").asLong();
        assertThat(client.send("POST","/api/author/units",unit(topicId,"Muộn",2,0)).statusCode()).isEqualTo(201);
        assertThat(client.send("POST","/api/author/units",unit(topicId,"Thứ hai",1,1)).statusCode()).isEqualTo(201);
        assertThat(client.send("POST","/api/author/units",unit(topicId,"Đầu tiên",1,0)).statusCode()).isEqualTo(201);
        var list=body(client.send("GET","/api/author/units?topicId="+topicId+"&size=2",null));
        assertThat(list.path("items").get(0).path("title").asText()).isEqualTo("Đầu tiên");
        assertThat(list.path("totalPages").asInt()).isEqualTo(2);
        assertThat(list.path("items").get(0).path("objectiveIds").isEmpty()).isTrue();
        assertThat(body(client.send("GET","/api/author/topics?q=%25",null)).path("totalElements").asLong()).isZero();
        var forbidden=new HashMap<>(edit(key(),0,"Tên mới"));forbidden.put("code","changed-code");
        assertThat(client.send("PUT","/api/author/objectives/"+objectiveId,forbidden).statusCode()).isEqualTo(400);
        var updated=client.send("PUT","/api/author/objectives/"+objectiveId,edit(key(),0,"Động từ thường dùng"));
        assertThat(body(updated).path("code").asText()).isEqualTo("daily-verbs");
        assertThat(body(updated).path("revision").asLong()).isEqualTo(1);
    }
    @Test void invalidReferencesAndDuplicateCodesRollBackReceipts() throws Exception {
        var client=new Client().login(author);long id=createTopic(client,"unique-topic");
        var duplicate=client.send("POST","/api/author/topics",topic("unique-topic"));assertThat(duplicate.statusCode()).isEqualTo(409);
        var command=new HashMap<>(unit(9007199254740991L,"Unit",1,0));
        assertThat(client.send("POST","/api/author/units",command).statusCode()).isEqualTo(404);
        assertThat(units.count()).isZero();assertThat(receipts.count()).isEqualTo(1);
        command.put("topicId",id);
        assertThat(client.send("POST","/api/author/units",command).statusCode()).isEqualTo(201);
        assertThat(receipts.count()).isEqualTo(2);
    }
    @Test void concurrentCreateAndEditsDoNotDuplicateOrOverwrite() throws Exception {
        var one=new Client().login(author);var two=new Client().login(author);var manager=new Client().login(admin);
        var command=topic("concurrent-topic");
        var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(() -> one.send("POST","/api/author/topics",command));
            var b=pool.submit(() -> two.send("POST","/api/author/topics",command));
            var first=a.get(30,TimeUnit.SECONDS);var second=b.get(30,TimeUnit.SECONDS);
            assertThat(first.statusCode()).isEqualTo(201);assertThat(second.statusCode()).isEqualTo(201);
            assertThat(body(first).path("id")).isEqualTo(body(second).path("id"));assertThat(topics.count()).isEqualTo(1);
            long id=body(first).path("id").asLong();
            var editA=pool.submit(() -> one.send("PUT","/api/author/topics/"+id,edit(key(),0,"Từ tab một")));
            var editB=pool.submit(() -> manager.send("PUT","/api/author/topics/"+id,edit(key(),0,"Từ tab hai")));
            assertThat(List.of(editA.get(30,TimeUnit.SECONDS).statusCode(),editB.get(30,TimeUnit.SECONDS).statusCode()))
                .containsExactlyInAnyOrder(200,409);
            assertThat(topics.findById(id).orElseThrow().getRevision()).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test void otherAuthorCannotEditObjectiveOrUnitAndUnitCanChangeTopicWhileEmpty() throws Exception {
        var one=new Client().login(author);var two=new Client().login(other);
        long first=createTopic(one,"first-topic"), second=createTopic(one,"second-topic");
        var created=one.send("POST","/api/author/units",unit(first,"Unit đầu",1,0));
        long id=body(created).path("id").asLong();var update=new HashMap<>(unit(second,"Unit sửa",1,1));update.put("baseRevision",0);
        assertThat(two.send("PUT","/api/author/units/"+id,update).statusCode()).isEqualTo(404);
        assertThat(one.send("PUT","/api/author/units/"+id,update).statusCode()).isEqualTo(200);
        assertThat(units.findById(id).orElseThrow().getTopicId()).isEqualTo(second);
        var objective=one.send("POST","/api/author/objectives",Map.of("requestId",key(),"topicId",first,"code","first-objective","name","Mục tiêu","description",""));
        long objectiveId=body(objective).path("id").asLong();
        assertThat(two.send("PUT","/api/author/objectives/"+objectiveId,edit(key(),0,"Sửa trái quyền")).statusCode()).isEqualTo(404);
    }
    @Test void demoCatalogCanRunAgainWithoutDuplicatingOrResettingEdits() throws Exception {
        var initializer=new CatalogDemoInitializer(users,topics,objectives,units,guard,author.email());
        var tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> initializer.run(new DefaultApplicationArguments()));
        var unit=units.findBySeedKey("m02-catalog-daily-routines").orElseThrow();
        tx.executeWithoutResult(status -> {var item=units.findById(unit.getId()).orElseThrow();item.update(item.getTopicId(),"Tên đã sửa","Giữ lại",1,0);units.save(item);});
        tx.executeWithoutResult(status -> initializer.run(new DefaultApplicationArguments()));
        assertThat(topics.count()).isEqualTo(3);assertThat(objectives.count()).isEqualTo(3);assertThat(units.count()).isEqualTo(3);
        assertThat(units.findById(unit.getId()).orElseThrow().view().title()).isEqualTo("Tên đã sửa");
    }
}
