package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.dto.AttemptData.*;
import vn.edu.ptit.english.dto.AuthoringData.*;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.enums.AttemptEnums.*;
import vn.edu.ptit.english.repository.*;
import static org.assertj.core.api.Assertions.*;

@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"app.demo.enabled=false","app.bootstrap-admin.enabled=false"})
@Testcontainers
class AttemptPersistenceTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("m03_upgrade_test");
    static boolean prepared;
    @DynamicPropertySource static synchronized void database(DynamicPropertyRegistry r)throws Exception {
        if(!prepared) {
            Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword()).target("3").load().migrate();
            try(var c=MYSQL.createConnection(""); var s=c.createStatement()) {
                s.executeUpdate("INSERT INTO users(email,password_hash,full_name,role) VALUES('v3-preserved@test.local','hash','V3 preserved','STUDENT')");
                s.executeUpdate("INSERT INTO topics(owner_id,code,name) VALUES(LAST_INSERT_ID(),'v3-preserved','V3 preserved')");
            }
            prepared=true;
        }
        r.add("spring.datasource.url",MYSQL::getJdbcUrl); r.add("spring.datasource.username",MYSQL::getUsername); r.add("spring.datasource.password",MYSQL::getPassword);
    }
    @Autowired AttemptRepository repo;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transactions;
    TransactionTemplate tx;
    long user,unit,lesson,lessonVersion,questionVersion,slot;
    String origin;
    @BeforeEach void fixture() {
        tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(s -> {
            user=users.create(UUID.randomUUID()+"@m03.test","hash","M03 learner",Role.STUDENT).id();
            var topic=new TopicEntity(user,UUID.randomUUID().toString(),"Topic",""); em.persist(topic); em.flush();
            var objective=new ObjectiveEntity(user,topic.getId(),UUID.randomUUID().toString(),"Objective",""); em.persist(objective); em.flush();
            var u=new UnitEntity(user,topic.getId(),"Unit","",1,0,((Number)em.createQuery("select coalesce(max(u.roundNumber),0) from UnitEntity u").getSingleResult()).intValue()+1); em.persist(u); em.flush(); unit=u.getId();
            origin=UUID.randomUUID().toString(); var q=new QuestionEntity(user,origin); em.persist(q); em.flush();
            var data=new Question("SINGLE_CHOICE",topic.getId(),objective.getId(),"EASY","Choose",node("{\"options\":[{\"id\":\"a\",\"text\":\"A\"},{\"id\":\"b\",\"text\":\"B\"}]}"),node("{\"correctOptionId\":\"b\"}"),"Explanation","Hint",null,new MaterialData.Source("Test",null,null));
            var qv=new QuestionVersionEntity(q.getId(),1,data); em.persist(qv); em.flush(); q.initialDraft(qv); q.publish(); questionVersion=qv.getId();
            var l=new LessonEntity(user,unit,0); em.persist(l); em.flush(); lesson=l.getId();
            var lv=new LessonVersionEntity(lesson,1,new Lesson("Lesson","",node("[{\"kind\":\"TEXT\",\"text\":\"Read\"}]"),List.of(objective.getId()),List.of(new Slot(questionVersion,"GUIDED",0))));
            em.persist(lv); em.flush(); l.initialDraft(lv); l.publish(); em.flush(); lessonVersion=lv.getId();
            slot=((Number)em.createNativeQuery("SELECT id FROM lesson_questions WHERE lesson_version_id=:v").setParameter("v",lessonVersion).getSingleResult()).longValue();
        });
    }
    JsonNode node(String text){try{return json.readTree(text);}catch(Exception e){throw new IllegalArgumentException(e);}}
    AttemptEntity attempt() {
        return repo.save(new AttemptEntity(user,Mode.LEARN,lessonVersion,json.valueToTree(Map.of("mode","LEARN","lessonId",lesson)),PlayerState.empty()));
    }
    AttemptQuestionEntity item(AttemptEntity a) {
        return repo.save(new AttemptQuestionEntity(a.getId(),questionVersion,slot,origin,PracticeRole.GUIDED,0,new Display(List.of("b","a"),List.of()),0));
    }
    AttemptViews.Result result(AttemptEntity a,long item,Instant now) {
        var ri=new AttemptViews.ResultItem(item,questionVersion,origin,1,PracticeRole.GUIDED,false,
            new SingleAnswer("a"),Outcome.INCORRECT,true,false,new SingleAnswer("b"),Outcome.CORRECT,true,node("{\"correctOptionId\":\"b\"}"),"Explanation",null);
        return new AttemptViews.Result(1,0,a.getId(),Mode.LEARN,lessonVersion,now,1,1,1,0,1,
            new BigDecimal("100.00"),0,0,true,List.of(ri),List.of());
    }
    @Test void v3RowsSurviveUpgradeAndHibernateValidatesCurrentSchema() {
        assertThat(users.findByEmail("v3-preserved@test.local")).isPresent();
        tx.executeWithoutResult(s -> {
            assertThat(((Number)em.createNativeQuery("SELECT COUNT(*) FROM topics WHERE code='v3-preserved'").getSingleResult()).intValue()).isEqualTo(1);
            assertThat(em.createNativeQuery("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1").getSingleResult()).isEqualTo("17");
        });
    }
    @Test void emptyDatabaseMigratesThroughCurrentVersion() {
        try(var fresh=new MySQLContainer<>("mysql:8.4")) {
            fresh.start();
            var flyway=Flyway.configure().dataSource(fresh.getJdbcUrl(),fresh.getUsername(),fresh.getPassword()).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(17);
            assertThat(flyway.info().current().getVersion().toString()).isEqualTo("17");
            flyway.validate();
        }
    }
    @Test void allSevenTablesRoundTripAndQueriesRespectOwnerAndFilters() {
        long attemptId=tx.execute(s -> {
            var a=attempt(); var i=item(a); var now=Instant.now();
            repo.save(new AttemptAnswerEntity(i.getId(),new SingleAnswer("b")));
            repo.save(new AnswerTrialEntity(i.getId(),1,TrialKind.FIRST,TrialSource.CHECK,new SingleAnswer("a"),Outcome.INCORRECT,true,false));
            i.openHint(now); i.openFeedback(now);
            repo.save(i);
            a.savePlayerState(new PlayerState(i.getId(),List.of(new AudioPosition(5,1.25)))); a.submit(now); a.changed(); repo.save(a);
            var submission=repo.save(new SubmissionEntity(result(a,i.getId(),now)));
            assertThat(submission.getResult().submissionId()).isEqualTo(submission.getId());
            repo.save(new QuestionExposureEntity(user,origin,a.getId(),now));
            var p=new LessonProgressEntity(user,lesson,a.getId()); p.recordSubmission(a.getId(),lessonVersion,new BigDecimal("100.00"),true,now); repo.save(p);
            return a.getId();
        });
        tx.executeWithoutResult(s -> {
            em.clear(); var a=repo.attempt(attemptId,user,true).orElseThrow();
            assertThat(a.getState()).isEqualTo(State.SUBMITTED); assertThat(a.getRevision()).isEqualTo(1);
            assertThat(a.getSelection().path("lessonId").asLong()).isEqualTo(lesson);
            assertThat(a.getPlayerState().audioPositions()).containsExactly(new AudioPosition(5,1.25));
            assertThat(repo.attempt(attemptId,user+999,false)).isEmpty();
            assertThat(repo.attempts(user,Mode.LEARN,State.SUBMITTED,0,20).getTotalElements()).isEqualTo(1);
            assertThat(repo.attempts(user,Mode.PRACTICE,null,0,20)).isEmpty();
            var i=repo.items(attemptId).get(0);
            assertThat(i.getDisplay().optionIds()).containsExactly("b","a"); assertThat(i.getHintOpenedAt()).isNotNull();
            assertThat(repo.answers(List.of(i.getId())).get(0).getResponse()).isEqualTo(new SingleAnswer("b"));
            assertThat(repo.trials(i.getId()).get(0).getResponse()).isEqualTo(new SingleAnswer("a"));
            assertThat(repo.submission(attemptId).orElseThrow().getResult().items().get(0).firstOutcome()).isEqualTo(Outcome.INCORRECT);
            assertThat(repo.exposure(new QuestionExposureKey(user,origin),true).orElseThrow().getSeenCount()).isEqualTo(1);
            assertThat(repo.exposures(user,List.of(origin))).hasSize(1);
            assertThat(repo.progress(new LessonProgressKey(user,lesson),true).orElseThrow().getState()).isEqualTo(ProgressState.COMPLETED);
            assertThat(repo.progress(user,unit,0,20).getTotalElements()).isEqualTo(1);
            assertThat(repo.progress(user,unit+999,0,20)).isEmpty();
        });
    }
    @Test void polymorphicAnswerJsonAndSqlNullRoundTrip() {
        long id=tx.execute(s->{var a=attempt();var i=item(a);repo.save(new AttemptAnswerEntity(i.getId(),null));return i.getId();});
        for(Answer answer:Arrays.asList(null,new SingleAnswer(null),new FillAnswer(List.of(new BlankValue("x","partial"))),new MatchingAnswer(List.of(new Pair("l","r"))),new OrderingAnswer(List.of("w2","w1")))) {
            tx.executeWithoutResult(s->{var row=repo.answers(List.of(id)).get(0); row.update(answer); repo.save(row);});
            tx.executeWithoutResult(s->{em.clear();assertThat(repo.answers(List.of(id)).get(0).getResponse()).isEqualTo(answer);});
        }
    }
    @Test void uniqueSubmissionAndTrialSequenceAreEnforcedByMySql() {
        long[] ids=tx.execute(s->{var a=attempt();var i=item(a);repo.save(new AnswerTrialEntity(i.getId(),1,TrialKind.FIRST,TrialSource.CHECK,null,Outcome.UNANSWERED,false,false));repo.save(new SubmissionEntity(result(a,i.getId(),Instant.now())));return new long[]{a.getId(),i.getId()};});
        assertThatThrownBy(()->tx.execute(s->repo.save(new AnswerTrialEntity(ids[1],1,TrialKind.FIRST,TrialSource.CHECK,null,Outcome.UNANSWERED,false,false)))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->tx.execute(s->repo.save(new SubmissionEntity(result(repo.attempt(ids[0],user,false).orElseThrow(),ids[1],Instant.now()))))).isInstanceOf(RuntimeException.class);
        tx.executeWithoutResult(s->{assertThat(repo.trials(ids[1])).hasSize(1);assertThat(repo.submission(ids[0])).isPresent();});
    }
    @Test void foreignKeysAndChecksRejectInvalidRowsAndRollback() {
        long[] ids=tx.execute(s->{var a=attempt();return new long[]{a.getId(),item(a).getId()};});
        for(String sql:List.of(
            "UPDATE attempts SET state='EXPIRED' WHERE id="+ids[0],
            "UPDATE attempts SET state='SUBMITTED',submitted_at=NULL WHERE id="+ids[0],
            "UPDATE attempt_questions SET question_version_id=999999999 WHERE id="+ids[1],
            "UPDATE attempt_questions SET display_json='[]' WHERE id="+ids[1],
            "INSERT INTO answer_trials(attempt_question_id,trial_no,kind,source,outcome,response_complete,support_used) VALUES("+ids[1]+",2,'FIRST','CHECK','CORRECT',1,0)")) {
            assertThatThrownBy(()->tx.execute(s->em.createNativeQuery(sql).executeUpdate())).isInstanceOf(RuntimeException.class);
        }
        tx.executeWithoutResult(s->{assertThat(repo.attempt(ids[0],user,false).orElseThrow().getState()).isEqualTo(State.IN_PROGRESS);assertThat(repo.trials(ids[1])).isEmpty();});
    }
    @Test void completionSurvivesIncompleteReplayAndCompositeKeysAreValueObjects() {
        tx.executeWithoutResult(s->{
            var a=attempt();var p=new LessonProgressEntity(user,lesson,a.getId());
            p.recordSubmission(a.getId(),lessonVersion,new BigDecimal("90.00"),true,Instant.now());repo.save(p);
        });
        tx.executeWithoutResult(s->{
            var p=repo.progress(new LessonProgressKey(user,lesson),true).orElseThrow(); var a=attempt();
            p.started(a.getId());p.recordSubmission(a.getId(),lessonVersion,new BigDecimal("0.00"),false,Instant.now());repo.save(p);
        });
        tx.executeWithoutResult(s->{assertThat(repo.progress(new LessonProgressKey(user,lesson),false).orElseThrow().getState()).isEqualTo(ProgressState.COMPLETED);});
        assertThat(new QuestionExposureKey(user,origin)).isEqualTo(new QuestionExposureKey(user,origin));
        assertThat(new LessonProgressKey(user,lesson)).hasSameHashCodeAs(new LessonProgressKey(user,lesson));
    }
}
