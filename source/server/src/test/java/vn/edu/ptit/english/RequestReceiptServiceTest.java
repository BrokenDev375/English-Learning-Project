package vn.edu.ptit.english;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.RequestReceiptService;
import vn.edu.ptit.english.service.RequestReceiptService.Reply;
import static org.assertj.core.api.Assertions.*;

@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"app.demo.enabled=false", "app.bootstrap-admin.enabled=false"})
@Testcontainers
class RequestReceiptServiceTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
    }
    @Autowired RequestReceiptService service;
    @Autowired UserRepository users;
    @Autowired RequestReceiptJpaRepository receipts;
    @Autowired PlatformTransactionManager transactions;
    UserPrincipal actor;
    TransactionTemplate tx;
    @BeforeEach void setup() {
        tx = new TransactionTemplate(transactions);
        actor = UserPrincipal.from(tx.execute(s -> users.create(UUID.randomUUID()+"@test.local", "hash", "Receipt test", Role.STUDENT)));
    }
    @Test void dynamicStatusIsChosenOnceAndReplayKeepsOriginalResponse() {
        for (int status : new int[]{200, 201}) {
            String key = UUID.randomUUID().toString();
            var first = tx.execute(s -> service.execute(actor,key,"POST /api/attempts",Map.of("mode","LEARN"),
                () -> new Reply(status,Map.of("revision",0))));
            var replay = tx.execute(s -> service.execute(actor,key,"POST /api/attempts",Map.of("mode","LEARN"),
                () -> { throw new AssertionError("Replay must not invoke action or check a newer revision"); }));
            assertThat(first.status()).isEqualTo(status);
            assertThat(replay.status()).isEqualTo(status);
            assertThat(replay.body()).isEqualTo(first.body());
            assertThat(replay.replayed()).isTrue();
        }
    }
    @Test void existingFixedStatusAndNullBodyRemainSupported() {
        String key = UUID.randomUUID().toString();
        var first = tx.execute(s -> service.execute(actor,key,"DELETE /example",Map.of(),204,() -> null));
        var replay = tx.execute(s -> service.execute(actor,key,"DELETE /example",Map.of(),204,() -> "unused"));
        assertThat(first.body().isNull()).isTrue();
        assertThat(replay.status()).isEqualTo(204);
        assertThat(replay.replayed()).isTrue();
    }
    @Test void reusedKeyRejectsChangedOperationOrPayload() {
        String key = UUID.randomUUID().toString();
        tx.execute(s -> service.execute(actor,key,"POST /api/attempts",Map.of("count",1),() -> new Reply(201,Map.of())));
        assertThatThrownBy(() -> tx.execute(s -> service.execute(actor,key,"POST /api/attempts",Map.of("count",2),() -> new Reply(201,Map.of()))))
            .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("REQUEST_ID_REUSED"));
        assertThatThrownBy(() -> tx.execute(s -> service.execute(actor,key,"POST /other",Map.of("count",1),() -> new Reply(201,Map.of()))))
            .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("REQUEST_ID_REUSED"));
    }
    @Test void transactionFailureRollsBackBusinessWriteAndReceipt() {
        long before = receipts.count();
        String key = UUID.randomUUID().toString(), email = key+"@rollback.local";
        assertThatThrownBy(() -> tx.execute(s -> {
            service.execute(actor,key,"POST /example",Map.of(),() -> {
                users.create(email,"hash","Must roll back",Role.STUDENT);
                return new Reply(201,Map.of("created",true));
            });
            throw new IllegalStateException("Failure after writing receipt");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(receipts.count()).isEqualTo(before);
        assertThat(users.findByEmail(email)).isEmpty();
        assertThat(tx.execute(s -> service.execute(actor,key,"POST /example",Map.of(),() -> new Reply(201,Map.of()))).replayed()).isFalse();
    }
    @Test void concurrentSameKeyRunsActionOnlyOnce() throws Exception {
        String key = UUID.randomUUID().toString();
        var calls = new AtomicInteger();
        var start = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<RequestReceiptService.Result> task = () -> {
                start.await(10,TimeUnit.SECONDS);
                return tx.execute(s -> service.execute(actor,key,"POST /api/attempts",Map.of(),
                    () -> new Reply(calls.incrementAndGet()==1?201:200,Map.of("id",1))));
            };
            var a = pool.submit(task); var b = pool.submit(task);
            var first = a.get(30,TimeUnit.SECONDS); var second = b.get(30,TimeUnit.SECONDS);
            assertThat(calls.get()).isEqualTo(1);
            assertThat(first.status()).isEqualTo(201);
            assertThat(second.status()).isEqualTo(201);
            assertThat(first.replayed()).isNotEqualTo(second.replayed());
        } finally { pool.shutdownNow(); }
    }
}
