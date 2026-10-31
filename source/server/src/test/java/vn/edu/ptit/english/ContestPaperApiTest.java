package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class ContestPaperApiTest extends AuthoringFixture {
    @AfterEach void cleanup(){new TransactionTemplate(transactions).executeWithoutResult(t->{for(String sql:List.of("DELETE FROM contest_events","UPDATE contest_participants SET submission_id=NULL","DELETE FROM contest_submissions","DELETE FROM contest_participants","DELETE FROM contests","DELETE FROM contest_papers","DELETE FROM contest_teacher_permissions"))em.createNativeQuery(sql).executeUpdate();});}
    ObjectNode command(){return json.createObjectNode().put("requestId",key());}
    JsonNode node(String s){try{return json.readTree(s);}catch(Exception e){throw new IllegalArgumentException(e);}}
    ObjectNode draft(){var n=command().put("title","Reusable private paper").put("description","For a future contest");var q=(ObjectNode)node("{\"type\":\"SINGLE_CHOICE\",\"prompt\":\"Private greeting\",\"context\":null,\"difficulty\":\"EASY\",\"content\":{\"options\":[{\"id\":\"a\",\"text\":\"Hello\"},{\"id\":\"b\",\"text\":\"Bye\"}]},\"answerKey\":{\"correctOptionId\":\"a\"},\"explanation\":\"PRIVATE_KEY_EXPLANATION\"}");q.put("id",key());n.putArray("questions").add(q);return n;}
    JsonNode grant(Client a,long user,boolean author,boolean proctor)throws Exception{var rows=ok(a.send("GET","/api/author/contest-permissions?size=50",null),200).path("items");long revision=0;for(var row:rows)if(row.path("userId").asLong()==user)revision=row.path("revision").asLong();return ok(a.send("PUT","/api/author/contest-permissions/"+user,command().put("baseRevision",revision).put("canAuthor",author).put("canProctor",proctor)),200);}
    JsonNode ready(Client a,Client teacher)throws Exception{grant(a,author.id(),true,false);var p=ok(teacher.send("POST","/api/author/contest-papers",draft()),201);p=action(teacher,p,"submit");return action(a,p,"approve");}
    JsonNode action(Client c,JsonNode p,String action)throws Exception{return ok(c.send("POST","/api/author/contest-papers/"+p.path("id").asLong()+"/"+action,command().put("baseRevision",p.path("revision").asLong())),action.equals("clone")?201:200);}
    ObjectNode contest(long paper,Long proctor){var now=Instant.now();var n=command().put("title","Contest from bank").put("description","").put("registrationClosesAt",now.plusSeconds(1800).toString()).put("startsAt",now.plusSeconds(3600).toString()).put("endsAt",now.plusSeconds(5400).toString()).put("penaltyMinutes",10).put("shuffleQuestions",false).put("paperId",paper);var ids=n.putArray("proctorIds");if(proctor!=null)ids.add(proctor);return n;}

    @Test void authorAndProctorAreSeparateAdminGrantedCapabilities()throws Exception{
        var a=new Client().login(admin);var teacher=new Client().login(author);var caps=ok(teacher.send("GET","/api/author/contest-permissions/mine",null),200);assertThat(caps.path("canAuthor").asBoolean()).isFalse();assertThat(caps.path("canProctor").asBoolean()).isFalse();
        assertThat(teacher.send("POST","/api/author/contest-papers",draft()).statusCode()).isEqualTo(403);assertThat(teacher.send("GET","/api/author/contest-permissions",null).statusCode()).isEqualTo(403);
        grant(a,author.id(),true,false);var p=ok(teacher.send("POST","/api/author/contest-papers",draft()),201);p=action(teacher,p,"submit");p=action(a,p,"approve");assertThat(a.send("POST","/api/author/contests",contest(p.path("id").asLong(),author.id())).statusCode()).isEqualTo(400);
        grant(a,author.id(),true,true);assertThat(a.send("POST","/api/author/contests",contest(p.path("id").asLong(),author.id())).statusCode()).isEqualTo(201);
        assertThat(new Client().login(learner).send("GET","/api/author/contest-papers",null).statusCode()).isEqualTo(403);
    }
    @Test void reviewReturnAndApprovalFreezePaperAndOnlyReadyPaperCanBeSelected()throws Exception{
        var a=new Client().login(admin);var teacher=new Client().login(author);grant(a,author.id(),true,false);var p=ok(teacher.send("POST","/api/author/contest-papers",draft()),201);long id=p.path("id").asLong();
        assertThat(a.send("POST","/api/author/contests",contest(id,null)).statusCode()).isEqualTo(409);p=action(teacher,p,"submit");
        assertThat(teacher.send("POST","/api/author/contest-papers/"+id+"/approve",command().put("baseRevision",p.path("revision").asLong())).statusCode()).isEqualTo(403);
        p=ok(a.send("POST","/api/author/contest-papers/"+id+"/return",command().put("baseRevision",p.path("revision").asLong()).put("note","Clarify the greeting")),200);assertThat(p.path("state").asText()).isEqualTo("DRAFT");assertThat(p.path("reviewNote").asText()).isEqualTo("Clarify the greeting");
        p=action(teacher,p,"submit");p=action(a,p,"approve");var edit=draft().put("baseRevision",p.path("revision").asLong());assertThat(teacher.send("PUT","/api/author/contest-papers/"+id+"/draft",edit).statusCode()).isEqualTo(409);assertThat(ok(a.send("GET","/api/author/contest-papers?state=READY",null),200).path("items")).hasSize(1);
    }
    @Test void ownersAreScopedAndProctorsNeverReceivePrivateQuestionsOrKeys()throws Exception{
        var a=new Client().login(admin);var teacher=new Client().login(author);var p=ready(a,teacher);var foreign=new Client().login(other);grant(a,other.id(),true,true);
        assertThat(foreign.send("GET","/api/author/contest-papers/"+p.path("id").asLong(),null).statusCode()).isEqualTo(404);assertThat(ok(foreign.send("GET","/api/author/contest-papers",null),200).path("items")).isEmpty();
        var c=ok(a.send("POST","/api/author/contests",contest(p.path("id").asLong(),other.id())),201);var view=ok(foreign.send("GET","/api/author/contests/"+c.path("id").asLong(),null),200);assertThat(view.path("questions")).isEmpty();assertThat(view.toString()).doesNotContain("Private greeting","PRIVATE_KEY_EXPLANATION","answerKey");
        grant(a,other.id(),false,true);assertThat(foreign.send("GET","/api/author/contest-papers/"+p.path("id").asLong(),null).statusCode()).isEqualTo(403);assertThat(foreign.send("GET","/api/author/contests/"+c.path("id").asLong()+"/monitor",null).statusCode()).isEqualTo(200);
    }
    @Test void reuseCloneAndArchiveCannotChangeAnAlreadyPublishedContestSnapshot()throws Exception{
        var a=new Client().login(admin);var teacher=new Client().login(author);var p=ready(a,teacher);long paper=p.path("id").asLong();var one=ok(a.send("POST","/api/author/contests",contest(paper,null)),201);var two=ok(a.send("POST","/api/author/contests",contest(paper,null)),201);
        one=ok(a.send("POST","/api/author/contests/"+one.path("id").asLong()+"/publish",command().put("baseRevision",one.path("revision").asLong())),200);var frozen=one.path("questions");var copy=action(teacher,p,"clone");assertThat(copy.path("state").asText()).isEqualTo("DRAFT");assertThat(copy.path("questions").get(0).path("id")).isNotEqualTo(p.path("questions").get(0).path("id"));
        action(teacher,p,"archive");assertThat(ok(a.send("GET","/api/author/contests/"+one.path("id").asLong(),null),200).path("questions")).isEqualTo(frozen);assertThat(a.send("POST","/api/author/contests/"+two.path("id").asLong()+"/publish",command().put("baseRevision",two.path("revision").asLong())).statusCode()).isEqualTo(409);assertThat(ok(teacher.send("GET","/api/author/contest-papers/"+paper,null),200).path("usageCount").asInt()).isEqualTo(2);
    }
    @Test void revokedPermissionsBlockReadsWritesAndOldReceiptReplaysWithoutLoggingOutTheTeacher()throws Exception{
        var a=new Client().login(admin);var teacher=new Client().login(author);grant(a,author.id(),true,true);var request=draft();var p=ok(teacher.send("POST","/api/author/contest-papers",request),201);grant(a,author.id(),false,false);
        assertThat(teacher.send("GET","/api/author/contest-papers/"+p.path("id").asLong(),null).statusCode()).isEqualTo(403);assertThat(teacher.send("POST","/api/author/contest-papers",request).statusCode()).isEqualTo(403);assertThat(teacher.send("GET","/api/auth/me",null).statusCode()).isEqualTo(200);
    }
    @Test void paperSearchAppliesScopeStateAndLiteralKeywordsBeforePagination()throws Exception{
        var a=new Client().login(admin);var teacher=new Client().login(author);var foreign=new Client().login(other);grant(a,author.id(),true,false);grant(a,other.id(),true,false);
        for(String title:List.of("Grammar first","Grammar 100%_safe","Unpublished Grammar")){var p=ok(teacher.send("POST","/api/author/contest-papers",draft().put("title",title)),201);if(!title.startsWith("Unpublished")){p=action(teacher,p,"submit");action(a,p,"approve");}}
        ok(foreign.send("POST","/api/author/contest-papers",draft().put("title","A private foreign paper")),201);
        var page=ok(a.send("GET","/api/author/contest-papers?state=READY&q=Grammar&size=1&page=1",null),200);assertThat(page.path("totalElements").asInt()).isEqualTo(2);assertThat(page.path("totalPages").asInt()).isEqualTo(2);assertThat(page.path("items")).hasSize(1);assertThat(page.toString()).doesNotContain("answerKey","PRIVATE_KEY_EXPLANATION");
        assertThat(ok(a.send("GET","/api/author/contest-papers?q=%25&size=1",null),200).path("totalElements").asInt()).isEqualTo(1);
        assertThat(ok(a.send("GET","/api/author/contest-papers?q=%5F&size=1",null),200).path("totalElements").asInt()).isEqualTo(1);
        String owner=java.net.URLEncoder.encode(other.fullName(),java.nio.charset.StandardCharsets.UTF_8);
        assertThat(ok(a.send("GET","/api/author/contest-papers?q="+owner,null),200).path("totalElements").asInt()).isEqualTo(1);
        assertThat(ok(teacher.send("GET","/api/author/contest-papers?q="+owner,null),200).path("totalElements").asInt()).isZero();
        assertThat(a.send("GET","/api/author/contest-papers?q="+"x".repeat(161),null).statusCode()).isEqualTo(400);
    }
    @Test void proctorSearchFiltersEligibilityBeforePaginationAndAuthorViewKeepsSelectedNames()throws Exception{
        var a=new Client().login(admin);var teacher=new Client().login(author);var p=ready(a,teacher);grant(a,author.id(),true,true);grant(a,other.id(),false,true);
        var extra=users.create("search-proctor@test.local",passwords.encode("TestPass@123"),"Search Invigilator",vn.edu.ptit.english.enums.Role.TEACHER);grant(a,extra.id(),false,false);
        var page=ok(a.send("GET","/api/author/contest-permissions?proctorOnly=true&size=1&page=1",null),200);assertThat(page.path("totalElements").asInt()).isEqualTo(2);assertThat(page.path("items")).hasSize(1);
        assertThat(ok(a.send("GET","/api/author/contest-permissions?proctorOnly=true&q=search-proctor",null),200).path("totalElements").asInt()).isZero();
        grant(a,extra.id(),false,true);assertThat(ok(a.send("GET","/api/author/contest-permissions?proctorOnly=true&q=search-proctor",null),200).path("totalElements").asInt()).isEqualTo(1);
        var c=ok(a.send("POST","/api/author/contests",contest(p.path("id").asLong(),extra.id())),201);var selected=c.path("proctors").get(0);assertThat(selected.path("fullName").asText()).isEqualTo("Search Invigilator");assertThat(selected.path("eligible").asBoolean()).isTrue();
        grant(a,extra.id(),false,false);var changed=ok(a.send("GET","/api/author/contests/"+c.path("id").asLong(),null),200);assertThat(changed.path("proctorIds")).hasSize(1);assertThat(changed.path("proctors").get(0).path("eligible").asBoolean()).isFalse();
    }
    @Test void immutableReceiptsAndDraftRevisionsProtectConcurrentPaperEdits()throws Exception{
        var a=new Client().login(admin);var teacher=new Client().login(author);grant(a,author.id(),true,false);var request=draft();var p=ok(teacher.send("POST","/api/author/contest-papers",request),201);var replay=teacher.send("POST","/api/author/contest-papers",request);assertThat(replay.statusCode()).isEqualTo(201);assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");long id=p.path("id").asLong();var first=draft().put("baseRevision",p.path("revision").asLong());var second=first.deepCopy().put("requestId",key());var pool=Executors.newFixedThreadPool(2);
        try{var x=pool.submit(()->teacher.send("PUT","/api/author/contest-papers/"+id+"/draft",first));var y=pool.submit(()->teacher.send("PUT","/api/author/contest-papers/"+id+"/draft",second));assertThat(List.of(x.get().statusCode(),y.get().statusCode())).containsExactlyInAnyOrder(200,409);}finally{pool.shutdownNow();}
    }
}
