package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.ptit.english.dto.UserRecord;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.service.ContestService;
import static org.assertj.core.api.Assertions.*;

@Import(ContestApiTest.TimeConfig.class)
@TestPropertySource(properties="app.contest.worker-enabled=false")
class ContestApiTest extends AuthoringFixture {
    static class Time extends Clock {
        volatile Instant now=Instant.parse("2026-10-30T04:00:00Z");
        public Instant instant(){return now;} public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId zone){return this;}
    }
    @TestConfiguration static class TimeConfig { @Bean @Primary Time contestTime(){return new Time();} }
    @Autowired Time time;
    @Autowired ContestService contests;
    @BeforeEach void resetTime(){time.now=Instant.parse("2026-10-30T04:00:00Z");}
    @AfterEach void cleanupContest(){new TransactionTemplate(transactions).executeWithoutResult(t->{em.createNativeQuery("DELETE FROM contest_events").executeUpdate();em.createNativeQuery("UPDATE contest_participants SET submission_id=NULL").executeUpdate();em.createNativeQuery("DELETE FROM contest_submissions").executeUpdate();em.createNativeQuery("DELETE FROM contest_participants").executeUpdate();em.createNativeQuery("DELETE FROM contests").executeUpdate();em.createNativeQuery("DELETE FROM contest_papers").executeUpdate();em.createNativeQuery("DELETE FROM contest_teacher_permissions").executeUpdate();});}
    record Case(Client admin,Client learner,Client teacher,long id,String client,List<String> questionIds,Instant start,Instant end) {}
    JsonNode node(String value){try{return json.readTree(value);}catch(Exception error){throw new IllegalArgumentException(error);}}
    ObjectNode command(){return json.createObjectNode().put("requestId",key());}
    JsonNode question(String id){var q=node("{\"type\":\"SINGLE_CHOICE\",\"prompt\":\"Choose yes\",\"context\":\"Private exam passage\",\"difficulty\":\"EASY\",\"content\":{\"options\":[{\"id\":\"yes\",\"text\":\"Yes\"},{\"id\":\"no\",\"text\":\"No\"}]},\"answerKey\":{\"correctOptionId\":\"yes\"},\"explanation\":\"EXAM_SECRET: yes is correct\"}");((ObjectNode)q).put("id",id);return q;}
    Case prepare(boolean checkIn)throws Exception{
        return prepare(checkIn,List.of(question(key()),question(key())));
    }
    Case prepare(boolean checkIn,List<JsonNode> bank)throws Exception{
        var a=new Client().login(admin);var s=new Client().login(learner);var teacher=new Client().login(author);
        var start=time.now.plusSeconds(120);var end=start.plusSeconds(1800);var input=command().put("title","Private contest").put("description","Contest rules").put("registrationClosesAt",time.now.plusSeconds(60).toString()).put("startsAt",start.toString()).put("endsAt",end.toString()).put("penaltyMinutes",10).put("shuffleQuestions",true);
        input.putArray("proctorIds").add(author.id());var ids=bank.stream().map(q->q.path("id").asText()).toList();
        ok(a.send("PUT","/api/author/contest-permissions/"+author.id(),command().put("baseRevision",0).put("canAuthor",true).put("canProctor",true)),200);
        var paperInput=command().put("title","Reusable exam").put("description","");var questions=paperInput.putArray("questions");bank.forEach(questions::add);
        var paper=ok(a.send("POST","/api/author/contest-papers",paperInput),201);long paperId=paper.path("id").asLong();
        paper=ok(a.send("POST","/api/author/contest-papers/"+paperId+"/submit",command().put("baseRevision",paper.path("revision").asLong())),200);
        paper=ok(a.send("POST","/api/author/contest-papers/"+paperId+"/approve",command().put("baseRevision",paper.path("revision").asLong())),200);input.put("paperId",paperId);
        var created=ok(a.send("POST","/api/author/contests",input),201);long id=created.path("id").asLong();
        ok(a.send("POST","/api/author/contests/"+id+"/publish",command().put("baseRevision",created.path("revision").asLong())),200);
        ok(s.send("POST","/api/me/contests/"+id+"/register",command()),201);
        var c=new Case(a,s,teacher,id,key(),ids,start,end);var p=participant(c);
        ok(a.send("POST","/api/author/contests/"+id+"/participants/"+p.path("id").asLong()+"/approve",command().put("baseRevision",p.path("controlRevision").asLong())),200);
        if(checkIn)ok(s.send("POST","/api/me/contests/"+id+"/check-in",command().put("clientId",c.client())),200);
        return c;
    }
    JsonNode view(Case c)throws Exception{return ok(c.learner().send("GET","/api/me/contests/"+c.id(),null),200);}
    JsonNode authorView(Case c)throws Exception{return ok(c.admin().send("GET","/api/author/contests/"+c.id(),null),200);}
    JsonNode participant(Case c)throws Exception{return ok(c.admin().send("GET","/api/author/contests/"+c.id()+"/monitor",null),200).path("participants").path("items").get(0);}
    JsonNode hidden(Case c,boolean hidden)throws Exception{return ok(c.learner().send("POST","/api/me/contests/"+c.id()+"/visibility",command().put("clientId",c.client()).put("hidden",hidden)),200);}
    ObjectNode answers(Case c,String option){var result=json.createObjectNode();c.questionIds().forEach(id->result.set(id,node("{\"type\":\"SINGLE_CHOICE\",\"optionId\":\""+option+"\"}")));return result;}
    ObjectNode answerCommand(Case c,String option)throws Exception{var v=view(c);var input=command().put("baseRevision",v.path("participation").path("revision").asLong()).put("clientId",c.client());input.set("answers",answers(c,option));return input;}
    void start(Case c){time.now=c.start().plusSeconds(1);contests.tick(c.id());}
    JsonNode close(Case c)throws Exception{return ok(c.admin().send("POST","/api/author/contests/"+c.id()+"/close",command().put("baseRevision",authorView(c).path("revision").asLong()).put("reason","End the contest")),200);}
    long count(String table){return new TransactionTemplate(transactions).execute(t->((Number)em.createNativeQuery("SELECT COUNT(*) FROM "+table).getSingleResult()).longValue());}

    @Test void privatePaperIsHiddenUntilTheStartAndAnswersUntilAllResultsAreFinalized()throws Exception{
        var c=prepare(true);assertThat(view(c).path("paper").isNull()).isTrue();assertThat(view(c).toString()).doesNotContain("answerKey","EXAM_SECRET","Private exam passage");
        assertThat(c.learner().send("GET","/api/author/contests/"+c.id(),null).statusCode()).isEqualTo(403);
        start(c);var opened=view(c);assertThat(opened.path("paper")).hasSize(2);assertThat(opened.toString()).doesNotContain("answerKey","EXAM_SECRET","explanation","hint");
        var submitted=ok(c.learner().send("POST","/api/me/contests/"+c.id()+"/submit",answerCommand(c,"yes")),200);assertThat(submitted.path("result").isNull()).isTrue();
        assertThat(ok(c.learner().send("GET","/api/contests/"+c.id()+"/leaderboard",null),200).path("finalized").asBoolean()).isFalse();
        close(c);var finished=view(c);assertThat(finished.path("result").path("correctCount").asInt()).isEqualTo(2);assertThat(finished.toString()).contains("EXAM_SECRET");
        assertThat(count("questions")).isZero();assertThat(count("attempts")).isZero();assertThat(count("submissions")).isZero();assertThat(count("xp_ledger")).isZero();
    }
    @Test void registrationRequiresAdminApprovalAndCheckInBeforeTheSharedStart()throws Exception{
        var c=prepare(false);var stranger=users.create("contest-new@test.local",passwords.encode("TestPass@123"),"New learner",Role.STUDENT);var other=new Client().login(stranger);
        var registered=ok(other.send("POST","/api/me/contests/"+c.id()+"/register",command()),201);assertThat(registered.path("participation").path("status").asText()).isEqualTo("REQUESTED");
        assertThat(other.send("POST","/api/me/contests/"+c.id()+"/check-in",command().put("clientId",key())).statusCode()).isEqualTo(403);
        start(c);assertThat(c.learner().send("POST","/api/me/contests/"+c.id()+"/claim",command().put("clientId",c.client())).statusCode()).isEqualTo(409);
        assertThat(other.send("POST","/api/me/contests/"+c.id()+"/register",command()).statusCode()).isEqualTo(409);
        assertThat(view(c).path("paper").isNull()).isTrue();
    }
    @Test void leavingTwiceCutsTenMinutesOnceAndTheThirdLeaveFlagsForAdminDecision()throws Exception{
        var c=prepare(true);start(c);var first=command().put("clientId",c.client()).put("hidden",true);
        ok(c.learner().send("POST","/api/me/contests/"+c.id()+"/visibility",first),200);
        var replay=c.learner().send("POST","/api/me/contests/"+c.id()+"/visibility",first);assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");ok(replay,200);
        assertThat(hidden(c,true).path("participation").path("tabLeaves").asInt()).isEqualTo(1);
        hidden(c,false);var second=hidden(c,true);assertThat(Instant.parse(second.path("participation").path("deadline").asText())).isEqualTo(c.end().minusSeconds(600));
        hidden(c,false);var third=hidden(c,true);assertThat(third.path("participation").path("tabLeaves").asInt()).isEqualTo(3);assertThat(third.path("participation").path("flagged").asBoolean()).isTrue();assertThat(third.path("participation").path("status").asText()).isEqualTo("APPROVED");
        assertThat(Instant.parse(third.path("participation").path("deadline").asText())).isEqualTo(c.end().minusSeconds(600));
        var p=participant(c);ok(c.admin().send("POST","/api/author/contests/"+c.id()+"/participants/"+p.path("id").asLong()+"/revoke",command().put("baseRevision",p.path("controlRevision").asLong()).put("reason","Repeated departures")),200);
        assertThat(view(c).path("participation").path("status").asText()).isEqualTo("REVOKED");assertThat(view(c).path("paper").isNull()).isTrue();assertThat(c.learner().send("GET","/api/auth/me",null).statusCode()).isEqualTo(200);
        assertThat(c.learner().send("POST","/api/me/contests/"+c.id()+"/save",answerCommand(c,"yes")).statusCode()).isEqualTo(403);
        close(c);assertThat(ok(c.admin().send("GET","/api/contests/"+c.id()+"/leaderboard",null),200).path("items")).isEmpty();assertThat(count("contest_submissions")).isEqualTo(1);
    }
    @Test void monitorSeparatesLastTabSignalFromConnectionAndRetainsRevokedParticipants()throws Exception{
        var c=prepare(true);start(c);assertThat(participant(c).path("tabHidden").asBoolean()).isFalse();
        var left=command().put("clientId",c.client()).put("hidden",true);
        ok(c.learner().send("POST","/api/me/contests/"+c.id()+"/visibility",left),200);
        assertThat(participant(c).path("tabHidden").asBoolean()).isTrue();assertThat(participant(c).path("online").asBoolean()).isTrue();
        time.now=time.now.plusSeconds(46);var stale=participant(c);
        assertThat(stale.path("online").asBoolean()).isFalse();assertThat(stale.path("tabHidden").asBoolean()).isTrue();
        hidden(c,false);var returned=participant(c);assertThat(returned.path("tabHidden").asBoolean()).isFalse();assertThat(returned.path("online").asBoolean()).isTrue();assertThat(returned.path("tabLeaves").asInt()).isEqualTo(1);
        ok(c.learner().send("POST","/api/me/contests/"+c.id()+"/visibility",left),200);
        assertThat(participant(c).path("tabHidden").asBoolean()).isFalse();assertThat(participant(c).path("tabLeaves").asInt()).isEqualTo(1);
        var p=participant(c);ok(c.admin().send("POST","/api/author/contests/"+c.id()+"/participants/"+p.path("id").asLong()+"/revoke",command().put("baseRevision",p.path("controlRevision").asLong()).put("reason","Proctor decision")),200);
        assertThat(participant(c).path("status").asText()).isEqualTo("REVOKED");
        var revoked=ok(c.admin().send("GET","/api/author/contests/"+c.id()+"/monitor?filter=REVOKED",null),200).path("participants").path("items");
        assertThat(revoked).hasSize(1);assertThat(revoked.get(0).path("id").asLong()).isEqualTo(p.path("id").asLong());
    }
    @Test void aPenaltyBiggerThanTheRemainingTimeEndsNowWithoutRewindingActualDuration()throws Exception{
        var c=prepare(true);start(c);ok(c.learner().send("POST","/api/me/contests/"+c.id()+"/save",answerCommand(c,"yes")),200);
        time.now=c.end().minusSeconds(300);c.learner().login(learner);c.admin().login(admin);hidden(c,true);hidden(c,false);var ended=hidden(c,true);assertThat(ended.path("participation").path("submitted").asBoolean()).isTrue();
        assertThat(Instant.parse(ended.path("participation").path("deadline").asText())).isEqualTo(time.now);
        close(c);var result=view(c).path("result");assertThat(result.path("correctCount").asInt()).isEqualTo(2);assertThat(result.path("elapsedMillis").asLong()).isEqualTo(1500_000);
    }
    @Test void lateDraftIsRejectedAndWorkerUsesTheDeadlineNotItsLateProcessingTime()throws Exception{
        var c=prepare(true);start(c);ok(c.learner().send("POST","/api/me/contests/"+c.id()+"/save",answerCommand(c,"no")),200);var late=answerCommand(c,"yes");
        time.now=c.end().plusSeconds(30);c.learner().login(learner);assertThat(c.learner().send("POST","/api/me/contests/"+c.id()+"/save",late).statusCode()).isEqualTo(409);
        var replay=c.learner().send("POST","/api/me/contests/"+c.id()+"/save",late);assertThat(replay.statusCode()).isEqualTo(409);assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");contests.tick(c.id());
        var result=view(c).path("result");assertThat(result.path("correctCount").asInt()).isZero();assertThat(Instant.parse(result.path("submittedAt").asText())).isEqualTo(c.end());assertThat(result.path("elapsedMillis").asLong()).isEqualTo(1800_000);
        contests.tick(c.id());assertThat(count("contest_submissions")).isEqualTo(1);
    }
    @Test void twoTabsUseOneWriterAndDraftRevisionsDoNotBlockAnAdminControlDecision()throws Exception{
        var c=prepare(true);String next=key();assertThat(c.learner().send("POST","/api/me/contests/"+c.id()+"/claim",command().put("clientId",next)).statusCode()).isEqualTo(409);
        time.now=time.now.plusSeconds(46);ok(c.learner().send("POST","/api/me/contests/"+c.id()+"/claim",command().put("clientId",next)),200);start(c);
        assertThat(c.learner().send("POST","/api/me/contests/"+c.id()+"/save",answerCommand(c,"yes")).statusCode()).isEqualTo(409);
        var owner=new Case(c.admin(),c.learner(),c.teacher(),c.id(),next,c.questionIds(),c.start(),c.end());var control=participant(c);var saved=answerCommand(owner,"yes");ok(owner.learner().send("POST","/api/me/contests/"+c.id()+"/save",saved),200);
        assertThat(owner.learner().send("POST","/api/me/contests/"+c.id()+"/save",answerCommandWithStaleKey(saved)).statusCode()).isEqualTo(409);
        ok(c.admin().send("POST","/api/author/contests/"+c.id()+"/participants/"+control.path("id").asLong()+"/revoke",command().put("baseRevision",control.path("controlRevision").asLong()).put("reason","Administrative decision")),200);
    }
    ObjectNode answerCommandWithStaleKey(ObjectNode value){var copy=value.deepCopy();copy.put("requestId",key());return copy;}
    @Test void submitReplayAndConcurrentSubmissionsCreateOnlyOneImmutableResult()throws Exception{
        var c=prepare(true);start(c);var first=answerCommand(c,"yes");var second=first.deepCopy().put("requestId",key());var pool=Executors.newFixedThreadPool(2);
        try{var a=pool.submit(()->c.learner().send("POST","/api/me/contests/"+c.id()+"/submit",first));var b=pool.submit(()->c.learner().send("POST","/api/me/contests/"+c.id()+"/submit",second));var ar=a.get();var br=b.get();assertThat(List.of(ar.statusCode(),br.statusCode())).containsExactlyInAnyOrder(200,409);var replay=c.learner().send("POST","/api/me/contests/"+c.id()+"/submit",ar.statusCode()==200?first:second);assertThat(replay.statusCode()).isEqualTo(200);assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");}finally{pool.shutdownNow();}
        assertThat(count("contest_submissions")).isEqualTo(1);close(c);var frozen=view(c).path("result");contests.tick(c.id());assertThat(view(c).path("result")).isEqualTo(frozen);
        var author=authorView(c);ok(c.admin().send("POST","/api/author/contests/"+c.id()+"/archive",command().put("baseRevision",author.path("revision").asLong())),200);assertThat(view(c).path("result")).isEqualTo(frozen);
    }
    @Test void proctorScopeIsCurrentAndTeacherCannotPublishApproveOrRevoke()throws Exception{
        var c=prepare(true);var outsider=new Client().login(other);assertThat(outsider.send("GET","/api/author/contests/"+c.id(),null).statusCode()).isEqualTo(404);
        assertThat(c.teacher().send("GET","/api/author/contests/"+c.id()+"/monitor",null).statusCode()).isEqualTo(200);
        assertThat(ok(c.teacher().send("GET","/api/contests",null),200).path("items")).hasSize(1);
        assertThat(c.teacher().send("POST","/api/author/contests/"+c.id()+"/publish",command().put("baseRevision",authorView(c).path("revision").asLong())).statusCode()).isEqualTo(403);
        var approved=participant(c);assertThat(c.teacher().send("POST","/api/author/contests/"+c.id()+"/participants/"+approved.path("id").asLong()+"/approve",command().put("baseRevision",approved.path("controlRevision").asLong())).statusCode()).isEqualTo(403);
        start(c);hidden(c,true);hidden(c,false);hidden(c,true);hidden(c,false);hidden(c,true);var p=participant(c);
        var decision=command().put("baseRevision",p.path("controlRevision").asLong()).put("reason","Checked the departure history");
        assertThat(c.teacher().send("POST","/api/author/contests/"+c.id()+"/participants/"+p.path("id").asLong()+"/revoke",decision).statusCode()).isEqualTo(403);
        ok(c.teacher().send("POST","/api/author/contests/"+c.id()+"/participants/"+p.path("id").asLong()+"/acknowledge",decision.deepCopy().put("requestId",key())),200);assertThat(participant(c).path("flagged").asBoolean()).isFalse();
        assertThat(c.teacher().send("POST","/api/author/contests/"+c.id()+"/close",command().put("baseRevision",authorView(c).path("revision").asLong()).put("reason","Not permitted")).statusCode()).isEqualTo(403);
    }
    @Test void paperFreezesOnPublicationAndApiRejectsForeignAnswersAndClientPenaltyCounters()throws Exception{
        var c=prepare(true);var current=authorView(c);assertThat(c.admin().send("PUT","/api/author/contests/"+c.id()+"/paper",command().put("baseRevision",current.path("revision").asLong()).set("questions",current.path("questions"))).statusCode()).isEqualTo(409);
        start(c);var forged=answerCommand(c,"yes");((ObjectNode)forged.path("answers")).set(key(),node("{\"type\":\"SINGLE_CHOICE\",\"optionId\":\"yes\"}"));assertThat(c.learner().send("POST","/api/me/contests/"+c.id()+"/save",forged).statusCode()).isEqualTo(400);
        var event=command().put("clientId",c.client()).put("hidden",true).put("tabLeaves",0);assertThat(c.learner().send("POST","/api/me/contests/"+c.id()+"/visibility",event).statusCode()).isEqualTo(400);assertThat(view(c).path("participation").path("tabLeaves").asInt()).isZero();
    }
    @Test void leaderboardUsesActualTimeAndSharesRanksForExactTies()throws Exception{
        var c=prepare(true);var second=users.create("contest-tie@test.local",passwords.encode("TestPass@123"),"Tied learner",Role.STUDENT);var other=new Client().login(second);
        var registration=ok(other.send("POST","/api/me/contests/"+c.id()+"/register",command()),201).path("participation");
        ok(c.admin().send("POST","/api/author/contests/"+c.id()+"/participants/"+registration.path("id").asLong()+"/approve",command().put("baseRevision",registration.path("controlRevision").asLong())),200);
        var opponent=new Case(c.admin(),other,c.teacher(),c.id(),key(),c.questionIds(),c.start(),c.end());ok(other.send("POST","/api/me/contests/"+c.id()+"/check-in",command().put("clientId",opponent.client())),200);start(c);
        ok(c.learner().send("POST","/api/me/contests/"+c.id()+"/submit",answerCommand(c,"yes")),200);ok(other.send("POST","/api/me/contests/"+c.id()+"/submit",answerCommand(opponent,"yes")),200);close(c);
        var board=ok(c.learner().send("GET","/api/contests/"+c.id()+"/leaderboard",null),200).path("items");assertThat(board).hasSize(2);assertThat(board.get(0).path("rank").asInt()).isEqualTo(1);assertThat(board.get(1).path("rank").asInt()).isEqualTo(1);assertThat(board.get(0).path("elapsedMillis").asLong()).isEqualTo(1000);assertThat(board.toString()).doesNotContain("userId","participantId");
    }
    @Test void allFourPrivateFormatsUseThePureGraderAndAllowNoContext()throws Exception{
        var single=(ObjectNode)question(key());single.putNull("context");
        var fill=(ObjectNode)question(key());fill.put("type","FILL_BLANK");fill.set("content",node("{\"parts\":[{\"kind\":\"TEXT\",\"text\":\"Good \"},{\"kind\":\"BLANK\",\"blankId\":\"word\"}]}"));fill.set("answerKey",node("{\"blanks\":[{\"blankId\":\"word\",\"acceptedAnswers\":[\"morning\"]}],\"caseSensitive\":false,\"normalizeWhitespace\":true}"));
        var matching=(ObjectNode)question(key());matching.put("type","MATCHING");matching.set("content",node("{\"leftItems\":[{\"id\":\"a\",\"text\":\"One\"},{\"id\":\"b\",\"text\":\"Two\"}],\"rightItems\":[{\"id\":\"x\",\"text\":\"Mot\"},{\"id\":\"y\",\"text\":\"Hai\"}]}"));matching.set("answerKey",node("{\"pairs\":[{\"leftId\":\"a\",\"rightId\":\"x\"},{\"leftId\":\"b\",\"rightId\":\"y\"}]}"));
        var ordering=(ObjectNode)question(key());ordering.put("type","ORDERING");ordering.set("content",node("{\"tokens\":[{\"id\":\"i\",\"text\":\"I\"},{\"id\":\"learn\",\"text\":\"learn\"}]}"));ordering.set("answerKey",node("{\"acceptedOrders\":[[\"i\",\"learn\"]]}"));
        var c=prepare(true,List.of(single,fill,matching,ordering));start(c);var input=answerCommand(c,"yes");var a=json.createObjectNode();a.set(single.path("id").asText(),node("{\"type\":\"SINGLE_CHOICE\",\"optionId\":\"yes\"}"));a.set(fill.path("id").asText(),node("{\"type\":\"FILL_BLANK\",\"values\":[{\"blankId\":\"word\",\"value\":\" MORNING \"}]}"));a.set(matching.path("id").asText(),node("{\"type\":\"MATCHING\",\"pairs\":[{\"leftId\":\"a\",\"rightId\":\"x\"},{\"leftId\":\"b\",\"rightId\":\"y\"}]}"));a.set(ordering.path("id").asText(),node("{\"type\":\"ORDERING\",\"tokenIds\":[\"i\",\"learn\"]}"));input.set("answers",a);
        ok(c.learner().send("POST","/api/me/contests/"+c.id()+"/submit",input),200);close(c);assertThat(view(c).path("result").path("correctCount").asInt()).isEqualTo(4);assertThat(view(c).path("result").path("items")).hasSize(4);
    }
    @Test void websocketReceivesCommittedContestInvalidationsWithoutQuestionOrAnswerContent()throws Exception{
        var c=prepare(true);start(c);var messages=new LinkedBlockingQueue<String>();var listener=new WebSocket.Listener(){public void onOpen(WebSocket s){s.request(1);}public CompletionStage<?> onText(WebSocket s,CharSequence data,boolean last){messages.add(data.toString());s.request(1);return null;}};
        String cookie=((CookieManager)c.learner().http.cookieHandler().orElseThrow()).getCookieStore().getCookies().stream().map(value->value.getName()+"="+value.getValue()).reduce((a,b)->a+"; "+b).orElseThrow();
        var socket=HttpClient.newHttpClient().newWebSocketBuilder().header("Cookie",cookie).header("Origin","http://localhost:5173").buildAsync(URI.create("ws://localhost:"+port+"/ws/events"),listener).join();
        try{assertThat(messages.poll(5,TimeUnit.SECONDS)).contains("CONNECTED");socket.sendText(json.writeValueAsString(Map.of("version",1,"type","SUBSCRIBE","requestId",key(),"resource","CONTEST","contestId",c.id())),true).join();assertThat(messages.poll(5,TimeUnit.SECONDS)).contains("ACK");hidden(c,true);var event=messages.poll(5,TimeUnit.SECONDS);assertThat(event).contains("CONTEST_CHANGED","contestId").doesNotContain("answerKey","EXAM_SECRET","Choose yes");}finally{socket.abort();}
    }
}
