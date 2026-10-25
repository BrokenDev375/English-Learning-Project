package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import vn.edu.ptit.english.dto.UserRecord;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.repository.ClassMemberJpaRepository;
import vn.edu.ptit.english.repository.ClassroomJpaRepository;
import vn.edu.ptit.english.repository.UserJpaRepository;
import vn.edu.ptit.english.repository.UserRepository;

@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.demo.enabled=false")
@Testcontainers
class ModuleOneApiTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("ielts_module1_tests").withUsername("test_app").withPassword("test-password");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserJpaRepository userEntities;
    @Autowired ClassroomJpaRepository classroomEntities;
    @Autowired ClassMemberJpaRepository membershipEntities;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    UserRecord teacher, otherTeacher, student;
    String hash;

    @BeforeEach void fixtures() {
        // This database belongs to the disposable test container, not the development database.
        membershipEntities.deleteAllInBatch();
        classroomEntities.deleteAllInBatch();
        userEntities.deleteAllInBatch();
        if (hash == null) hash = passwords.encode("TestPass@123");
        teacher = users.create("teacher@test.local", hash, "Giảng viên Một", Role.TEACHER);
        otherTeacher = users.create("other@test.local", hash, "Giảng viên Hai", Role.TEACHER);
        student = users.create("student@test.local", hash, "Học viên Một", Role.STUDENT);
    }

    final class Client {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        String token;
        void csrf() throws Exception {
            var response = request("GET", "/api/auth/csrf", null, false);
            assertThat(response.statusCode()).isEqualTo(200);
            token = body(response).get("token").asText();
        }
        Client login(String email) throws Exception {
            csrf();
            var response = request("POST", "/api/auth/login", Map.of("email", email, "password", "TestPass@123"), true);
            assertThat(response.statusCode()).isEqualTo(200);
            csrf();
            return this;
        }
        HttpResponse<String> request(String method, String path, Object payload, boolean useCsrf) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15)).header("Accept", "application/json");
            if (useCsrf) builder.header("X-CSRF-TOKEN", token);
            if (payload != null) builder.header("Content-Type", "application/json");
            builder.method(method, payload == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)));
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> send(String method, String path, Object payload) throws Exception {
            return request(method, path, payload, !method.equals("GET"));
        }
    }
    JsonNode body(HttpResponse<String> response) throws Exception { return json.readTree(response.body()); }
    JsonNode create(Client client, String name) throws Exception {
        var response = client.send("POST", "/api/classes", Map.of("name", name, "description", "Lớp thử nghiệm", "requestId", UUID.randomUUID().toString()));
        assertThat(response.statusCode()).isEqualTo(201);
        return body(response);
    }

    @Test void authenticationRequiredAndWrongCredentialsAreJsonErrors() throws Exception {
        var client = new Client();
        var privateResponse = client.send("GET", "/api/classes", null);
        assertThat(privateResponse.statusCode()).isEqualTo(401);
        assertThat(body(privateResponse).get("code").asText()).isEqualTo("UNAUTHENTICATED");
        client.csrf();
        var wrong = client.send("POST", "/api/auth/login", Map.of("email", teacher.email(), "password", "wrong-password"));
        assertThat(wrong.statusCode()).isEqualTo(401);
        assertThat(body(wrong).get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test void registrationHashesPasswordNormalizesEmailAndAlwaysCreatesStudent() throws Exception {
        var client = new Client();
        client.csrf();
        var registered = client.send("POST", "/api/auth/register", Map.of("fullName", "  Học viên Mới  ", "email", "NEW@Test.local", "password", "Register@123"));
        assertThat(registered.statusCode()).isEqualTo(201);
        assertThat(body(registered).get("role").asText()).isEqualTo("STUDENT");
        assertThat(body(registered).has("passwordHash")).isFalse();
        var stored = users.findByEmail("new@test.local").orElseThrow();
        assertThat(stored.fullName()).isEqualTo("Học viên Mới");
        assertThat(stored.passwordHash()).isNotEqualTo("Register@123");
        assertThat(passwords.matches("Register@123", stored.passwordHash())).isTrue();
        var duplicate = client.send("POST", "/api/auth/register", Map.of("fullName", "Người khác", "email", "new@test.local", "password", "Register@123"));
        assertThat(duplicate.statusCode()).isEqualTo(409);
    }

    @Test void forgedRoleAndInvalidInputsAreRejected() throws Exception {
        var client = new Client();
        client.csrf();
        var forged = client.send("POST", "/api/auth/register", Map.of("fullName", "Người mới", "email", "forged@test.local", "password", "Register@123", "role", "TEACHER"));
        assertThat(forged.statusCode()).isEqualTo(400);
        assertThat(users.findByEmail("forged@test.local")).isEmpty();
        var shortPassword = client.send("POST", "/api/auth/register", Map.of("fullName", "Người mới", "email", "valid@test.local", "password", "short"));
        assertThat(shortPassword.statusCode()).isEqualTo(400);
        var longUtf8 = client.send("POST", "/api/auth/register", Map.of("fullName", "Người mới", "email", "valid@test.local", "password", "ấ".repeat(30)));
        assertThat(longUtf8.statusCode()).isEqualTo(400);
    }

    @Test void csrfIsRequiredForMutations() throws Exception {
        var owner = new Client().login(teacher.email());
        var response = owner.request("POST", "/api/classes", Map.of("name", "Không hợp lệ", "requestId", UUID.randomUUID().toString()), false);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(body(response).get("code").asText()).isEqualTo("CSRF_INVALID");
        assertThat(classroomEntities.count()).isZero();
    }

    @Test void studentCannotCreateClassesAndOnlyOwnerSeesTeacherClasses() throws Exception {
        var owner = new Client().login(teacher.email());
        create(owner, "IELTS Một");
        var learner = new Client().login(student.email());
        assertThat(learner.send("POST", "/api/classes", Map.of("name", "Tự tạo lớp", "requestId", UUID.randomUUID().toString())).statusCode()).isEqualTo(403);
        assertThat(body(learner.send("GET", "/api/classes", null))).isEmpty();
        var second = new Client().login(otherTeacher.email());
        assertThat(body(second.send("GET", "/api/classes", null))).isEmpty();
        assertThat(body(owner.send("GET", "/api/classes", null))).hasSize(1);
    }

    @Test void joinIsIdempotentAndStudentCanJoinMultipleClasses() throws Exception {
        var owner = new Client().login(teacher.email());
        var first = create(owner, "Reading");
        var second = create(owner, "Writing");
        var learner = new Client().login(student.email());
        var joined = body(learner.send("POST", "/api/classes/join", Map.of("code", first.get("joinCode").asText().toLowerCase())));
        assertThat(joined.get("alreadyJoined").asBoolean()).isFalse();
        assertThat(joined.get("classroom").get("joinCode").isNull()).isTrue();
        var duplicate = body(learner.send("POST", "/api/classes/join", Map.of("code", first.get("joinCode").asText())));
        assertThat(duplicate.get("alreadyJoined").asBoolean()).isTrue();
        learner.send("POST", "/api/classes/join", Map.of("code", second.get("joinCode").asText()));
        assertThat(body(learner.send("GET", "/api/classes", null))).hasSize(2);
        assertThat(body(owner.send("GET", "/api/classes/" + first.get("id").asLong() + "/members", null))).hasSize(1);
    }

    @Test void crossTeacherAccessAndStudentRosterAccessAreForbidden() throws Exception {
        var owner = new Client().login(teacher.email());
        var room = create(owner, "Lớp riêng");
        String path = "/api/classes/" + room.get("id").asLong();
        var second = new Client().login(otherTeacher.email());
        assertThat(second.send("GET", path, null).statusCode()).isEqualTo(403);
        assertThat(second.send("GET", path + "/members", null).statusCode()).isEqualTo(403);
        assertThat(second.send("PATCH", path, Map.of("name", "Chiếm lớp", "description", "")).statusCode()).isEqualTo(403);
        assertThat(second.send("DELETE", path + "/members/" + student.id(), null).statusCode()).isEqualTo(403);
        var learner = new Client().login(student.email());
        assertThat(learner.send("GET", path, null).statusCode()).isEqualTo(403);
        learner.send("POST", "/api/classes/join", Map.of("code", room.get("joinCode").asText()));
        assertThat(learner.send("GET", path, null).statusCode()).isEqualTo(200);
        assertThat(learner.send("GET", path + "/members", null).statusCode()).isEqualTo(403);
    }

    @Test void editingRemovingAndLeavingRespectOwnershipAndMembership() throws Exception {
        var owner = new Client().login(teacher.email());
        var room = create(owner, "Tên trước");
        String path = "/api/classes/" + room.get("id").asLong();
        var edited = owner.send("PATCH", path, Map.of("name", "Tên sau", "description", "Đã cập nhật"));
        assertThat(edited.statusCode()).isEqualTo(200);
        assertThat(body(edited).get("name").asText()).isEqualTo("Tên sau");
        var learner = new Client().login(student.email());
        learner.send("POST", "/api/classes/join", Map.of("code", room.get("joinCode").asText()));
        assertThat(owner.send("DELETE", path + "/members/" + student.id(), null).statusCode()).isEqualTo(204);
        assertThat(owner.send("DELETE", path + "/members/" + student.id(), null).statusCode()).isEqualTo(204);
        assertThat(learner.send("GET", path, null).statusCode()).isEqualTo(403);
        learner.send("POST", "/api/classes/join", Map.of("code", room.get("joinCode").asText()));
        assertThat(learner.send("DELETE", path + "/membership", null).statusCode()).isEqualTo(204);
        assertThat(learner.send("DELETE", path + "/membership", null).statusCode()).isEqualTo(204);
        assertThat(body(learner.send("GET", "/api/classes", null))).isEmpty();
    }

    @Test void createRequestReplaysWithoutDuplicateAndRejectsChangedPayload() throws Exception {
        var owner = new Client().login(teacher.email());
        String request = UUID.randomUUID().toString();
        var payload = Map.of("name", "Chỉ một lớp", "description", "", "requestId", request);
        var first = owner.send("POST", "/api/classes", payload);
        var replay = owner.send("POST", "/api/classes", payload);
        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(body(replay).get("id")).isEqualTo(body(first).get("id"));
        assertThat(owner.send("POST", "/api/classes", Map.of("name", "Nội dung khác", "description", "", "requestId", request)).statusCode()).isEqualTo(409);
        assertThat(classroomEntities.count()).isEqualTo(1);
    }

    @Test void concurrentCreateAndJoinKeepExactlyOneRow() throws Exception {
        var owner = new Client().login(teacher.email());
        var pool = Executors.newFixedThreadPool(4);
        try {
            var barrier = new CyclicBarrier(4);
            String request = UUID.randomUUID().toString();
            var tasks = new ArrayList<java.util.concurrent.Callable<HttpResponse<String>>>();
            for (int i = 0; i < 4; i++) tasks.add(() -> { barrier.await(); return owner.send("POST", "/api/classes", Map.of("name", "Đồng thời", "description", "", "requestId", request)); });
            var responses = pool.invokeAll(tasks);
            for (var response : responses) assertThat(response.get().statusCode()).isIn(200, 201);
            assertThat(classroomEntities.count()).isEqualTo(1);
            var room = body(owner.send("GET", "/api/classes", null)).get(0);
            var learner = new Client().login(student.email());
            var joinBarrier = new CyclicBarrier(4);
            tasks.clear();
            for (int i = 0; i < 4; i++) tasks.add(() -> { joinBarrier.await(); return learner.send("POST", "/api/classes/join", Map.of("code", room.get("joinCode").asText())); });
            for (var response : pool.invokeAll(tasks)) assertThat(response.get().statusCode()).isEqualTo(200);
            assertThat(membershipEntities.count()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void invalidCodeAndMalformedIdReturnUsefulErrors() throws Exception {
        var learner = new Client().login(student.email());
        assertThat(learner.send("POST", "/api/classes/join", Map.of("code", "!!!!1234")).statusCode()).isEqualTo(400);
        assertThat(learner.send("POST", "/api/classes/join", Map.of("code", "ABCDEFGH")).statusCode()).isEqualTo(404);
        assertThat(learner.send("GET", "/api/classes/not-a-number", null).statusCode()).isEqualTo(400);
    }

    @Test void logoutInvalidatesSession() throws Exception {
        var owner = new Client().login(teacher.email());
        assertThat(owner.send("GET", "/api/auth/me", null).statusCode()).isEqualTo(200);
        assertThat(owner.send("POST", "/api/auth/logout", null).statusCode()).isEqualTo(204);
        assertThat(owner.send("GET", "/api/auth/me", null).statusCode()).isEqualTo(401);
    }
}
