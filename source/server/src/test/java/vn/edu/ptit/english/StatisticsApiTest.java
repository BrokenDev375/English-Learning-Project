package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.dto.AttemptData.*;
import vn.edu.ptit.english.dto.AttemptViews.*;
import vn.edu.ptit.english.dto.AuthoringData.*;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.*;
import vn.edu.ptit.english.repository.*;
import static vn.edu.ptit.english.enums.AttemptEnums.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.demo.enabled=false", "app.bootstrap-admin.enabled=false", "app.gamification.worker-enabled=false"})
@Testcontainers
class StatisticsApiTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4").withDatabaseName("m07_statistics_test");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        r.add("spring.datasource.username", MYSQL::getUsername); r.add("spring.datasource.password", MYSQL::getPassword);
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired UserRepository users;
    @Autowired AttemptRepository attempts;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @Autowired vn.edu.ptit.english.security.LiveConnections live;
    TransactionTemplate tx;
    UserRecord alice, bob, teacher, admin;
    long topic, objective, secondObjective, lesson, lessonVersion, unitId;
    List<Long> questions = new ArrayList<>();
    @BeforeEach void fixture() {
        tx = new TransactionTemplate(transactions); questions.clear();
        tx.executeWithoutResult(status -> {
            // Keep the published journey isolated from earlier test fixtures.
            em.createQuery("select l from LessonEntity l where l.retiredAt is null",LessonEntity.class).getResultList().forEach(LessonEntity::retire);
            String prefix = UUID.randomUUID().toString(), hash = passwords.encode("StatsTest@12345");
            alice = users.create(prefix + "a@test.local", hash, "Alice M07", Role.STUDENT);
            bob = users.create(prefix + "b@test.local", hash, "Bob M07", Role.STUDENT);
            teacher = users.create(prefix + "t@test.local", hash, "Teacher M07", Role.TEACHER);
            admin = users.create(prefix + "x@test.local", hash, "Admin M07", Role.ADMIN);
            var t = new TopicEntity(teacher.id(), prefix, "Topic M07", ""); em.persist(t); em.flush(); topic = t.getId();
            var o = new ObjectiveEntity(teacher.id(), topic, prefix + "-a", "Present simple", "");
            var o2 = new ObjectiveEntity(teacher.id(), topic, prefix + "-b", "Vocabulary", "");
            em.persist(o); em.persist(o2); em.flush(); objective = o.getId(); secondObjective = o2.getId();
            var unit = new UnitEntity(teacher.id(), topic, "Unit M07", "", 1, 0,((Number)em.createQuery("select coalesce(max(u.roundNumber),0) from UnitEntity u").getSingleResult()).intValue()+1); em.persist(unit); em.flush();
            unitId = unit.getId();
            var slots = new ArrayList<Slot>();
            for (int i = 0; i < 6; i++) {
                var root = new QuestionEntity(teacher.id(), UUID.randomUUID().toString()); em.persist(root); em.flush();
                var data = new Question("SINGLE_CHOICE", topic, i == 5 ? secondObjective : objective,
                    i == 5 ? "HARD" : "EASY", "Question M07 " + i,
                    node("{\"options\":[{\"id\":\"yes\",\"text\":\"Yes\"},{\"id\":\"no\",\"text\":\"No\"}]}"),
                    node("{\"correctOptionId\":\"yes\"}"), "SECRET_EXPLANATION", "SECRET_HINT", null,
                    new MaterialData.Source("Fixture", null, null));
                var version = new QuestionVersionEntity(root.getId(), 1, data); em.persist(version); em.flush();
                root.initialDraft(version); root.publish(); questions.add(version.getId());
                slots.add(new Slot(version.getId(), i == 0 ? "GUIDED" : "INDEPENDENT", i == 0 ? 0 : i - 1));
            }
            var root = new LessonEntity(teacher.id(), unit.getId(), 0); em.persist(root); em.flush(); lesson = root.getId();
            var version = new LessonVersionEntity(lesson, 1, new Lesson("Lesson M07", "", node("[]"), List.of(objective, secondObjective), slots));
            em.persist(version); em.flush(); root.initialDraft(version); root.publish(); lessonVersion = version.getId();
        });
    }
    JsonNode node(String value) { try { return json.readTree(value); } catch (Exception e) { throw new IllegalArgumentException(e); } }
    record Sample(int question, PracticeRole role, Outcome outcome, boolean supported, int seen) {}
    Sample sample(int index, Outcome outcome) { return new Sample(index, PracticeRole.INDEPENDENT, outcome, false, 0); }
    long submitted(UserRecord user, Mode mode, Instant time, boolean completed, Sample... samples) {
        return submittedIn(lesson, lessonVersion, user, mode, time, completed, samples);
    }
    long submittedIn(long lesson, long lessonVersion, UserRecord user, Mode mode, Instant time, boolean completed, Sample... samples) {
        return tx.execute(status -> {
            var a = attempts.save(new AttemptEntity(user.id(), mode, mode == Mode.LEARN ? lessonVersion : null, node("{}"), PlayerState.empty()));
            var facts = new ArrayList<ResultItem>(); int correct = 0, blank = 0;
            for (var sample : samples) {
                long versionId = questions.get(sample.question());
                var item = attempts.save(new AttemptQuestionEntity(a.getId(), versionId, null, UUID.randomUUID().toString(),
                    sample.role(), facts.size(), new Display(List.of("yes", "no"), List.of()), sample.seen()));
                Answer response = sample.outcome() == Outcome.UNANSWERED ? null : new SingleAnswer(sample.outcome() == Outcome.CORRECT ? "yes" : "no");
                attempts.save(new AttemptAnswerEntity(item.getId(), response));
                attempts.save(new AnswerTrialEntity(item.getId(), 1, TrialKind.FIRST, TrialSource.SUBMIT, response,
                    sample.outcome(), response != null, sample.supported()));
                if (sample.outcome() == Outcome.CORRECT) correct++;
                if (sample.outcome() == Outcome.UNANSWERED) blank++;
                facts.add(new ResultItem(item.getId(), versionId, item.getOriginKey(), sample.question() == 5 ? secondObjective : objective,
                    sample.role(), sample.seen() > 0, response, sample.outcome(), response != null, sample.supported(), response,
                    sample.outcome(), response != null, node("{\"correctOptionId\":\"yes\"}"), "SECRET_EXPLANATION", null));
            }
            a.submit(time); a.changed(); attempts.save(a);
            attempts.save(new SubmissionEntity(new Result(1, 0, a.getId(), mode, a.getLessonVersionId(), time, a.getRevision(),
                facts.size(), facts.size() - blank, blank, correct, BigDecimal.valueOf(correct * 100.0 / facts.size()),
                0, 0, completed, facts, List.of())));
            if (mode == Mode.LEARN) {
                var progress = attempts.progress(new LessonProgressKey(user.id(), lesson), false)
                    .orElseGet(() -> new LessonProgressEntity(user.id(), lesson, a.getId()));
                progress.recordSubmission(a.getId(), lessonVersion, BigDecimal.valueOf(correct * 100.0 / facts.size()), completed, time);
                attempts.save(progress);
            }
            return a.getId();
        });
    }
    void extraTrial(long attemptId, TrialKind kind) {
        tx.executeWithoutResult(s -> {
            var item = attempts.items(attemptId).get(0);
            attempts.save(new AnswerTrialEntity(item.getId(), attempts.trials(item.getId()).size() + 1, kind, TrialSource.RETRY,
                new SingleAnswer("yes"), Outcome.CORRECT, true, true));
        });
    }
    class Client {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        HttpResponse<String> call(String method, String path, Object body, String csrf) throws Exception {
            var b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(45));
            if (body != null) b.header("Content-Type", "application/json");
            if (csrf != null) b.header("X-CSRF-TOKEN", csrf);
            return http.send(b.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        }
        Client login(UserRecord user) throws Exception {
            String csrf = json.readTree(call("GET", "/api/auth/csrf", null, null).body()).path("token").asText();
            assertThat(call("POST", "/api/auth/login", Map.of("email", user.email(), "password", "StatsTest@12345"), csrf).statusCode()).isEqualTo(200);
            return this;
        }
        HttpResponse<String> get(String path) throws Exception { return call("GET", path, null, null); }
        JsonNode read(String path) throws Exception {
            var response = get(path); assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
            assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store", "private");
            assertThat(response.body()).doesNotContain("SECRET_", "answerKey", "passwordHash", "storageKey", "securityVersion");
            return json.readTree(response.body());
        }
    }
    @Test void adminPresenceIsPrivateReadOnlyAndRestrictedToCurrentAdmins() throws Exception {
        String path = "/api/admin/statistics/presence";
        assertThat(new Client().get(path).statusCode()).isEqualTo(401);
        for (var user : List.of(alice, teacher)) assertThat(new Client().login(user).get(path).statusCode()).isEqualTo(403);
        var data = new Client().login(admin).read(path);
        assertThat(data.size()).isEqualTo(5);
        assertThat(data.path("users").isArray()).isTrue();
        assertThat(data.path("users").size()).isEqualTo(data.path("connectedAccounts").asInt());
        assertThat(data.path("basis").asText()).isEqualTo("OPEN_AUTHENTICATED_SOCKETS");
        assertThat(Instant.parse(data.path("generatedAt").asText())).isBeforeOrEqualTo(Instant.now());
        assertThat(data.path("connectedAccounts").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(data.path("openConnections").asLong()).isGreaterThanOrEqualTo(data.path("connectedAccounts").asLong());
    }
    @Test void adminPresenceListsEachActiveConnectedAccountOnceWithoutSensitiveFields() throws Exception {
        var first=org.mockito.Mockito.mock(org.springframework.web.socket.WebSocketSession.class);
        var second=org.mockito.Mockito.mock(org.springframework.web.socket.WebSocketSession.class);
        var locked=org.mockito.Mockito.mock(org.springframework.web.socket.WebSocketSession.class);
        org.mockito.Mockito.when(first.getId()).thenReturn("online-alice-1"); org.mockito.Mockito.when(first.isOpen()).thenReturn(true);
        org.mockito.Mockito.when(second.getId()).thenReturn("online-alice-2"); org.mockito.Mockito.when(second.isOpen()).thenReturn(true);
        org.mockito.Mockito.when(locked.getId()).thenReturn("online-bob"); org.mockito.Mockito.when(locked.isOpen()).thenReturn(true);
        live.attach(first,vn.edu.ptit.english.security.UserPrincipal.from(alice),"online-test");
        live.attach(second,vn.edu.ptit.english.security.UserPrincipal.from(alice),"online-test");
        live.attach(locked,vn.edu.ptit.english.security.UserPrincipal.from(bob),"online-test");
        try {
            var c=new Client().login(admin); var data=c.read("/api/admin/statistics/presence");
            assertThat(data.path("users")).hasSize(2); assertThat(data.path("openConnections").asInt()).isEqualTo(3);
            var ids=new java.util.HashSet<Long>(); data.path("users").forEach(u->{ids.add(u.path("id").asLong());assertThat(u.size()).isEqualTo(3);assertThat(u.path("fullName").asText()).isNotBlank();assertThat(u.path("role").asText()).isEqualTo("STUDENT");});
            assertThat(ids).containsExactlyInAnyOrder(alice.id(),bob.id());
            assertThat(data.toString()).doesNotContain("email","password","securityVersion");
            tx.executeWithoutResult(s->em.find(UserEntity.class,bob.id()).changeStatus(AccountStatus.LOCKED));
            var updated=c.read("/api/admin/statistics/presence");assertThat(updated.path("users")).hasSize(1);
            assertThat(updated.path("users").get(0).path("id").asLong()).isEqualTo(alice.id());
            org.mockito.Mockito.when(first.isOpen()).thenReturn(false); org.mockito.Mockito.when(second.isOpen()).thenReturn(false);
            assertThat(c.read("/api/admin/statistics/presence").path("users")).isEmpty();
        } finally { live.detach("online-alice-1");live.detach("online-alice-2");live.detach("online-bob"); }
    }
    @Test void emptyStatisticsHaveNullRatesAndUnavailableRewardsWithoutWritingData() throws Exception {
        var c = new Client().login(alice);
        var before = tx.execute(s -> em.createQuery("select count(p.id) from ReviewProgressEntity p", Long.class).getSingleResult());
        var value = c.read("/api/me/statistics");
        assertThat(value.path("activity").path("submittedAttempts").asLong()).isZero();
        assertThat(value.path("performance").path("first").path("accuracyPercent").isNull()).isTrue();
        assertThat(value.path("rewards").path("state").asText()).isEqualTo("AVAILABLE");
        assertThat(value.path("rewards").path("totalXp").asLong()).isZero();
        assertThat(c.read("/api/me/statistics/breakdown").path("totalElements").asInt()).isZero();
        assertThat(c.read("/api/me/statistics/lessons").path("totalElements").asInt()).isZero();
        Long after = tx.execute(s -> em.createQuery("select count(p.id) from ReviewProgressEntity p", Long.class).getSingleResult());
        assertThat(after).isEqualTo(before);
    }
    @Test void separatesFirstSupportedRepeatedAndCorrectionEvidenceAndExcludesOpenAttempts() throws Exception {
        long a = submitted(alice, Mode.LEARN, Instant.now().minusSeconds(30), false,
            new Sample(0, PracticeRole.GUIDED, Outcome.INCORRECT, true, 0),
            new Sample(1, PracticeRole.INDEPENDENT, Outcome.CORRECT, true, 0),
            sample(2, Outcome.CORRECT), sample(3, Outcome.UNANSWERED),
            new Sample(4, PracticeRole.INDEPENDENT, Outcome.INCORRECT, false, 1));
        extraTrial(a, TrialKind.GUIDED_RETRY); extraTrial(a, TrialKind.CORRECTION);
        tx.executeWithoutResult(s -> {
            var open = attempts.save(new AttemptEntity(alice.id(), Mode.PRACTICE, null, node("{}"), PlayerState.empty()));
            var i = attempts.save(new AttemptQuestionEntity(open.getId(), questions.get(2), null, UUID.randomUUID().toString(), PracticeRole.INDEPENDENT, 0, new Display(List.of(), List.of()), 0));
            attempts.save(new AnswerTrialEntity(i.getId(), 1, TrialKind.FIRST, TrialSource.CHECK, new SingleAnswer("no"), Outcome.INCORRECT, true, false));
        });
        var value = new Client().login(alice).read("/api/me/statistics"); var p = value.path("performance");
        assertThat(p.path("first").path("total").asInt()).isEqualTo(5);
        assertThat(p.path("first").path("accuracyPercent").asDouble()).isEqualTo(50);
        assertThat(p.path("guided").path("incorrect").asInt()).isEqualTo(1);
        assertThat(p.path("assisted").path("correct").asInt()).isEqualTo(1);
        assertThat(p.path("independentNew").path("total").asInt()).isEqualTo(2);
        assertThat(p.path("independentSeen").path("incorrect").asInt()).isEqualTo(1);
        assertThat(p.path("guidedRetries").path("correct").asInt()).isEqualTo(1);
        assertThat(p.path("corrections").path("correct").asInt()).isEqualTo(1);
        assertThat(value.path("activity").path("activeAttempts").asInt()).isEqualTo(1);
        assertThat(value.path("review").path("pendingSubmissions").asInt()).isEqualTo(1);
    }
    @Test void isolatesStudentsAndRejectsOtherRolesAndUnauthenticatedRequests() throws Exception {
        submitted(alice, Mode.PRACTICE, Instant.now().minusSeconds(1), false, sample(2, Outcome.INCORRECT));
        var a = new Client().login(alice); var b = new Client().login(bob);
        assertThat(a.read("/api/me/statistics").path("activity").path("submittedAttempts").asInt()).isEqualTo(1);
        assertThat(b.read("/api/me/statistics").path("activity").path("submittedAttempts").asInt()).isZero();
        assertThat(b.read("/api/me/statistics?userId=" + alice.id()).path("activity").path("submittedAttempts").asInt()).isZero();
        assertThat(new Client().get("/api/me/statistics").statusCode()).isEqualTo(401);
        assertThat(a.get("/api/admin/statistics").statusCode()).isEqualTo(403);
        var t = new Client().login(teacher); var x = new Client().login(admin);
        assertThat(t.get("/api/me/statistics").statusCode()).isEqualTo(403);
        assertThat(t.get("/api/admin/statistics/questions").statusCode()).isEqualTo(403);
        assertThat(x.get("/api/me/statistics").statusCode()).isEqualTo(403);
        tx.executeWithoutResult(s -> em.find(UserEntity.class, alice.id()).changeStatus(AccountStatus.LOCKED));
        assertThat(a.get("/api/me/statistics").statusCode()).isEqualTo(401);
    }
    @Test void appliesPeriodAndKeepsLessonHistoryAfterRetirementWithoutCountingRetakesTwice() throws Exception {
        submitted(alice, Mode.LEARN, Instant.now().minus(Duration.ofDays(40)), true, sample(2, Outcome.CORRECT));
        submitted(alice, Mode.LEARN, Instant.now().minusSeconds(30), false, sample(2, Outcome.INCORRECT));
        submitted(alice, Mode.REVIEW, Instant.now().minusSeconds(20), false, sample(5, Outcome.CORRECT));
        tx.executeWithoutResult(s -> em.find(LessonEntity.class, lesson).retire());
        var c = new Client().login(alice);
        assertThat(c.read("/api/me/statistics?period=7D").path("activity").path("submittedAttempts").asInt()).isEqualTo(2);
        assertThat(c.read("/api/me/statistics").path("activity").path("submittedAttempts").asInt()).isEqualTo(3);
        var list = c.read("/api/me/statistics/lessons?size=1");
        assertThat(list.path("totalElements").asInt()).isEqualTo(1);
        assertThat(list.path("items").get(0).path("title").asText()).isEqualTo("Lesson M07");
        assertThat(list.path("items").get(0).path("lastScorePercent").asDouble()).isZero();
        assertThat(c.read("/api/me/statistics?period=7D").path("activity").path("completedLessons").asInt()).isEqualTo(1);
    }
    @Test void breakdownIsPagedAndKeepsObjectiveAndTopicTotalsConsistent() throws Exception {
        submitted(alice, Mode.PRACTICE, Instant.now().minusSeconds(1), false, sample(2, Outcome.CORRECT), sample(5, Outcome.INCORRECT));
        var c = new Client().login(alice);
        var topics = c.read("/api/me/statistics/breakdown");
        assertThat(topics.path("items").get(0).path("performance").path("first").path("total").asInt()).isEqualTo(2);
        var page0 = c.read("/api/me/statistics/breakdown?groupBy=OBJECTIVE&size=1");
        var page1 = c.read("/api/me/statistics/breakdown?groupBy=OBJECTIVE&size=1&page=1");
        assertThat(page0.path("totalElements").asInt()).isEqualTo(2);
        assertThat(page0.path("items").get(0).path("id").asLong()).isNotEqualTo(page1.path("items").get(0).path("id").asLong());
        assertThat(c.read("/api/me/statistics/breakdown?groupBy=OBJECTIVE&page=9").path("items").isEmpty()).isTrue();
    }
    @Test void adminAggregatesByPinnedQuestionVersionAndFiltersDifficultyWithoutPersonalAnswers() throws Exception {
        submitted(alice, Mode.PRACTICE, Instant.now().minusSeconds(1), false, sample(2, Outcome.INCORRECT), sample(5, Outcome.INCORRECT));
        long correct = submitted(bob, Mode.PRACTICE, Instant.now().minusSeconds(1), false, sample(2, Outcome.CORRECT));
        extraTrial(correct, TrialKind.CORRECTION);
        var x = new Client().login(admin); var overview = x.read("/api/admin/statistics?period=7D");
        assertThat(overview.path("difficulties")).hasSize(3);
        assertThat(overview.path("presence").path("basis").asText()).isEqualTo("OPEN_AUTHENTICATED_SOCKETS");
        var list = x.read("/api/admin/statistics/questions?difficulty=EASY&size=50");
        JsonNode own = null;
        for (var item : list.path("items")) if (item.path("questionVersionId").asLong() == questions.get(2)) own = item;
        assertThat(own).isNotNull();
        assertThat(own.path("first").path("incorrect").asInt()).isEqualTo(1);
        assertThat(own.path("first").path("correct").asInt()).isEqualTo(1);
        assertThat(own.path("learners").asInt()).isEqualTo(2);
        for (var item : list.path("items")) assertThat(item.path("difficulty").asText()).isEqualTo("EASY");
    }
    @Test void invalidFiltersAndOverflowPagesReturn400() throws Exception {
        var c = new Client().login(alice); var x = new Client().login(admin);
        for (String path : List.of("/api/me/statistics?period=BAD", "/api/me/statistics/breakdown?groupBy=BAD",
            "/api/me/statistics/breakdown?page=-1", "/api/me/statistics/lessons?size=51",
            "/api/me/statistics/lessons?page=2147483647&size=50")) assertThat(c.get(path).statusCode()).isEqualTo(400);
        assertThat(x.get("/api/admin/statistics/questions?difficulty=BAD").statusCode()).isEqualTo(400);
        assertThat(x.get("/api/admin/statistics?period=BAD").statusCode()).isEqualTo(400);
    }
    UserRecord anotherTeacher() {
        return tx.execute(s -> users.create(UUID.randomUUID() + "@test.local", passwords.encode("StatsTest@12345"), "Other teacher", Role.TEACHER));
    }
    long[] additionalLesson(long unitOwner, long lessonOwner, String title) {
        return tx.execute(s -> {
            var u = new UnitEntity(unitOwner, topic, title + " Unit", "", 1, 1, ((Number)em.createQuery("select coalesce(max(u.roundNumber),0) from UnitEntity u").getSingleResult()).intValue()+1); em.persist(u); em.flush();
            var l = new LessonEntity(lessonOwner, u.getId(), 0); em.persist(l); em.flush();
            var v = new LessonVersionEntity(l.getId(), 1, new Lesson(title, "", node("[]"), List.of(objective),
                List.of(new Slot(questions.get(2), "INDEPENDENT", 0))));
            em.persist(v); em.flush(); l.initialDraft(v); l.publish();
            return new long[]{u.getId(), l.getId(), v.getId()};
        });
    }
    @Test void teacherOnlyCountsLearnAttemptsInsideOwnedUnitsEvenWhenQuestionsAreShared() throws Exception {
        var other = anotherTeacher();
        // Lesson owner deliberately differs: the Unit owner is the authority.
        long[] foreign = additionalLesson(other.id(), teacher.id(), "Other owner's");
        submitted(alice, Mode.LEARN, Instant.now().minusSeconds(1), true, sample(2, Outcome.INCORRECT));
        submittedIn(foreign[1], foreign[2], bob, Mode.LEARN, Instant.now().minusSeconds(1), true, sample(2, Outcome.CORRECT));
        submitted(alice, Mode.PRACTICE, Instant.now().minusSeconds(1), false, sample(2, Outcome.CORRECT));
        submitted(bob, Mode.REVIEW, Instant.now().minusSeconds(1), false, sample(2, Outcome.CORRECT));
        var c = new Client().login(teacher); var data = c.read("/api/teacher/statistics");
        assertThat(data.path("units").asInt()).isEqualTo(1);
        assertThat(data.path("learnersStarted").asInt()).isEqualTo(1);
        assertThat(data.path("activity").path("submittedAttempts").asInt()).isEqualTo(1);
        assertThat(data.path("activity").path("completedLessons").asInt()).isEqualTo(1);
        assertThat(data.path("performance").path("first").path("total").asInt()).isEqualTo(1);
        assertThat(data.path("performance").path("first").path("accuracyPercent").asInt()).isZero();
        var errors = c.read("/api/teacher/statistics/questions").path("items");
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).path("first").path("incorrect").asInt()).isEqualTo(1);
        assertThat(errors.get(0).path("first").path("correct").asInt()).isZero();
        assertThat(errors.get(0).path("learners").asInt()).isEqualTo(1);
        assertThat(c.read("/api/teacher/statistics?teacherId=" + other.id()).path("units").asInt()).isEqualTo(1);
        var otherData = new Client().login(other).read("/api/teacher/statistics");
        assertThat(otherData.path("performance").path("first").path("correct").asInt()).isEqualTo(1);
        String output = data.toString() + errors;
        assertThat(output).doesNotContain(alice.email(), bob.email(), "Alice M07", "Bob M07", "userId", "attemptId", "answerKey", "response", "SECRET_");
    }
    @Test void teacherCannotRequestForeignOrMissingUnitAndOtherRolesCannotUseTeacherEndpoints() throws Exception {
        var other = anotherTeacher(); long[] foreign = additionalLesson(other.id(), other.id(), "Private");
        var c = new Client().login(teacher);
        for (String endpoint : List.of("", "/lessons", "/questions", "/breakdown")) {
            assertThat(c.get("/api/teacher/statistics" + endpoint + "?unitId=" + foreign[0]).statusCode()).isEqualTo(404);
            assertThat(c.get("/api/teacher/statistics" + endpoint + "?unitId=9223372036854775807").statusCode()).isEqualTo(404);
        }
        assertThat(c.read("/api/teacher/statistics/units").toString()).doesNotContain("Private");
        for (var user : List.of(alice, admin)) {
            var denied = new Client().login(user);
            for (String endpoint : List.of("", "/units", "/lessons", "/questions", "/breakdown"))
                assertThat(denied.get("/api/teacher/statistics" + endpoint).statusCode()).isEqualTo(403);
        }
        assertThat(new Client().get("/api/teacher/statistics").statusCode()).isEqualTo(401);
        tx.executeWithoutResult(s -> em.find(UserEntity.class, teacher.id()).changeStatus(AccountStatus.LOCKED));
        assertThat(c.get("/api/teacher/statistics").statusCode()).isEqualTo(401);
    }
    @Test void teacherWithoutUnitsSeesEmptyReadOnlyStatistics() throws Exception {
        var c = new Client().login(anotherTeacher());
        long before = tx.execute(s -> em.createQuery("select count(p.id) from ReviewProgressEntity p", Long.class).getSingleResult());
        var value = c.read("/api/teacher/statistics");
        assertThat(value.path("units").asInt()).isZero();
        assertThat(value.path("activity").path("submittedAttempts").asInt()).isZero();
        assertThat(value.path("performance").path("first").path("accuracyPercent").isNull()).isTrue();
        for (String endpoint : List.of("/units", "/lessons", "/questions", "/breakdown"))
            assertThat(c.read("/api/teacher/statistics" + endpoint).path("totalElements").asInt()).isZero();
        Long after = tx.execute(s -> em.createQuery("select count(p.id) from ReviewProgressEntity p", Long.class).getSingleResult());
        assertThat(after).isEqualTo(before);
    }
    @Test void teacherPeriodsKeepRetiredLessonVersionsAndDoNotCountRetakesAsNewCompletions() throws Exception {
        submitted(alice, Mode.LEARN, Instant.now().minus(Duration.ofDays(40)), true, sample(2, Outcome.CORRECT));
        submitted(alice, Mode.LEARN, Instant.now().minusSeconds(2), false, sample(2, Outcome.INCORRECT));
        tx.executeWithoutResult(s -> em.find(LessonEntity.class, lesson).retire());
        var c = new Client().login(teacher);
        assertThat(c.read("/api/teacher/statistics").path("activity").path("submittedAttempts").asInt()).isEqualTo(2);
        var recent = c.read("/api/teacher/statistics?period=7D&unitId=" + unitId);
        assertThat(recent.path("activity").path("submittedAttempts").asInt()).isEqualTo(1);
        assertThat(recent.path("activity").path("completedLessons").asInt()).isEqualTo(1);
        assertThat(recent.path("learnersStarted").asInt()).isEqualTo(1);
        var l = c.read("/api/teacher/statistics/lessons?period=7D&unitId=" + unitId).path("items").get(0);
        assertThat(l.path("lessonId").asLong()).isEqualTo(lesson);
        assertThat(l.path("submittedAttempts").asInt()).isEqualTo(1);
        assertThat(l.path("completedLearners").asInt()).isEqualTo(1);
        assertThat(l.path("performance").path("first").path("incorrect").asInt()).isEqualTo(1);
        assertThat(c.read("/api/teacher/statistics?period=30D").path("activity").path("submittedAttempts").asInt()).isEqualTo(1);
        assertThat(c.read("/api/teacher/statistics?period=90D").path("activity").path("submittedAttempts").asInt()).isEqualTo(2);
    }
    @Test void teacherSeparatesSupportBlankRetriesAndCorrectionsAndExcludesUnsubmittedEvidence() throws Exception {
        long attempt = submitted(alice, Mode.LEARN, Instant.now().minusSeconds(2), false,
            new Sample(0, PracticeRole.GUIDED, Outcome.INCORRECT, true, 0),
            new Sample(1, PracticeRole.INDEPENDENT, Outcome.CORRECT, true, 0),
            sample(2, Outcome.CORRECT), sample(3, Outcome.UNANSWERED),
            new Sample(5, PracticeRole.INDEPENDENT, Outcome.INCORRECT, false, 1));
        extraTrial(attempt, TrialKind.GUIDED_RETRY); extraTrial(attempt, TrialKind.CORRECTION);
        tx.executeWithoutResult(s -> {
            attempts.items(attempt).get(0).openHint(Instant.now());
            var open = attempts.save(new AttemptEntity(bob.id(), Mode.LEARN, lessonVersion, node("{}"), PlayerState.empty()));
            var i = attempts.save(new AttemptQuestionEntity(open.getId(), questions.get(2), null, UUID.randomUUID().toString(), PracticeRole.INDEPENDENT, 0, new Display(List.of(), List.of()), 0));
            attempts.save(new AnswerTrialEntity(i.getId(), 1, TrialKind.FIRST, TrialSource.CHECK, new SingleAnswer("yes"), Outcome.CORRECT, true, false));
        });
        var c = new Client().login(teacher); var value = c.read("/api/teacher/statistics");
        var p = value.path("performance");
        assertThat(p.path("first").path("total").asInt()).isEqualTo(5);
        assertThat(p.path("first").path("unanswered").asInt()).isEqualTo(1);
        assertThat(p.path("first").path("accuracyPercent").asDouble()).isEqualTo(50);
        assertThat(p.path("guided").path("total").asInt()).isEqualTo(1);
        assertThat(p.path("assisted").path("total").asInt()).isEqualTo(1);
        assertThat(p.path("independentNew").path("total").asInt()).isEqualTo(2);
        assertThat(p.path("independentSeen").path("total").asInt()).isEqualTo(1);
        assertThat(p.path("guidedRetries").path("total").asInt()).isEqualTo(1);
        assertThat(p.path("corrections").path("total").asInt()).isEqualTo(1);
        assertThat(value.path("hintOpenedQuestions").asInt()).isEqualTo(1);
        assertThat(value.path("activity").path("activeAttempts").asInt()).isEqualTo(1);
        assertThat(value.path("learnersStarted").asInt()).isEqualTo(2);
        assertThat(c.read("/api/teacher/statistics/breakdown?groupBy=OBJECTIVE&size=1").path("totalElements").asInt()).isEqualTo(2);
        assertThat(c.read("/api/teacher/statistics/questions?difficulty=HARD").path("items").get(0).path("questionVersionId").asLong()).isEqualTo(questions.get(5));
        assertThat(c.read("/api/teacher/statistics/lessons").path("items").get(0).path("hintOpenedQuestions").asInt()).isEqualTo(1);
    }
    @Test void teacherUnitLessonAndQuestionPaginationStayWithinTheOwnerScope() throws Exception {
        long[] owned = additionalLesson(teacher.id(), teacher.id(), "Second");
        var other = anotherTeacher(); additionalLesson(other.id(), other.id(), "Excluded");
        submitted(alice, Mode.LEARN, Instant.now().minusSeconds(1), false, sample(2, Outcome.INCORRECT));
        submittedIn(owned[1], owned[2], bob, Mode.LEARN, Instant.now().minusSeconds(1), false, sample(2, Outcome.CORRECT));
        var c = new Client().login(teacher);
        var u0 = c.read("/api/teacher/statistics/units?size=1");
        var u1 = c.read("/api/teacher/statistics/units?size=1&page=1");
        assertThat(u0.path("totalElements").asInt()).isEqualTo(2);
        assertThat(u0.path("items").get(0).path("unitId").asLong()).isNotEqualTo(u1.path("items").get(0).path("unitId").asLong());
        assertThat(c.read("/api/teacher/statistics/lessons?size=1").path("totalElements").asInt()).isEqualTo(2);
        assertThat(c.read("/api/teacher/statistics/lessons?unitId=" + owned[0]).path("items").get(0).path("title").asText()).isEqualTo("Second");
        assertThat(c.read("/api/teacher/statistics/questions?unitId=" + owned[0]).path("totalElements").asInt()).isZero();
        assertThat(c.read("/api/teacher/statistics/breakdown?unitId=" + owned[0]).path("items").get(0).path("performance").path("first").path("correct").asInt()).isEqualTo(1);
        assertThat(c.read("/api/teacher/statistics/lessons?page=9").path("items").isEmpty()).isTrue();
    }
    @Test void teacherRejectsInvalidFiltersAndPagination() throws Exception {
        var c = new Client().login(teacher);
        for (String path : List.of("?unitId=0", "?unitId=-1", "?unitId=bad", "?period=BAD",
            "/units?page=-1", "/units?size=51", "/lessons?page=2147483647&size=50", "/questions?difficulty=BAD",
            "/questions?size=0", "/breakdown?groupBy=BAD", "/breakdown?size=51", "/lessons?period=BAD"))
            assertThat(c.get("/api/teacher/statistics" + path).statusCode()).withFailMessage(path).isEqualTo(400);
    }
    @Test void teacherKeepsPinnedQuestionVersionsAfterTheLessonUsesANewerVersion() throws Exception {
        long originalQuestion = questions.get(2);
        submitted(alice, Mode.LEARN, Instant.now().minusSeconds(2), false, sample(2, Outcome.INCORRECT));
        long[] newer = tx.execute(s -> {
            var previous = em.find(QuestionVersionEntity.class, originalQuestion);
            var q = new QuestionVersionEntity(previous.getQuestionId(), 2, previous.data()); q.publish(); em.persist(q); em.flush();
            var v = new LessonVersionEntity(lesson, 2, new Lesson("Revised lesson", "", node("[]"), List.of(objective),
                List.of(new Slot(q.getId(), "INDEPENDENT", 0))));
            v.publish(); em.persist(v); em.flush();
            return new long[]{q.getId(), v.getId()};
        });
        questions.set(2, newer[0]);
        submittedIn(lesson, newer[1], bob, Mode.LEARN, Instant.now().minusSeconds(1), false, sample(2, Outcome.INCORRECT));
        var c = new Client().login(teacher);
        var items = c.read("/api/teacher/statistics/questions?unitId=" + unitId).path("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).path("questionVersionId").asLong()).isEqualTo(originalQuestion);
        assertThat(items.get(1).path("questionVersionId").asLong()).isEqualTo(newer[0]);
        for (var item : items) assertThat(item.path("first").path("incorrect").asInt()).isEqualTo(1);
        assertThat(c.read("/api/teacher/statistics/lessons").path("items").get(0).path("title").asText()).isEqualTo("Revised lesson");
    }
}
