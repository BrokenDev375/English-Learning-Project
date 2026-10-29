package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import vn.edu.ptit.english.repository.UserRepository;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.security.SessionActivityRegistry;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"app.demo.enabled=false","app.bootstrap-admin.enabled=false"})
@Import(SessionActivityApiTest.TimeConfig.class)
@org.springframework.test.annotation.DirtiesContext
@Testcontainers
class SessionActivityApiTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("session_test").withUsername("test_app").withPassword("test-password");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);}
    @TestConfiguration static class TimeConfig {@Bean @Primary LiveConnectionsTest.Time activityTime(){return new LiveConnectionsTest.Time();}}
    @Autowired LiveConnectionsTest.Time time;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired SessionActivityRegistry registry;
    @Autowired vn.edu.ptit.english.security.LiveConnections live;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @LocalServerPort int port;
    String email;
    @BeforeEach void create(){time.time=Instant.now();email=UUID.randomUUID()+"@test.local";users.create(email,passwords.encode("TestPass@123"),"Idle test",Role.STUDENT);}
    class Client {
        CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        HttpClient http=HttpClient.newBuilder().cookieHandler(cookies).build();String csrf;
        HttpResponse<String> call(String method,String path,Object body)throws Exception{
            var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(15));
            if(body!=null)b.header("Content-Type","application/json");if(csrf!=null)b.header("X-CSRF-TOKEN",csrf);
            return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        void token()throws Exception{csrf=json.readTree(call("GET","/api/auth/csrf",null).body()).path("token").asText();}
        void login()throws Exception{login(email);}
        void login(String address)throws Exception{token();assertThat(call("POST","/api/auth/login",Map.of("email",address,"password","TestPass@123")).statusCode()).isEqualTo(200);token();}
    }
    Map<String,Object> report(String tab,long seq,boolean active){return Map.of("tabId",tab,"seq",seq,"activity","HOME","visibility","VISIBLE","interaction",active);}
    @Test void authenticatedSessionAndContextHavePrivateNoStoreResponsesAndStrictInput()throws Exception{
        var c=new Client();assertThat(c.call("GET","/api/auth/session",null).statusCode()).isEqualTo(401);c.login();
        var state=c.call("GET","/api/auth/session",null);assertThat(state.statusCode()).isEqualTo(200);assertThat(state.headers().firstValue("Cache-Control").orElse("")).contains("no-store","private");
        assertThat(state.body()).doesNotContain("JSESSIONID","securityVersion","password");
        assertThat(c.call("POST","/api/auth/activity",report(UUID.randomUUID().toString(),0,false)).statusCode()).isEqualTo(200);
        assertThat(c.call("POST","/api/auth/activity",Map.of("userId",123)).statusCode()).isEqualTo(400);
        c.csrf=null;assertThat(c.call("POST","/api/auth/activity",report(UUID.randomUUID().toString(),0,false)).statusCode()).isEqualTo(403);
    }
    @Test void backgroundRequestsDoNotRenewAndExpiredCookieCanLoginAgain()throws Exception{
        var c=new Client();c.login();var first=json.readTree(c.call("GET","/api/auth/session",null).body());
        var deadline=Instant.parse(first.path("idleExpiresAt").asText());
        time.time=deadline.minusMillis(1);assertThat(c.call("GET","/api/auth/me",null).statusCode()).isEqualTo(200);
        assertThat(json.readTree(c.call("GET","/api/auth/session",null).body()).get("idleExpiresAt")).isEqualTo(first.get("idleExpiresAt"));
        time.time=deadline;var expired=c.call("GET","/api/auth/me",null);assertThat(expired.statusCode()).withFailMessage(expired.body()).isEqualTo(401);assertThat(expired.body()).contains("SESSION_IDLE_TIMEOUT");
        assertThat(c.call("POST","/api/auth/activity",report(UUID.randomUUID().toString(),0,false)).statusCode()).isEqualTo(401);
        c.login();assertThat(c.call("GET","/api/auth/session",null).statusCode()).isEqualTo(200);
    }
    @Test void oneDeviceInteractionDoesNotRenewAnotherDevice()throws Exception{
        var a=new Client();a.login();var b=new Client();b.login();String tab=UUID.randomUUID().toString();a.call("POST","/api/auth/activity",report(tab,0,false));
        time.advance(60000);assertThat(a.call("POST","/api/auth/activity",report(tab,1,true)).statusCode()).isEqualTo(200);
        time.advance(240000);registry.sweep();assertThat(a.call("GET","/api/auth/session",null).statusCode()).isEqualTo(200);assertThat(b.call("GET","/api/auth/me",null).statusCode()).isEqualTo(401);
    }
    @Test void serverIdleSweepClosesAnAlreadyAuthenticatedSocket()throws Exception{
        var c=new Client();c.login();var close=new java.util.concurrent.CompletableFuture<String>();
        var socket=c.http.newWebSocketBuilder().header("Origin","http://localhost:5173").buildAsync(URI.create("ws://127.0.0.1:"+port+"/ws/events"),new WebSocket.Listener(){
            public void onOpen(WebSocket s){s.request(1);}
            public java.util.concurrent.CompletionStage<?> onText(WebSocket s,CharSequence text,boolean last){s.request(1);return null;}
            public java.util.concurrent.CompletionStage<?> onClose(WebSocket s,int code,String reason){close.complete(code+":"+reason);return null;}
        }).get(10,java.util.concurrent.TimeUnit.SECONDS);
        var state=json.readTree(c.call("GET","/api/auth/session",null).body());time.time=Instant.parse(state.path("idleExpiresAt").asText());registry.sweep();
        assertThat(close.get(10,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("1008:IDLE_TIMEOUT");
        assertThat(c.call("GET","/api/auth/me",null).statusCode()).isEqualTo(401);socket.abort();
    }
    Client admin()throws Exception{
        String address=UUID.randomUUID()+"@admin.test";users.create(address,passwords.encode("TestPass@123"),"Session admin",Role.ADMIN);
        var c=new Client();c.login(address);return c;
    }
    @Test void monitoringEndpointsRequireCurrentAdminAndReturnPrivateOpaqueDetail()throws Exception{
        var guest=new Client();assertThat(guest.call("GET","/api/admin/sessions",null).statusCode()).isEqualTo(401);
        var student=new Client();student.login();assertThat(student.call("GET","/api/admin/sessions",null).statusCode()).isEqualTo(403);
        String teacherEmail=UUID.randomUUID()+"@teacher.test";users.create(teacherEmail,passwords.encode("TestPass@123"),"Teacher",Role.TEACHER);
        var teacher=new Client();teacher.login(teacherEmail);assertThat(teacher.call("GET","/api/admin/sessions/no-such-ref",null).statusCode()).isEqualTo(403);
        var a=admin();var source=registry.snapshot().stream().filter(s->s.userId()==users.findByEmail(email).orElseThrow().id()).findFirst().orElseThrow();
        var response=a.call("GET","/api/admin/sessions/"+source.sessionRef(),null);
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("private","no-store");
        assertThat(response.body()).doesNotContain(source.id(),"JSESSIONID","passwordHash","securityVersion");
        assertThat(a.call("GET","/api/admin/sessions/"+source.id(),null).statusCode()).isEqualTo(404);
        assertThat(a.call("GET","/api/admin/sessions?size=51",null).statusCode()).isEqualTo(400);
        assertThat(a.call("GET","/api/admin/sessions?q=a&q=b",null).statusCode()).isEqualTo(400);
    }
    @Test void monitoringJoinsRealMetadataRejectsForeignAttemptAndRetainsDisconnectedHttpSession()throws Exception{
        var a=admin();var s=new Client();s.login();long uid=users.findByEmail(email).orElseThrow().id();String tab=UUID.randomUUID().toString();
        jdbc.update("insert into attempts(user_id,mode,selection_json,player_state_json) values (?,'REVIEW','{}',?)",uid,
            json.writeValueAsString(vn.edu.ptit.english.dto.AttemptData.PlayerState.empty()));
        long attempt=jdbc.queryForObject("select max(id) from attempts where user_id=?",Long.class,uid);
        var body=new HashMap<String,Object>(report(tab,0,false));body.put("attemptId",attempt);body.put("activity","LEARN");
        assertThat(s.call("POST","/api/auth/activity",body).statusCode()).isEqualTo(200);
        var actor=vn.edu.ptit.english.security.UserPrincipal.from(users.findByEmail(email).orElseThrow());
        var source=registry.snapshot().stream().filter(x->x.userId()==uid).findFirst().orElseThrow();
        var socket=org.mockito.Mockito.mock(org.springframework.web.socket.WebSocketSession.class);
        org.mockito.Mockito.when(socket.getId()).thenReturn("monitor-"+tab);org.mockito.Mockito.when(socket.isOpen()).thenReturn(true);
        live.attach(socket,actor,source.id());live.bindTab(socket.getId(),tab);
        var response=a.call("GET","/api/admin/sessions?q="+URLEncoder.encode(email,java.nio.charset.StandardCharsets.UTF_8),null);
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
        var page=json.readTree(response.body());assertThat(page.path("totalElements").asInt()).isEqualTo(1);
        assertThat(page.at("/items/0/primaryContext/activity").asText()).isEqualTo("REVIEW");
        var foreign=new Client();String foreignEmail=UUID.randomUUID()+"@other.test";users.create(foreignEmail,passwords.encode("TestPass@123"),"Other",Role.STUDENT);foreign.login(foreignEmail);
        assertThat(foreign.call("POST","/api/auth/activity",body).statusCode()).isEqualTo(404);
        live.detach(socket.getId());time.advance(60000);
        var detail=a.call("GET","/api/admin/sessions/"+source.sessionRef(),null);
        assertThat(detail.statusCode()).isEqualTo(200);assertThat(json.readTree(detail.body()).at("/session/connected").asBoolean()).isFalse();
        assertThat(Instant.parse(json.readTree(detail.body()).at("/session/idleExpiresAt").asText())).isEqualTo(source.idleExpiresAt());
    }
}
