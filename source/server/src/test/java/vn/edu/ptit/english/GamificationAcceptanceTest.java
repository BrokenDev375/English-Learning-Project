package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.WebSocket;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.dto.AuthoringData.*;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.service.impl.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GamificationAcceptanceTest extends GamificationFixture {
    @MockitoSpyBean AttemptRepository attempts;
    @MockitoSpyBean CatalogRepository catalog;
    @Autowired GamificationFailureTracker failures;
    @Autowired GamificationEventDispatcher dispatcher;
    long scalar(String sql){return tx.execute(t->((Number)em.createNativeQuery(sql).getSingleResult()).longValue());}
    JsonNode activity(Client c,long id)throws Exception{return ok(c.call("GET","/api/me/gamification/activities?submissionId="+id,null),200).path("items").get(0);}

    @ParameterizedTest @ValueSource(strings={"GamificationClaimEntity","XpLedgerEntity","GamificationDayEntity","GamificationProfileEntity","UserBadgeEntity","GamificationProcessedEntity"})
    void everyProjectionFailureRollsBackTheEntireRewardAndCanBeRetried(String type)throws Exception{
        var c=new Client().login(alice);var snapshot=ok(c.call("POST","/api/attempts",Map.of("requestId",key(),"mode","LEARN","lessonId",lesson)),201);
        var check=ok(c.call("POST","/api/attempts/"+snapshot.path("attemptId").asLong()+"/items/"+snapshot.path("items").get(0).path("attemptQuestionId").asLong()+"/check",
            Map.of("requestId",key(),"baseRevision",snapshot.path("revision").asLong(),"response",Map.of("type","SINGLE_CHOICE","optionId","yes"))),200);
        ((com.fasterxml.jackson.databind.node.ObjectNode)snapshot).put("revision",check.path("revision").asLong());submit(c,snapshot,"yes");String command=key();
        doAnswer(call->{if(call.getArgument(0).getClass().getSimpleName().equals(type))throw new DataAccessResourceFailureException("acceptance projection failure");return call.callRealMethod();}).when(rewards).save(any());
        try{ok(c.call("POST","/api/me/gamification/sync",Map.of("requestId",command)),503);}finally{reset(rewards);}
        for(String table:List.of("gamification_reward_claims","xp_ledger","gamification_daily_stats","gamification_profiles","user_badges","gamification_processed_submissions","gamification_outbox"))assertThat(count(table)).as(table).isZero();
        assertThat(scalar("SELECT COUNT(*) FROM request_receipts WHERE actor_id="+alice.id()+" AND request_id='"+command+"'")).isZero();
        ok(c.call("POST","/api/me/gamification/sync",Map.of("requestId",command)),200);assertThat(summary(c).path("totalXp").asInt()).isEqualTo(30);assertThat(count("user_badges")).isEqualTo(1);
    }
    @Test void receiptFailureRollsBackProcessedMarkerAndAllAwardedXp()throws Exception{
        var c=new Client().login(alice);submit(c,practice(c,3),"yes");String request=key();
        doThrow(new DataAccessResourceFailureException("acceptance receipt failure")).when(catalog).save(any(RequestReceiptEntity.class));
        try{ok(c.call("POST","/api/me/gamification/sync",Map.of("requestId",request)),503);}finally{reset(catalog);}
        assertThat(count("xp_ledger")).isZero();assertThat(count("gamification_processed_submissions")).isZero();
        ok(c.call("POST","/api/me/gamification/sync",Map.of("requestId",request)),200);assertThat(summary(c).path("totalXp").asInt()).isEqualTo(15);
    }
    @Test void administratorCannotCreateStudentRewardsOrMemberships()throws Exception{
        var admin=tx.execute(t->users.create(key()+"@admin-acceptance.test",hash,"Acceptance admin",Role.ADMIN));var c=new Client().login(admin);
        for(String path:List.of("/api/me/gamification","/api/me/badges","/api/me/league","/api/me/league/history","/api/me/league/leaderboard"))ok(c.call("GET",path,null),403);
        ok(c.call("POST","/api/me/league/join",Map.of("requestId",key())),403);ok(c.call("POST","/api/me/gamification/sync",Map.of("requestId",key())),403);
        assertThat(scalar("SELECT COUNT(*) FROM league_members WHERE user_id="+admin.id())).isZero();assertThat(scalar("SELECT COUNT(*) FROM gamification_profiles WHERE user_id="+admin.id())).isZero();
    }
    @Test void lowerSubmissionIdCommittingLaterIsNotSkippedByTheWorker()throws Exception{
        var a=new Client().login(alice);var b=new Client().login(bob);var first=practice(a,3);var second=practice(b,3);
        var inserted=new CountDownLatch(1);var release=new CountDownLatch(1);long attempt=first.path("attemptId").asLong();
        AttemptRepository target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(attempts);
        doAnswer(call->{var row=(SubmissionEntity)call.callRealMethod();if(row.getAttemptId()==attempt){inserted.countDown();if(!release.await(20,TimeUnit.SECONDS))throw new IllegalStateException("late commit timeout");}return row;}).when(target).save(any(SubmissionEntity.class));
        var pool=Executors.newSingleThreadExecutor();
        try{var pending=pool.submit(()->submit(a,first,"yes"));assertThat(inserted.await(15,TimeUnit.SECONDS)).isTrue();
            var high=submit(b,second,"yes");service.processUser(bob.id());assertThat(summary(b).path("totalXp").asInt()).isEqualTo(15);
            release.countDown();var low=pending.get(20,TimeUnit.SECONDS);assertThat(low.path("submissionId").asLong()).isLessThan(high.path("submissionId").asLong());
            var worker=new GamificationWorker(service,failures,dispatcher);worker.tick();worker.tick();assertThat(summary(a).path("totalXp").asInt()).isEqualTo(15);
        }finally{release.countDown();pool.shutdownNow();reset(target);}
    }
    @ParameterizedTest @ValueSource(strings={"LOCKED","TEACHER"})
    void unavailableAccountIsDeferredWhileOtherUsersContinueAndCanRecover(String state)throws Exception{
        var a=new Client().login(alice);var b=new Client().login(bob);submit(a,practice(a,3),"yes");submit(b,practice(b,3),"yes");
        tx.executeWithoutResult(t->{var u=em.find(UserEntity.class,alice.id());if(state.equals("TEACHER"))u.changeRole(Role.TEACHER);else u.changeStatus(vn.edu.ptit.english.enums.AccountStatus.LOCKED);});
        var worker=new GamificationWorker(service,failures,dispatcher);worker.tick();worker.tick();
        assertThat(count("gamification_processed_submissions")).isZero();assertThat(count("gamification_processing_failures")).isEqualTo(1);assertThat(summary(b).path("totalXp").asInt()).isEqualTo(15);
        tx.executeWithoutResult(t->{var u=em.find(UserEntity.class,alice.id());u.changeRole(Role.STUDENT);u.changeStatus(vn.edu.ptit.english.enums.AccountStatus.ACTIVE);});
        clock.set("2026-10-21T12:01:01Z");worker.tick();worker.tick();assertThat(summary(new Client().login(alice)).path("totalXp").asInt()).isEqualTo(15);
        assertThat(count("gamification_processing_failures")).isZero();
    }
    @Test void activationEqualityAndOneMicrosecondAfterAreIncluded()throws Exception{
        var a=new Client().login(alice);clock.set("2026-10-01T00:00:00Z");submit(a,practice(a,3),"yes");sync(a);
        clock.set("2026-10-01T00:00:00.000001Z");submit(a,practice(a,3),"yes");sync(a);assertThat(summary(a).path("totalXp").asInt()).isEqualTo(30);
    }
    @ParameterizedTest @ValueSource(ints={0,99,100,199,200})
    void activityLevelBoundaryUsesTheStoredProjection(int xp)throws Exception{
        // Projection boundary fixture, not XP manufactured by the public API.
        tx.executeWithoutResult(t->{var p=new GamificationProfileEntity(alice.id());p.record(xp);em.persist(p);});
        var s=summary(new Client().login(alice));assertThat(s.path("activityLevel").asInt()).isEqualTo(1+xp/100);
        assertThat(s.path("levelFloorXp").asInt()).isEqualTo(xp/100*100);assertThat(s.path("nextLevelAtXp").asInt()).isEqualTo(xp/100*100+100);
    }
    @Test void allocationAbandonmentNeverRegainsFreshXp()throws Exception{
        var c=new Client().login(alice);practice(c,6);var again=practice(c,5);submit(c,again,"yes");sync(c);
        assertThat(summary(c).path("totalXp").asInt()).isZero();assertThat(summary(c).path("day").path("isActive").asBoolean()).isTrue();
        assertThat(count("gamification_reward_claims")).isZero();
    }
    @Test void reviewCycleAndObjectiveDayUniquenessSurviveTwoPinnedAttempts()throws Exception{
        var c=new Client().login(alice);submit(c,practice(c,3),"yes");ok(c.call("POST","/api/me/review/sync",Map.of("requestId",key())),200);
        tx.executeWithoutResult(t->em.createNativeQuery("UPDATE review_objective_progress SET next_review_at='2026-10-20' WHERE user_id=:u").setParameter("u",alice.id()).executeUpdate());
        var one=ok(c.call("POST","/api/attempts",Map.of("requestId",key(),"mode","REVIEW","reviewKind","DUE","topicId",topic,"count",3)),201);
        var two=ok(c.call("POST","/api/attempts",Map.of("requestId",key(),"mode","REVIEW","reviewKind","DUE","topicId",topic,"count",4)),201);
        submit(c,one,"yes");var repeated=submit(c,two,"yes");sync(c);
        assertThat(activity(c,repeated.path("submissionId").asLong()).path("decisions").findValuesAsText("reason")).contains("ALREADY_CLAIMED");
        assertThat(scalar("SELECT COUNT(*) FROM gamification_reward_claims WHERE user_id="+alice.id()+" AND reward_type='DUE_REVIEW'")).isEqualTo(1);
        ok(c.call("POST","/api/me/review/sync",Map.of("requestId",key())),200);
        tx.executeWithoutResult(t->em.createNativeQuery("UPDATE review_objective_progress SET stage=1,next_review_at='2026-10-20 01:00:00',needs_practice=0,has_mistakes=0 WHERE user_id=:u").setParameter("u",alice.id()).executeUpdate());
        var three=ok(c.call("POST","/api/attempts",Map.of("requestId",key(),"mode","REVIEW","reviewKind","DUE","topicId",topic,"count",5)),201);
        var limited=submit(c,three,"yes");sync(c);assertThat(activity(c,limited.path("submissionId").asLong()).path("decisions").findValuesAsText("reason")).contains("ALREADY_REWARDED_TODAY");
    }
    @Test void aPublishedCopyWithTheSameOriginCannotCreateAnotherFreshAward()throws Exception{
        var c=new Client().login(alice);var first=practice(c,6);submit(c,first,"yes");sync(c);var item=first.path("items").get(0);
        long copyLesson=tx.execute(t->{
            var original=em.find(QuestionVersionEntity.class,item.path("questionVersionId").asLong());var root=new QuestionEntity(teacher.id(),item.path("originKey").asText());em.persist(root);em.flush();
            var v=new QuestionVersionEntity(root.getId(),1,original.data());em.persist(v);em.flush();root.initialDraft(v);root.publish();
            var lessonRoot=new LessonEntity(teacher.id(),unit,1);em.persist(lessonRoot);em.flush();
            var data=new vn.edu.ptit.english.dto.AuthoringData.Lesson("Copy lesson","",node("[{\"kind\":\"TEXT\",\"text\":\"Copy\"}]"),List.of(objective),List.of(new vn.edu.ptit.english.dto.AuthoringData.Slot(v.getId(),"INDEPENDENT",0)));
            var version=new LessonVersionEntity(lessonRoot.getId(),1,data);em.persist(version);em.flush();lessonRoot.initialDraft(version);lessonRoot.publish();return lessonRoot.getId();});
        var copy=ok(c.call("POST","/api/attempts",Map.of("requestId",key(),"mode","LEARN","lessonId",copyLesson)),201);
        var source=submit(c,copy,"yes");sync(c);assertThat(summary(c).path("totalXp").asInt()).isEqualTo(30);
        assertThat(activity(c,source.path("submissionId").asLong()).path("decisions").findValuesAsText("reason")).contains("SEEN_BEFORE");
    }
    @Test void immediateMistakeReviewHasFreshXpButNoDueBonus()throws Exception{
        var c=new Client().login(alice);submit(c,practice(c,3),"no");ok(c.call("POST","/api/me/review/sync",Map.of("requestId",key())),200);
        var review=ok(c.call("POST","/api/attempts",Map.of("requestId",key(),"mode","REVIEW","reviewKind","MISTAKES","topicId",topic,"count",3)),201);
        var source=submit(c,review,"yes");sync(c);assertThat(summary(c).path("totalXp").asInt()).isEqualTo(15);
        assertThat(activity(c,source.path("submissionId").asLong()).path("decisions").findValuesAsText("reason")).contains("REVIEW_NOT_DUE");
    }
    @Test void legacyReviewWithoutContextDoesNotInventDueEvidence()throws Exception{
        var c=new Client().login(alice);submit(c,practice(c,3),"yes");ok(c.call("POST","/api/me/review/sync",Map.of("requestId",key())),200);
        tx.executeWithoutResult(t->em.createNativeQuery("UPDATE review_objective_progress SET next_review_at='2026-10-20' WHERE user_id=:u").setParameter("u",alice.id()).executeUpdate());
        var review=ok(c.call("POST","/api/attempts",Map.of("requestId",key(),"mode","REVIEW","reviewKind","DUE","topicId",topic,"count",3)),201);
        // Test-only legacy fixture: M03 snapshot/result remains unchanged, only M05 evidence is absent.
        tx.executeWithoutResult(t->em.createNativeQuery("DELETE FROM review_reward_context WHERE attempt_id=:a").setParameter("a",review.path("attemptId").asLong()).executeUpdate());
        var source=submit(c,review,"yes");sync(c);assertThat(activity(c,source.path("submissionId").asLong()).path("decisions").findValuesAsText("reason")).contains("NO_REVIEW_CONTEXT");
        assertThat(scalar("SELECT COUNT(*) FROM gamification_reward_claims WHERE user_id="+alice.id()+" AND reward_type='DUE_REVIEW'")).isZero();
    }
    @Test void twoObjectiveContextIsPinnedAcrossMidnightAndOneFailedObjectiveCannotClaimBonus()throws Exception{
        tx.executeWithoutResult(t->{long original=objective;var second=new ObjectiveEntity(teacher.id(),topic,key(),"Second objective","");em.persist(second);em.flush();objective=second.getId();bank(6);objective=original;});
        var c=new Client().login(alice);submit(c,practice(c,10),"yes");ok(c.call("POST","/api/me/review/sync",Map.of("requestId",key())),200);
        tx.executeWithoutResult(t->em.createNativeQuery("UPDATE review_objective_progress SET next_review_at='2026-10-20' WHERE user_id=:u").setParameter("u",alice.id()).executeUpdate());
        var command=Map.of("requestId",key(),"mode","REVIEW","reviewKind","DUE","topicId",topic,"count",6);
        var review=ok(c.call("POST","/api/attempts",command),201);long attempt=review.path("attemptId").asLong();
        assertThat(review.path("items").findValuesAsText("objectiveId").stream().distinct().count()).isEqualTo(2);
        String before=tx.execute(t->em.createNativeQuery("SELECT CAST(JSON_ARRAYAGG(JSON_OBJECT('cycle',cycle_key,'selected',selected_at)) AS CHAR) FROM review_reward_context WHERE attempt_id=:a").setParameter("a",attempt).getSingleResult().toString());
        long exposure=scalar("SELECT COUNT(*) FROM question_exposures WHERE user_id="+alice.id());clock.set("2026-10-22T12:00:00Z");
        ok(c.call("POST","/api/attempts",Map.of("requestId",key(),"mode","REVIEW","reviewKind","DUE","topicId",topic,"count",6)),200);
        assertThat(ok(c.call("POST","/api/attempts",command),201)).isEqualTo(review);
        assertThat(tx.execute(t->em.createNativeQuery("SELECT CAST(JSON_ARRAYAGG(JSON_OBJECT('cycle',cycle_key,'selected',selected_at)) AS CHAR) FROM review_reward_context WHERE attempt_id=:a").setParameter("a",attempt).getSingleResult().toString()).toString()).isEqualTo(before);
        assertThat(scalar("SELECT COUNT(*) FROM question_exposures WHERE user_id="+alice.id())).isEqualTo(exposure);
        var body=json.createObjectNode().put("requestId",key()).put("baseRevision",review.path("revision").asLong());body.set("playerState",review.path("playerState"));var answers=body.putArray("answers");int index=0;
        for(var item:review.path("items")){var entry=answers.addObject().put("attemptQuestionId",item.path("attemptQuestionId").asLong());if(index++==0)entry.putNull("response");else entry.set("response",node("{\"type\":\"SINGLE_CHOICE\",\"optionId\":\"yes\"}"));}
        var result=ok(c.call("POST","/api/attempts/"+attempt+"/submit",body),200);sync(c);
        assertThat(activity(c,result.path("submissionId").asLong()).path("decisions").findValuesAsText("reason")).contains("REVIEW_OBJECTIVE_NOT_PASSED");
        assertThat(scalar("SELECT COUNT(*) FROM gamification_reward_claims WHERE user_id="+alice.id()+" AND reward_type='DUE_REVIEW'")).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void multipartQuestionsRewardOncePerQuestionAndPartialAnswersDoNotQualify(boolean partial)throws Exception{
        long multi=tx.execute(t->{var slots=new ArrayList<Slot>();
            for(String type:List.of("FILL_BLANK","MATCHING")){
                var root=new QuestionEntity(teacher.id(),key());em.persist(root);em.flush();
                var content=type.equals("FILL_BLANK")?node("{\"parts\":[{\"kind\":\"BLANK\",\"blankId\":\"b1\"},{\"kind\":\"TEXT\",\"text\":\" and \"},{\"kind\":\"BLANK\",\"blankId\":\"b2\"}]}"):
                    node("{\"leftItems\":[{\"id\":\"a\",\"text\":\"A\"},{\"id\":\"b\",\"text\":\"B\"}],\"rightItems\":[{\"id\":\"x\",\"text\":\"X\"},{\"id\":\"y\",\"text\":\"Y\"}]}");
                var key=type.equals("FILL_BLANK")?node("{\"blanks\":[{\"blankId\":\"b1\",\"acceptedAnswers\":[\"one\"]},{\"blankId\":\"b2\",\"acceptedAnswers\":[\"two\"]}],\"caseSensitive\":false,\"normalizeWhitespace\":true}"):
                    node("{\"pairs\":[{\"leftId\":\"a\",\"rightId\":\"x\"},{\"leftId\":\"b\",\"rightId\":\"y\"}]}");
                var data=new Question(type,topic,objective,"EASY",type,content,key,"Explanation",null,null,new vn.edu.ptit.english.dto.MaterialData.Source("Test",null,null));
                var version=new QuestionVersionEntity(root.getId(),1,data);em.persist(version);em.flush();root.initialDraft(version);root.publish();slots.add(new Slot(version.getId(),"INDEPENDENT",slots.size()));
            }
            var root=new LessonEntity(teacher.id(),unit,1);em.persist(root);em.flush();var version=new LessonVersionEntity(root.getId(),1,new Lesson("Multipart","",node("[{\"kind\":\"TEXT\",\"text\":\"Answer\"}]"),List.of(objective),slots));
            em.persist(version);em.flush();root.initialDraft(version);root.publish();return root.getId();});
        var c=new Client().login(alice);var snapshot=ok(c.call("POST","/api/attempts",Map.of("requestId",key(),"mode","LEARN","lessonId",multi)),201);
        var body=json.createObjectNode().put("requestId",key()).put("baseRevision",snapshot.path("revision").asLong());body.set("playerState",snapshot.path("playerState"));var answers=body.putArray("answers");
        for(var item:snapshot.path("items")){boolean fill=item.path("question").path("type").asText().equals("FILL_BLANK");
            var response=fill?node("{\"type\":\"FILL_BLANK\",\"values\":[{\"blankId\":\"b1\",\"value\":\"one\"}"+(partial?"":",{\"blankId\":\"b2\",\"value\":\"two\"}")+"]}"):
                node("{\"type\":\"MATCHING\",\"pairs\":[{\"leftId\":\"a\",\"rightId\":\"x\"}"+(partial?"":",{\"leftId\":\"b\",\"rightId\":\"y\"}")+"]}");
            answers.addObject().put("attemptQuestionId",item.path("attemptQuestionId").asLong()).set("response",response);}
        ok(c.call("POST","/api/attempts/"+snapshot.path("attemptId").asLong()+"/submit",body),200);sync(c);
        assertThat(summary(c).path("totalXp").asInt()).isEqualTo(partial?0:10);assertThat(summary(c).path("day").path("isActive").asBoolean()).isEqualTo(!partial);
        assertThat(count("gamification_reward_claims")).isEqualTo(partial?0:2);
    }
    @Test void capAt195SharesTheLastFiveWithDueBonusAndConsumesTheZeroAwardClaim()throws Exception{
        tx.executeWithoutResult(t->bank(40));var c=new Client().login(alice);
        for(int i=0;i<3;i++){submit(c,practice(c,10),"yes");sync(c);}submit(c,practice(c,9),"yes");sync(c);assertThat(summary(c).path("totalXp").asInt()).isEqualTo(195);
        ok(c.call("POST","/api/me/review/sync",Map.of("requestId",key())),200);
        tx.executeWithoutResult(t->em.createNativeQuery("UPDATE review_objective_progress SET next_review_at='2026-10-20' WHERE user_id=:u").setParameter("u",alice.id()).executeUpdate());
        var one=ok(c.call("POST","/api/attempts",Map.of("requestId",key(),"mode","REVIEW","reviewKind","DUE","topicId",topic,"count",3)),201);
        var two=ok(c.call("POST","/api/attempts",Map.of("requestId",key(),"mode","REVIEW","reviewKind","DUE","topicId",topic,"count",4)),201);
        var result=submit(c,one,"yes");sync(c);assertThat(activity(c,result.path("submissionId").asLong()).path("awardedXp").asInt()).isEqualTo(5);
        assertThat(scalar("SELECT SUM(x.awarded_xp) FROM xp_ledger x JOIN gamification_reward_claims c ON c.id=x.claim_id WHERE c.user_id="+alice.id()+" AND c.reward_type='DUE_REVIEW'")).isZero();
        assertThat(count("user_badges")).isEqualTo(1);clock.set("2026-10-22T12:00:00Z");var later=submit(c,two,"yes");sync(c);
        assertThat(activity(c,later.path("submissionId").asLong()).path("decisions").findValuesAsText("reason")).contains("ALREADY_CLAIMED");
    }
    @Test void blankReviewChangesLastSubmissionButNotTheDueCycle()throws Exception{
        var c=new Client().login(alice);submit(c,practice(c,3),"yes");ok(c.call("POST","/api/me/review/sync",Map.of("requestId",key())),200);
        tx.executeWithoutResult(t->em.createNativeQuery("UPDATE review_objective_progress SET next_review_at='2026-10-20' WHERE user_id=:u").setParameter("u",alice.id()).executeUpdate());
        var first=ok(c.call("POST","/api/attempts",Map.of("requestId",key(),"mode","REVIEW","reviewKind","DUE","topicId",topic,"count",3)),201);submit(c,first,null);
        ok(c.call("POST","/api/me/review/sync",Map.of("requestId",key())),200);
        var next=ok(c.call("POST","/api/attempts",Map.of("requestId",key(),"mode","REVIEW","reviewKind","DUE","topicId",topic,"count",4)),201);
        var cycles=tx.execute(t->em.createNativeQuery("SELECT DISTINCT cycle_key FROM review_reward_context WHERE attempt_id IN (:a,:b)").setParameter("a",first.path("attemptId").asLong()).setParameter("b",next.path("attemptId").asLong()).getResultList());
        assertThat(cycles).hasSize(1);submit(c,next,"yes");sync(c);assertThat(scalar("SELECT COUNT(*) FROM gamification_reward_claims WHERE user_id="+alice.id()+" AND reward_type='DUE_REVIEW'")).isEqualTo(1);
    }
    final class Messages implements WebSocket.Listener {
        final BlockingQueue<String> messages=new LinkedBlockingQueue<>();final StringBuilder text=new StringBuilder();
        @Override public void onOpen(WebSocket socket){socket.request(1);}
        @Override public CompletionStage<?> onText(WebSocket socket,CharSequence data,boolean last){text.append(data);if(last){messages.add(text.toString());text.setLength(0);}socket.request(1);return CompletableFuture.completedFuture(null);}
        JsonNode next()throws Exception{String value=messages.poll(10,TimeUnit.SECONDS);assertThat(value).isNotNull();return json.readTree(value);}
    }
    @Test void socketScopesRejectForeignSubscriptionAndOutboxRetryRepeatsOnlyInvalidation()throws Exception{
        var a=new Client().login(alice);var b=new Client().login(bob);var ma=new Messages();var mb=new Messages();
        var wa=a.http.newWebSocketBuilder().header("Origin","http://localhost:5173").buildAsync(URI.create("ws://127.0.0.1:"+port+"/ws/events"),ma).get(10,TimeUnit.SECONDS);
        var wb=b.http.newWebSocketBuilder().header("Origin","http://localhost:5173").buildAsync(URI.create("ws://127.0.0.1:"+port+"/ws/events"),mb).get(10,TimeUnit.SECONDS);
        try{
            ma.next();mb.next();wa.sendText("{\"version\":1,\"type\":\"SUBSCRIBE\",\"resource\":\"GAMIFICATION\",\"requestId\":\""+key()+"\",\"userId\":1}",true).join();assertThat(ma.next().path("code").asText()).isEqualTo("INVALID_SUBSCRIPTION");
            for(var pair:List.of(Map.entry(wa,ma),Map.entry(wb,mb))){pair.getKey().sendText(json.writeValueAsString(Map.of("version",1,"type","SUBSCRIBE","resource","GAMIFICATION","requestId",key())),true).join();assertThat(pair.getValue().next().path("type").asText()).isEqualTo("ACK");}
            submit(a,practice(a,3),"yes");service.processUser(alice.id());var once=new AtomicBoolean();
            doAnswer(call->{var row=call.getArgument(0);if(row instanceof GamificationOutboxEntity event&&event.getUserId()==alice.id()&&once.compareAndSet(false,true))throw new DataAccessResourceFailureException("delivery rollback");return call.callRealMethod();}).when(rewards).save(any());
            try{assertThatThrownBy(()->dispatcher.dispatch()).isInstanceOf(DataAccessResourceFailureException.class);}finally{reset(rewards);}
            var first=ma.next();dispatcher.dispatch();var replay=ma.next();assertThat(replay.path("eventId")).isEqualTo(first.path("eventId"));
            assertThat(first.toString()).doesNotContain("answer","email","userId");assertThat(mb.messages.poll(300,TimeUnit.MILLISECONDS)).isNull();
            assertThat(summary(a).path("totalXp").asInt()).isEqualTo(15);assertThat(count("xp_ledger")).isEqualTo(3);
        }finally{wa.abort();wb.abort();}
    }
}
