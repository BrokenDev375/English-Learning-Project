package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import jakarta.persistence.EntityManager;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.demo.enabled=false", "app.bootstrap-admin.enabled=false"})
@Testcontainers
class PersonalReviewApiTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4").withDatabaseName("m04_review_test");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        r.add("spring.datasource.username", MYSQL::getUsername); r.add("spring.datasource.password", MYSQL::getPassword);
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired AttemptRepository attempts;
    @MockitoSpyBean ReviewRepository reviews;
    TransactionTemplate tx;
    UserRecord alice, bob, teacher, admin;
    long topic, objectiveA, objectiveB;
    long lessonA, lessonB, lessonA2;
    List<Long> lessonIds = new ArrayList<>();

    @BeforeEach void fixture() {
        tx = new TransactionTemplate(transactions); lessonIds.clear();
        String hash = passwords.encode("TestPass@123"), prefix = UUID.randomUUID().toString();
        tx.executeWithoutResult(s -> {
            // Keep the published journey isolated from earlier test fixtures.
            em.createQuery("select l from LessonEntity l where l.retiredAt is null",LessonEntity.class).getResultList().forEach(LessonEntity::retire);
            alice = users.create(prefix + "a@test.local", hash, "Alice", Role.STUDENT);
            bob = users.create(prefix + "b@test.local", hash, "Bob", Role.STUDENT);
            teacher = users.create(prefix + "t@test.local", hash, "Teacher", Role.TEACHER);
            admin = users.create(prefix + "x@test.local", hash, "Admin", Role.ADMIN);
            var t = new TopicEntity(teacher.id(), prefix, "Topic", ""); em.persist(t); em.flush(); topic = t.getId();
            var a = new ObjectiveEntity(teacher.id(), topic, prefix + "a", "Do/does", "");
            var b = new ObjectiveEntity(teacher.id(), topic, prefix + "b", "S/es", "");
            em.persist(a); em.persist(b); em.flush(); objectiveA = a.getId(); objectiveB = b.getId();
            var unit = new UnitEntity(teacher.id(), topic, "Unit", "", 1, 0,((Number)em.createQuery("select coalesce(max(u.roundNumber),0) from UnitEntity u").getSingleResult()).intValue()+1); em.persist(unit); em.flush();
            lessonA = lesson(unit.getId(), objectiveA, 0);
            lessonB = lesson(unit.getId(), objectiveB, 1);
            lessonA2 = lesson(unit.getId(), objectiveA, 2);
        });
    }
    long lesson(long unitId, long objectiveId, int position) {
        return lesson(unitId, objectiveId, position, false);
    }
    long lesson(long unitId, long objectiveId, int position, boolean mixedDifficulty) {
        var slots = new ArrayList<Slot>();
        for (int i = 0; i < 16; i++) {
            var root = new QuestionEntity(teacher.id(), UUID.randomUUID().toString()); em.persist(root); em.flush();
            String level = mixedDifficulty && i >= 5 && i < 7 ? "HARD" : i >= 13 ? "MEDIUM" : "EASY";
            var data = new Question("SINGLE_CHOICE", topic, objectiveId, level, "Question " + i,
                node("{\"options\":[{\"id\":\"yes\",\"text\":\"Yes\"},{\"id\":\"no\",\"text\":\"No\"}]}"),
                node("{\"correctOptionId\":\"yes\"}"), "SECRET_EXPLANATION", "SECRET_HINT", null, new MaterialData.Source("Fixture", null, null));
            var version = new QuestionVersionEntity(root.getId(), 1, data); em.persist(version); em.flush(); root.initialDraft(version); root.publish();
            slots.add(new Slot(version.getId(), i == 0 ? "GUIDED" : i < 7 ? "INDEPENDENT" : "REVIEW_VARIANT", i == 0 ? 0 : i < 7 ? i - 1 : i - 7));
        }
        var root = new LessonEntity(teacher.id(), unitId, position); em.persist(root); em.flush();
        var data = new Lesson("Lesson " + position, "", node("[{\"kind\":\"TEXT\",\"text\":\"Read\"}]"), List.of(objectiveId), slots);
        var version = new LessonVersionEntity(root.getId(), 1, data); em.persist(version); em.flush();
        root.initialDraft(version); root.publish(); em.flush(); lessonIds.add(root.getId()); return root.getId();
    }
    JsonNode node(String value) { try { return json.readTree(value); } catch (Exception e) { throw new IllegalArgumentException(e); } }
    String key() { return UUID.randomUUID().toString(); }
    JsonNode ok(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
        return response.body().isEmpty() ? json.nullNode() : json.readTree(response.body());
    }
    final class Client {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        String csrf;
        HttpResponse<String> call(String method, String path, Object data) throws Exception {
            var b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(30));
            if (!method.equals("GET") && csrf != null) b.header("X-CSRF-TOKEN", csrf);
            if (data != null) b.header("Content-Type", "application/json");
            return http.send(b.method(method, data == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data))).build(), HttpResponse.BodyHandlers.ofString());
        }
        Client login(UserRecord user) throws Exception {
            csrf = ok(call("GET", "/api/auth/csrf", null), 200).path("token").asText();
            ok(call("POST", "/api/auth/login", Map.of("email", user.email(), "password", "TestPass@123")), 200);
            csrf = ok(call("GET", "/api/auth/csrf", null), 200).path("token").asText(); return this;
        }
    }
    JsonNode learn(Client c, long lesson) throws Exception {
        return ok(c.call("POST", "/api/attempts", Map.of("requestId", key(), "mode", "LEARN", "lessonId", lesson)), 201);
    }
    JsonNode refresh(Client c, JsonNode s) throws Exception { return ok(c.call("GET", "/api/attempts/" + s.path("attemptId").asLong(), null), 200); }
    ObjectNode answers(JsonNode s, String option) {
        var m = json.createObjectNode().put("requestId", key()).put("baseRevision", s.path("revision").asLong());
        var list = m.putArray("answers");
        s.path("items").forEach(i -> {
            var a = list.addObject().put("attemptQuestionId", i.path("attemptQuestionId").asLong());
            if (option == null) a.putNull("response"); else a.set("response", node("{\"type\":\"SINGLE_CHOICE\",\"optionId\":\"" + option + "\"}"));
        });
        m.set("playerState", s.path("playerState").deepCopy()); return m;
    }
    JsonNode submit(Client c, JsonNode s, String option) throws Exception {
        return ok(c.call("POST", "/api/attempts/" + s.path("attemptId").asLong() + "/submit", answers(s, option)), 200);
    }
    JsonNode sync(Client c) throws Exception { return ok(c.call("POST", "/api/me/review/sync", Map.of("requestId", key())), 200); }
    JsonNode dashboard(Client c) throws Exception { return ok(c.call("GET", "/api/me/review?topicId=" + topic, null), 200); }
    JsonNode state(Client c) throws Exception { return dashboard(c).path("objectives").path("items").get(0); }
    Map<String, Object> review(String kind, int count) {
        return Map.of("requestId", key(), "mode", "REVIEW", "reviewKind", kind, "topicId", topic, "count", count);
    }

    @Test void isolatesStudentsAndRejectsTeachersAdminInvalidBodiesAndCsrf() throws Exception {
        ok(new Client().call("GET", "/api/me/review", null), 401);
        var a = new Client().login(alice);
        ok(a.call("POST", "/api/me/review/sync", Map.of("requestId", key(), "userId", bob.id())), 400);
        ok(a.call("POST", "/api/me/review/sync", Map.of("requestId", "bad")), 400);
        ok(a.call("GET", "/api/me/review?kind=OTHER", null), 400);
        ok(a.call("GET", "/api/me/review?page=-1", null), 400);
        ok(a.call("GET", "/api/me/review?size=51", null), 400);
        a.csrf = null; ok(a.call("POST", "/api/me/review/sync", Map.of("requestId", key())), 403);
        for (var user : List.of(teacher, admin)) {
            var c = new Client().login(user); ok(c.call("GET", "/api/me/review", null), 403);
            ok(c.call("POST", "/api/me/review/sync", Map.of("requestId", key())), 403);
        }
    }
    @Test void twoStudentsWithDifferentMistakesReceiveDifferentReviewObjectives() throws Exception {
        var a = new Client().login(alice); var b = new Client().login(bob);
        submit(a, learn(a, lessonA), "no"); submit(b, learn(b, lessonB), "no");
        assertThat(sync(a).path("processedSubmissions").asInt()).isEqualTo(1); sync(b);
        assertThat(state(a).path("objectiveId").asLong()).isEqualTo(objectiveA);
        assertThat(state(b).path("objectiveId").asLong()).isEqualTo(objectiveB);
        var ar = ok(a.call("POST", "/api/attempts", review("MISTAKES", 10)), 201);
        var br = ok(b.call("POST", "/api/attempts", review("MISTAKES", 10)), 201);
        for (var i : ar.path("items")) {
            assertThat(i.path("objectiveId").asLong()).isEqualTo(objectiveA);
            assertThat(i.path("practiceRole").asText()).isEqualTo("REVIEW_VARIANT");
            assertThat(i.path("difficulty").asText()).isEqualTo("EASY");
            assertThat(i.path("wasSeenBefore").asBoolean()).isFalse();
        }
        for (var i : br.path("items")) assertThat(i.path("objectiveId").asLong()).isEqualTo(objectiveB);
        assertThat(dashboard(a).toString()).doesNotContain("answerKey", "SECRET_", "transcript", "password", "storageKey");
        ok(b.call("GET", "/api/attempts/" + ar.path("attemptId").asLong(), null), 404);
    }
    @Test void synchronizationIsDurableReplayableAndDoesNotDoubleCountSubmissions() throws Exception {
        var c = new Client().login(alice); submit(c, learn(c, lessonA), "yes");
        var command = Map.of("requestId", key());
        var first = c.call("POST", "/api/me/review/sync", command); var before = state(c);
        var replay = c.call("POST", "/api/me/review/sync", command);
        assertThat(ok(replay, 200)).isEqualTo(ok(first, 200));
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(sync(c).path("processedSubmissions").asInt()).isZero();
        assertThat(state(c)).isEqualTo(before);
        tx.executeWithoutResult(s -> { em.clear(); assertThat(reviews.progress(alice.id())).hasSize(1); });
        assertThat(state(new Client().login(alice))).isEqualTo(before);
    }
    @Test void staleResultsRequireSyncBeforeCreatingANewReview() throws Exception {
        var c = new Client().login(alice); submit(c, learn(c, lessonA), "no");
        assertThat(dashboard(c).path("pendingSubmissions").asInt()).isEqualTo(1);
        assertThat(ok(c.call("POST", "/api/attempts", review("DUE", 5)), 409).path("code").asText()).isEqualTo("REVIEW_SYNC_REQUIRED");
        sync(c); ok(c.call("POST", "/api/attempts", review("DUE", 5)), 201);
    }
    @Test void emptySubmissionIsInsufficientEvidenceAndCannotPromote() throws Exception {
        var c = new Client().login(alice); submit(c, learn(c, lessonA), null); sync(c);
        var p = state(c);
        assertThat(p.path("status").asText()).isEqualTo("NO_EVIDENCE");
        assertThat(p.path("firstAnsweredCount").asInt()).isZero();
        assertThat(p.path("unansweredCount").asInt()).isEqualTo(7);
        assertThat(p.path("stage").asInt()).isZero();
        assertThat(p.path("freshSetCount").asInt()).isZero();
        assertThat(p.path("recommendedDifficulty").asText()).isEqualTo("EASY");
    }
    @Test void firstWrongSurvivesGuidedRetryFinalCorrectAndPostSubmissionCorrection() throws Exception {
        var c = new Client().login(alice); var s = learn(c, lessonA);
        String path = "/api/attempts/" + s.path("attemptId").asLong() + "/items/" + s.path("items").get(0).path("attemptQuestionId").asLong();
        for (String option : List.of("no", "yes")) {
            var command = json.createObjectNode().put("requestId", key()).put("baseRevision", s.path("revision").asLong());
            command.set("response", node("{\"type\":\"SINGLE_CHOICE\",\"optionId\":\"" + option + "\"}"));
            ok(c.call("POST", path + "/check", command), 200); s = refresh(c, s);
        }
        submit(c, s, "yes"); sync(c); var before = state(c);
        assertThat(before.path("hasMistakes").asBoolean()).isTrue();
        assertThat(before.path("lastWrongCount").asInt()).isEqualTo(1);
        assertThat(before.path("lastIndependentCorrect").asInt()).isEqualTo(6);
        s = refresh(c, s);
        var correction = json.createObjectNode().put("requestId", key()).put("baseRevision", s.path("revision").asLong());
        correction.set("response", node("{\"type\":\"SINGLE_CHOICE\",\"optionId\":\"yes\"}"));
        ok(c.call("POST", path + "/retry", correction), 200);
        assertThat(sync(c).path("processedSubmissions").asInt()).isZero();
        assertThat(state(c)).isEqualTo(before);
        assertThat(dashboard(c).path("choices").path("wrong").asInt()).isEqualTo(1);
        var wrong = ok(c.call("POST", "/api/attempts", review("WRONG", 10)), 201);
        assertThat(wrong.path("items").size()).isEqualTo(1);
        assertThat(wrong.path("items").get(0).path("originKey")).isEqualTo(s.path("items").get(0).path("originKey"));
        assertThat(wrong.path("items").get(0).path("practiceRole").asText()).isEqualTo("GUIDED");
    }
    @Test void twoFreshSetsCanSuggestHigherDifficultyButRepeatingOldQuestionsCannot() throws Exception {
        var c = new Client().login(alice); submit(c, learn(c, lessonA), "yes"); sync(c);
        assertThat(state(c).path("freshSetCount").asInt()).isEqualTo(1);
        submit(c, learn(c, lessonA), "yes"); sync(c);
        assertThat(state(c).path("freshSetCount").asInt()).isEqualTo(1);
        assertThat(state(c).path("recommendedDifficulty").asText()).isEqualTo("EASY");
        submit(c, learn(c, lessonA2), "yes"); sync(c);
        assertThat(state(c).path("freshSetCount").asInt()).isEqualTo(2);
        assertThat(state(c).path("recommendedDifficulty").asText()).isEqualTo("MEDIUM");
        assertThat(state(c).path("currentDifficulty").asText()).isEqualTo("EASY");
        assertThat(state(c).path("stage").asInt()).isEqualTo(1);
    }
    @Test void insufficientNewHigherLevelContentDoesNotProducePromotion() throws Exception {
        var c = new Client().login(alice);
        submit(c, learn(c, lessonA), "yes"); submit(c, learn(c, lessonA2), "yes"); sync(c);
        tx.executeWithoutResult(t -> {
            var versions = em.createQuery("select q from QuestionVersionEntity q where q.objectiveId=:objective and q.difficulty='MEDIUM'", QuestionVersionEntity.class)
                .setParameter("objective", objectiveA).getResultList();
            long attempt = attempts.active(alice.id(), vn.edu.ptit.english.enums.AttemptEnums.Mode.LEARN).stream().findFirst().map(AttemptEntity::getId).orElse(0L);
            if (attempt == 0) attempt = em.createQuery("select a.id from AttemptEntity a where a.userId=:u order by a.id", Long.class).setParameter("u", alice.id()).setMaxResults(1).getSingleResult();
            for (var q : versions) {
                var root = em.find(QuestionEntity.class, q.getQuestionId());
                em.persist(new QuestionExposureEntity(alice.id(), root.getOriginKey(), attempt, Instant.now()));
            }
        });
        assertThat(state(c).path("recommendedDifficulty").asText()).isEqualTo("EASY");
        assertThat(state(c).path("recommendationReason").asText()).isEqualTo("NOT_ENOUGH_NEW_QUESTIONS");
    }
    @Test void reviewReusesAttemptSnapshotSaveAndSubmissionThenReschedulesOnce() throws Exception {
        var c = new Client().login(alice); submit(c, learn(c, lessonA), "no"); sync(c);
        var command = review("DUE", 5); var s = ok(c.call("POST", "/api/attempts", command), 201);
        var resumed = ok(c.call("POST", "/api/attempts", review("DUE", 5)), 200);
        assertThat(resumed.path("attemptId")).isEqualTo(s.path("attemptId"));
        assertThat(resumed.path("items")).isEqualTo(s.path("items"));
        var draft = answers(s, "yes");
        ok(c.call("PUT", "/api/attempts/" + s.path("attemptId").asLong() + "/draft", draft), 200);
        s = refresh(c, s); assertThat(s.path("items").get(0).path("response").path("optionId").asText()).isEqualTo("yes");
        submit(c, s, "yes"); sync(c);
        var p = state(c);
        assertThat(p.path("needsReview").asBoolean()).isFalse();
        assertThat(p.path("hasMistakes").asBoolean()).isFalse();
        assertThat(p.path("submissionCount").asInt()).isEqualTo(2);
        assertThat(Instant.parse(p.path("nextReviewAt").asText())).isAfter(Instant.now());
        assertThat(ok(c.call("POST", "/api/attempts", review("DUE", 5)), 409).path("code").asText()).isEqualTo("NO_QUESTIONS_AVAILABLE");
    }
    @Test void retiredLessonsLeaveHistoryButNoNewReviewQuestions() throws Exception {
        var c = new Client().login(alice); submit(c, learn(c, lessonA), "no"); sync(c);
        tx.executeWithoutResult(t -> lessonIds.forEach(id -> em.find(LessonEntity.class, id).retire()));
        assertThat(state(c).path("availableQuestionCount").asInt()).isZero();
        assertThat(dashboard(c).path("choices")).isEqualTo(node("{\"all\":0,\"priority\":0,\"wrong\":0}"));
        for (String kind : List.of("ALL", "PRIORITY", "WRONG"))
            assertThat(ok(c.call("POST", "/api/attempts", review(kind, 5)), 409).path("code").asText()).isEqualTo("NO_QUESTIONS_AVAILABLE");
        assertThat(ok(c.call("POST", "/api/attempts", review("DUE", 5)), 409).path("code").asText()).isEqualTo("NO_QUESTIONS_AVAILABLE");
    }
    @Test void failureAfterProjectionWriteRollsBackProgressLedgerAndReceipt() throws Exception {
        var c = new Client().login(alice); submit(c, learn(c, lessonA), "no");
        var command = Map.of("requestId", key());
        doThrow(new IllegalStateException("SIMULATED_M04_LEDGER_FAILURE")).when(reviews).processed(eq(alice.id()), anyLong());
        assertThat(ok(c.call("POST", "/api/me/review/sync", command), 503).path("code").asText()).isEqualTo("DATABASE_UNAVAILABLE");
        assertThat(dashboard(c).path("totalObjectives").asInt()).isZero();
        assertThat(dashboard(c).path("pendingSubmissions").asInt()).isEqualTo(1);
        doCallRealMethod().when(reviews).processed(eq(alice.id()), anyLong());
        ok(c.call("POST", "/api/me/review/sync", command), 200);
        assertThat(state(c).path("submissionCount").asInt()).isEqualTo(1);
    }
    @Test void mixedDifficultyLessonPrioritizesTheLowestLevelStillWrong() throws Exception {
        long mixed = tx.execute(t -> lesson(em.find(LessonEntity.class, lessonA).getUnitId(), objectiveA, 3, true));
        var c = new Client().login(alice); submit(c, learn(c, mixed), "no"); sync(c);
        assertThat(state(c).path("currentDifficulty").asText()).isEqualTo("EASY");
        var s = ok(c.call("POST", "/api/attempts", review("MISTAKES", 3)), 201);
        for (var item : s.path("items")) assertThat(item.path("difficulty").asText()).isEqualTo("EASY");
    }
    @Test void concurrentSyncCommandsProcessTheSubmissionOnce() throws Exception {
        var a = new Client().login(alice); var b = new Client().login(alice); submit(a, learn(a, lessonA), "no");
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> sync(a)); var two = executor.submit(() -> sync(b));
            assertThat(one.get().path("processedSubmissions").asInt() + two.get().path("processedSubmissions").asInt()).isEqualTo(1);
        } finally { executor.shutdownNow(); }
        assertThat(state(a).path("submissionCount").asInt()).isEqualTo(1);
    }
    @Test void allIncludesEveryPublishedVariantWithoutHistoryAndReviewOrPracticeErrorsDoNotBecomeRoundMistakes() throws Exception {
        var c = new Client().login(alice);
        assertThat(dashboard(c).path("choices")).isEqualTo(node("{\"all\":27,\"priority\":0,\"wrong\":0}"));
        var all = ok(c.call("POST", "/api/attempts", review("ALL", 10)), 201);
        var levels = new HashSet<String>();
        for (var item : all.path("items")) {
            assertThat(item.path("practiceRole").asText()).isEqualTo("REVIEW_VARIANT");
            assertThat(item.path("wasSeenBefore").asBoolean()).isFalse(); levels.add(item.path("difficulty").asText());
        }
        assertThat(levels).contains("EASY", "MEDIUM");
        submit(c, all, "no"); sync(c);
        var practice = ok(c.call("POST", "/api/attempts", Map.of("requestId", key(), "mode", "PRACTICE", "topicId", topic, "difficulty", "EASY", "count", 1)), 201);
        submit(c, practice, "no"); sync(c);
        assertThat(dashboard(c).path("choices").path("wrong").asInt()).isZero();
        assertThat(dashboard(c).path("choices").path("priority").asInt()).isZero();
        for (String kind : List.of("WRONG", "PRIORITY"))
            assertThat(ok(c.call("POST", "/api/attempts", review(kind, 10)), 409).path("code").asText()).isEqualTo("NO_QUESTIONS_AVAILABLE");
        // Completing one batch rotates to unused variants rather than repeating it.
        var next = ok(c.call("POST", "/api/attempts", review("ALL", 10)), 201);
        for (var item : next.path("items")) assertThat(item.path("wasSeenBefore").asBoolean()).isFalse();
    }
    @Test void wrongUsesOnlyOwnSubmittedRoundQuestionsAndClearsAfterReviewSuccessThenReopensAfterNewRoundErrors() throws Exception {
        var c = new Client().login(alice); var other = new Client().login(bob);
        var learned = learn(c, lessonA); submit(c, learned, "no"); sync(c);
        submit(other, learn(other, lessonB), "no"); sync(other);
        // A wrong check in an unfinished round is not a submitted result.
        var unfinished = learn(c, lessonA2);
        var check = json.createObjectNode().put("requestId", key()).put("baseRevision", 0);
        check.set("response", node("{\"type\":\"SINGLE_CHOICE\",\"optionId\":\"no\"}"));
        ok(c.call("POST", "/api/attempts/" + unfinished.path("attemptId").asLong() + "/items/" + unfinished.path("items").get(0).path("attemptQuestionId").asLong() + "/check", check), 200);
        assertThat(dashboard(c).path("choices")).isEqualTo(node("{\"all\":34,\"priority\":25,\"wrong\":7}"));
        var command = review("WRONG", 10); var wrong = ok(c.call("POST", "/api/attempts", command), 201);
        var expected = new HashSet<String>(); learned.path("items").forEach(i -> expected.add(i.path("originKey").asText()));
        var actual = new HashSet<String>();
        for (var item : wrong.path("items")) {
            actual.add(item.path("originKey").asText());
            assertThat(item.path("objectiveId").asLong()).isEqualTo(objectiveA);
            assertThat(item.path("practiceRole").asText()).isIn("GUIDED", "INDEPENDENT");
        }
        assertThat(actual).isEqualTo(expected);
        assertThat(wrong.path("items").size()).isEqualTo(7);
        var replay = c.call("POST", "/api/attempts", command);
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(ok(replay, 201)).isEqualTo(wrong);
        assertThat(ok(c.call("POST", "/api/attempts", review("WRONG", 10)), 200).path("attemptId")).isEqualTo(wrong.path("attemptId"));
        ok(other.call("GET", "/api/attempts/" + wrong.path("attemptId").asLong(), null), 404);
        submit(c, wrong, "yes"); sync(c);
        assertThat(dashboard(c).path("choices")).isEqualTo(node("{\"all\":27,\"priority\":0,\"wrong\":0}"));
        submit(c, learn(c, lessonA), "yes"); sync(c);
        assertThat(dashboard(c).path("choices").path("wrong").asInt()).isZero();
        submit(c, learn(c, lessonA), "no"); sync(c);
        assertThat(dashboard(c).path("choices").path("wrong").asInt()).isEqualTo(7);
    }
    @Test void priorityCombinesWrongRoundQuestionsWithOnlyVariantsOfTheirObjectivesAtEveryLevel() throws Exception {
        var c = new Client().login(alice); var learned = learn(c, lessonA); submit(c, learned, "no");
        assertThat(ok(c.call("POST", "/api/attempts", review("PRIORITY", 10)), 409).path("code").asText()).isEqualTo("REVIEW_SYNC_REQUIRED");
        sync(c);
        var priority = ok(c.call("POST", "/api/attempts", review("PRIORITY", 10)), 201);
        var origins = new HashSet<String>(); learned.path("items").forEach(i -> origins.add(i.path("originKey").asText()));
        int original = 0, variant = 0;
        for (var item : priority.path("items")) {
            assertThat(item.path("objectiveId").asLong()).isEqualTo(objectiveA);
            if (origins.contains(item.path("originKey").asText())) original++;
            else { assertThat(item.path("practiceRole").asText()).isEqualTo("REVIEW_VARIANT"); variant++; }
        }
        assertThat(original).isEqualTo(5); assertThat(variant).isEqualTo(5);
        assertThat(priority.path("items").size()).isEqualTo(10);
        assertThat(dashboard(c).toString()).doesNotContain("answerKey", "SECRET_", "transcript", "password", "storageKey");
        // Topic filtering applies to both counts and selection.
        var anotherTopic = tx.execute(t -> { var x = new TopicEntity(teacher.id(), key(), "Other", ""); em.persist(x); em.flush(); return x.getId(); });
        assertThat(ok(c.call("GET", "/api/me/review?topicId=" + anotherTopic, null), 200).path("choices").path("all").asInt()).isZero();
        assertThat(ok(c.call("POST", "/api/attempts", Map.of("requestId", key(), "mode", "REVIEW", "reviewKind", "ALL", "topicId", anotherTopic, "count", 10)), 409).path("code").asText()).isEqualTo("NO_QUESTIONS_AVAILABLE");
    }
    @Test void clearingRequiresCorrectSubmissionOfTheSameOriginNotVariantSuccessDraftOrLaterCorrection() throws Exception {
        var c = new Client().login(alice); submit(c, learn(c, lessonA), "no"); sync(c);
        var variant = ok(c.call("POST", "/api/attempts", review("ALL", 1)), 201);
        assertThat(variant.path("items").get(0).path("practiceRole").asText()).isEqualTo("REVIEW_VARIANT");
        submit(c, variant, "yes"); sync(c);
        assertThat(dashboard(c).path("choices").path("wrong").asInt()).isEqualTo(7);
        var pair = ok(c.call("POST", "/api/attempts", review("PRIORITY", 2)), 201);
        var guided = pair.path("items").get(0);
        assertThat(guided.path("practiceRole").asText()).isEqualTo("GUIDED");
        ok(c.call("PUT", "/api/attempts/" + pair.path("attemptId").asLong() + "/draft", answers(pair, "yes")), 200);
        assertThat(dashboard(c).path("choices").path("wrong").asInt()).isEqualTo(7);
        pair = refresh(c, pair);
        var check = json.createObjectNode().put("requestId", key()).put("baseRevision", pair.path("revision").asLong());
        check.set("response", node("{\"type\":\"SINGLE_CHOICE\",\"optionId\":\"no\"}"));
        ok(c.call("POST", "/api/attempts/" + pair.path("attemptId").asLong() + "/items/" + guided.path("attemptQuestionId").asLong() + "/check", check), 200);
        // A correct final answer clears even if the guided FIRST was wrong.
        submit(c, refresh(c, pair), "yes"); sync(c);
        assertThat(dashboard(c).path("choices").path("wrong").asInt()).isEqualTo(6);
        var failed = ok(c.call("POST", "/api/attempts", review("WRONG", 2)), 201);
        var body = answers(failed, "no"); ((ObjectNode) body.path("answers").get(1)).putNull("response");
        ok(c.call("POST", "/api/attempts/" + failed.path("attemptId").asLong() + "/submit", body), 200); sync(c);
        assertThat(dashboard(c).path("choices").path("wrong").asInt()).isEqualTo(6);
        failed = refresh(c, failed);
        var correction = json.createObjectNode().put("requestId", key()).put("baseRevision", failed.path("revision").asLong());
        correction.set("response", node("{\"type\":\"SINGLE_CHOICE\",\"optionId\":\"yes\"}"));
        ok(c.call("POST", "/api/attempts/" + failed.path("attemptId").asLong() + "/items/" + failed.path("items").get(0).path("attemptQuestionId").asLong() + "/retry", correction), 200);
        assertThat(dashboard(c).path("choices").path("wrong").asInt()).isEqualTo(6);
    }
    @Test void aSharedQuestionUsesItsVariantSlotWhenAllSelectsItAsAPublishedVariant() throws Exception {
        var learned = learn(new Client().login(alice), lessonA);
        long versionId = learned.path("items").get(0).path("questionVersionId").asLong();
        tx.executeWithoutResult(t -> {
            var root = new LessonEntity(teacher.id(), em.find(LessonEntity.class, lessonA).getUnitId(), 3); em.persist(root); em.flush();
            var data = new Lesson("Shared variant", "", node("[]"), List.of(objectiveA), List.of(new Slot(versionId, "REVIEW_VARIANT", 0)));
            var version = new LessonVersionEntity(root.getId(), 1, data); em.persist(version); em.flush();
            root.initialDraft(version); root.publish(); lessonIds.add(root.getId());
        });
        var c = new Client().login(bob);
        var all = ok(c.call("POST", "/api/attempts", review("ALL", 1)), 201);
        assertThat(all.path("items").get(0).path("questionVersionId").asLong()).isEqualTo(versionId);
        assertThat(all.path("items").get(0).path("practiceRole").asText()).isEqualTo("REVIEW_VARIANT");
        assertThat(dashboard(c).path("choices").path("all").asInt()).isEqualTo(28);
    }
}
