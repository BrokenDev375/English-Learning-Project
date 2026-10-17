package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import vn.edu.ptit.english.dto.UserRecord;
import vn.edu.ptit.english.enums.*;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.security.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.demo.enabled=false", "app.bootstrap-admin.enabled=false"})
@Testcontainers
class AccountAdministrationApiTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("english_admin_test").withUsername("test_app").withPassword("test-password");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired UserJpaRepository entities;
    @Autowired ClassroomJpaRepository rooms;
    @Autowired ClassMemberJpaRepository members;
    @Autowired PasswordEncoder passwords;
    @Autowired AccountGuard guard;
    @Autowired PlatformTransactionManager transactions;
    UserRecord admin, secondAdmin, learner, author;

    @BeforeEach void fixtures() {
        members.deleteAllInBatch(); rooms.deleteAllInBatch(); entities.deleteAllInBatch();
        String hash = passwords.encode("TestPass@123");
        admin = users.create("admin@test.local", hash, "Admin Một", Role.ADMIN);
        secondAdmin = users.create("second@test.local", hash, "Admin Hai", Role.ADMIN);
        learner = users.create("learner@test.local", hash, "Người học", Role.STUDENT);
        author = users.create("author@test.local", hash, "Người biên soạn", Role.TEACHER);
    }
    final class Client {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        String token;
        void csrf() throws Exception {
            var response = request("GET", "/api/auth/csrf", null, false);
            assertThat(response.statusCode()).isEqualTo(200);
            token = body(response).path("token").asText();
        }
        HttpResponse<String> request(String method, String path, Object payload, boolean withCsrf) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15)).header("Accept", "application/json");
            if (withCsrf) builder.header("X-CSRF-TOKEN", token);
            if (payload != null) builder.header("Content-Type", "application/json");
            builder.method(method, payload == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)));
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> send(String method, String path, Object payload) throws Exception {
            return request(method, path, payload, !method.equals("GET"));
        }
        HttpResponse<String> authenticate(String email) throws Exception {
            csrf();
            var response = send("POST", "/api/auth/login", Map.of("email", email, "password", "TestPass@123"));
            if (response.statusCode() == 200) csrf();
            return response;
        }
        Client login(UserRecord user) throws Exception {
            assertThat(authenticate(user.email()).statusCode()).isEqualTo(200); return this;
        }
        WebSocket socket(WebSocket.Listener listener, String origin) {
            String cookie = cookies.getCookieStore().getCookies().stream()
                .map(c -> c.getName() + "=" + c.getValue()).collect(java.util.stream.Collectors.joining("; "));
            return HttpClient.newHttpClient().newWebSocketBuilder().header("Cookie", cookie).header("Origin", origin)
                .connectTimeout(Duration.ofSeconds(5)).buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws/events"), listener).join();
        }
    }
    JsonNode body(HttpResponse<String> response) throws Exception { return json.readTree(response.body()); }
    String path(UserRecord user) { return "/api/admin/users/" + user.id(); }

    @Test void onlyAdminCanListAndSearchResultsNeverExposeCredentials() throws Exception {
        assertThat(new Client().send("GET", "/api/admin/users", null).statusCode()).isEqualTo(401);
        assertThat(new Client().login(learner).send("GET", "/api/admin/users", null).statusCode()).isEqualTo(403);
        assertThat(new Client().login(author).send("GET", "/api/admin/users", null).statusCode()).isEqualTo(403);
        var client = new Client().login(admin);
        var result = client.send("GET", "/api/admin/users?q=learner&role=STUDENT&status=ACTIVE&size=1", null);
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(body(result).path("items")).hasSize(1);
        assertThat(body(result).path("totalElements").asInt()).isEqualTo(1);
        assertThat(result.body()).doesNotContain("password", "securityVersion");
        assertThat(client.send("GET", "/api/admin/users?size=51", null).statusCode()).isEqualTo(400);
        assertThat(client.send("GET", "/api/admin/users?page=-1", null).statusCode()).isEqualTo(400);
        assertThat(body(client.send("GET", "/api/admin/users?q=%25", null)).path("items")).isEmpty();
    }
    @Test void roleChangeRevokesAllSessionsAndRequiresFreshLogin() throws Exception {
        var manager = new Client().login(admin);
        var first = new Client().login(learner); var second = new Client().login(learner);
        assertThat(manager.send("PATCH", path(learner) + "/role", Map.of("role", "TEACHER")).statusCode()).isEqualTo(200);
        assertThat(first.send("GET", "/api/auth/me", null).statusCode()).isEqualTo(401);
        assertThat(second.send("GET", "/api/auth/me", null).statusCode()).isEqualTo(401);
        assertThat(body(first.authenticate(learner.email())).path("role").asText()).isEqualTo("TEACHER");
        assertThat(manager.send("PATCH", path(learner) + "/role", Map.of("role", "TEACHER")).statusCode()).isEqualTo(200);
        assertThat(first.send("GET", "/api/auth/me", null).statusCode()).isEqualTo(200);
    }
    @Test void lockAndUnlockNeverReviveOldSessions() throws Exception {
        var manager = new Client().login(admin); var client = new Client().login(learner);
        assertThat(manager.send("PATCH", path(learner) + "/status", Map.of("status", "LOCKED")).statusCode()).isEqualTo(200);
        assertThat(client.send("GET", "/api/auth/me", null).statusCode()).isEqualTo(401);
        assertThat(new Client().authenticate(learner.email()).statusCode()).isEqualTo(401);
        assertThat(manager.send("PATCH", path(learner) + "/status", Map.of("status", "ACTIVE")).statusCode()).isEqualTo(200);
        assertThat(client.send("GET", "/api/auth/me", null).statusCode()).isEqualTo(401);
        assertThat(client.authenticate(learner.email()).statusCode()).isEqualTo(200);
    }
    @Test void softDeleteIsIdempotentAndPreservesHistoricalForeignKeys() throws Exception {
        var manager = new Client().login(admin); var teacher = new Client().login(author);
        var room = teacher.send("POST", "/api/classes", Map.of("name", "History", "requestId", UUID.randomUUID()));
        assertThat(room.statusCode()).isEqualTo(201);
        var client = new Client().login(learner);
        assertThat(client.send("POST", "/api/classes/join", Map.of("code", body(room).path("joinCode").asText())).statusCode()).isEqualTo(200);
        assertThat(manager.send("DELETE", path(learner), null).statusCode()).isEqualTo(204);
        assertThat(manager.send("DELETE", path(learner), null).statusCode()).isEqualTo(204);
        assertThat(members.count()).isEqualTo(1);
        assertThat(users.findById(learner.id()).orElseThrow().status()).isEqualTo(AccountStatus.DELETED);
        assertThat(client.send("GET", "/api/auth/me", null).statusCode()).isEqualTo(401);
        assertThat(new Client().authenticate(learner.email()).statusCode()).isEqualTo(401);
        assertThat(manager.send("PATCH", path(learner) + "/status", Map.of("status", "ACTIVE")).statusCode()).isEqualTo(409);
        assertThat(manager.send("PATCH", path(learner) + "/role", Map.of("role", "ADMIN")).statusCode()).isEqualTo(409);
    }
    @Test void selfChangesForgedRegistrationAndInvalidMutationAreRejected() throws Exception {
        var manager = new Client().login(admin);
        assertThat(manager.send("PATCH", path(admin) + "/role", Map.of("role", "STUDENT")).statusCode()).isEqualTo(409);
        assertThat(manager.send("PATCH", path(admin) + "/status", Map.of("status", "LOCKED")).statusCode()).isEqualTo(409);
        assertThat(manager.send("DELETE", path(admin), null).statusCode()).isEqualTo(409);
        assertThat(manager.send("PATCH", path(learner) + "/status", Map.of("status", "DELETED")).statusCode()).isEqualTo(400);
        assertThat(manager.send("PATCH", path(learner) + "/role", Map.of("role", "OWNER")).statusCode()).isEqualTo(400);
        assertThat(manager.send("DELETE", "/api/admin/users/999999999", null).statusCode()).isEqualTo(404);
        assertThat(manager.request("PATCH", path(learner) + "/role", Map.of("role", "ADMIN"), false).statusCode()).isEqualTo(403);
        var stranger = new Client(); stranger.csrf();
        assertThat(stranger.send("POST", "/api/auth/register", Map.of("fullName", "Forged Admin", "email",
            "forged@test.local", "password", "TestPass@123", "role", "ADMIN")).statusCode()).isEqualTo(400);
        assertThat(users.findByEmail("forged@test.local")).isEmpty();
    }
    @Test void concurrentAdminChangesKeepOneActiveAdmin() throws Exception {
        var first = new Client().login(admin); var second = new Client().login(secondAdmin);
        var pool = Executors.newFixedThreadPool(2); var barrier = new CyclicBarrier(2);
        try {
            var responses = pool.invokeAll(List.<Callable<Integer>>of(
                () -> { barrier.await(); return first.send("PATCH", path(secondAdmin) + "/status", Map.of("status", "LOCKED")).statusCode(); },
                () -> { barrier.await(); return second.send("PATCH", path(admin) + "/status", Map.of("status", "LOCKED")).statusCode(); }));
            var codes = List.of(responses.get(0).get(), responses.get(1).get());
            assertThat(codes).contains(200);
            assertThat(codes.stream().filter(c -> c == 200).count()).isEqualTo(1);
            assertThat(entities.countByRoleAndStatus(Role.ADMIN, AccountStatus.ACTIVE)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    static class SocketListener implements WebSocket.Listener {
        final CompletableFuture<Integer> closed = new CompletableFuture<>();
        final CompletableFuture<String> received = new CompletableFuture<>();
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence text, boolean last) {
            if (text.toString().contains("CONNECTED")) received.complete(text.toString());
            socket.request(1); return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket socket, int code, String reason) {
            closed.complete(code); return null;
        }
        @Override public void onError(WebSocket socket, Throwable error) { closed.completeExceptionally(error); }
    }
    @Test void lockClosesAuthenticatedSocketAndUntrustedOriginIsRejected() throws Exception {
        var client = new Client().login(learner); var listener = new SocketListener();
        var socket = client.socket(listener, "http://localhost:5173");
        assertThat(listener.received.get(5, TimeUnit.SECONDS)).contains("CONNECTED");
        assertThatThrownBy(() -> client.socket(new SocketListener(), "https://untrusted.example")).isInstanceOf(CompletionException.class);
        var manager = new Client().login(admin);
        assertThat(manager.send("PATCH", path(learner) + "/status", Map.of("status", "LOCKED")).statusCode()).isEqualTo(200);
        assertThat(listener.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
        socket.abort();
    }
    @Test void logoutClosesOnlyThatSessionsSocket() throws Exception {
        var first = new Client().login(learner); var second = new Client().login(learner);
        var listener = new SocketListener(); var socket = first.socket(listener, "http://localhost:5173");
        listener.received.get(5, TimeUnit.SECONDS);
        assertThat(first.send("POST", "/api/auth/logout", null).statusCode()).isEqualTo(204);
        assertThat(listener.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
        assertThat(second.send("GET", "/api/auth/me", null).statusCode()).isEqualTo(200);
        socket.abort();
    }
    @Test void switchingAccountsDoesNotLetTheOldIdentityRevokeTheNewSession() throws Exception {
        var client = new Client().login(learner);
        var listener = new SocketListener();
        var socket = client.socket(listener, "http://localhost:5173");
        listener.received.get(5, TimeUnit.SECONDS);
        assertThat(client.authenticate(author.email()).statusCode()).isEqualTo(200);
        assertThat(listener.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
        var manager = new Client().login(admin);
        assertThat(manager.send("PATCH", path(learner) + "/status", Map.of("status", "LOCKED")).statusCode()).isEqualTo(200);
        var current = client.send("GET", "/api/auth/me", null);
        assertThat(current.statusCode()).isEqualTo(200);
        assertThat(body(current).path("id").asLong()).isEqualTo(author.id());
        socket.abort();
    }
    @Test void accountLockWaitsForInFlightWritesAndRejectsStaleActors() throws Exception {
        var transaction = new TransactionTemplate(transactions);
        var actor = UserPrincipal.from(author);
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        var manager = new Client().login(admin);
        try {
            var write = pool.submit(() -> transaction.execute(status -> {
                guard.lockCurrent(actor); held.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test timed out"); }
                catch (InterruptedException ex) { throw new RuntimeException(ex); }
                return true;
            }));
            assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
            var lock = pool.submit(() -> manager.send("PATCH", path(author) + "/status", Map.of("status", "LOCKED")));
            Thread.sleep(150);
            assertThat(lock.isDone()).isFalse();
            release.countDown();
            assertThat(write.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(lock.get(5, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
            assertThatThrownBy(() -> transaction.execute(status -> guard.lockCurrent(actor)))
                .isInstanceOf(vn.edu.ptit.english.exception.ApiException.class);
        } finally { release.countDown(); pool.shutdownNow(); }
    }
}
