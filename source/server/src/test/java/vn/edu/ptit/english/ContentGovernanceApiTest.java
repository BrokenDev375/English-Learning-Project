package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ContentGovernanceApiTest extends AuthoringFixture {
    JsonNode submitted(Client c,String kind,long id)throws Exception{return ok(c.send("POST","/api/author/"+kind+"/"+id+"/submit-review",command(preview(c,kind,id))),201);}
    Map<String,Object> decision(JsonNode review,String note){return Map.of("requestId",key(),"baseRevision",review.path("revision").asLong(),"note",note);}
    JsonNode approve(JsonNode submitted)throws Exception{var r=submitted.path("review");return ok(new Client().login(admin).send("POST","/api/author/reviews/"+r.path("id").asLong()+"/approve",decision(r,"Đã kiểm tra")),200);}
    JsonNode readyLesson(Client c,long[] catalog)throws Exception{return createLesson(c,lessonData(c,catalog,questions(c,catalog)));}
    Map<String,Object> assignment(long[] catalog,long teacher)throws Exception{
        var unit=ok(new Client().login(admin).send("GET","/api/author/units/"+catalog[2],null),200).path("unit");
        return Map.of("requestId",key(),"baseRevision",unit.path("revision").asLong(),"topicId",catalog[0],"title",unit.path("title").asText(),"description","","stage",1,"sortOrder",0,"roundNumber",unit.path("roundNumber").asInt(),"assignedTeacherId",teacher);
    }
    @Test void teachersCannotCreateOrReorderGlobalCurriculumAndAssignmentRequiresActiveTeacher()throws Exception{
        var c=new Client().login(author);var cat=catalog(c);var a=new Client().login(admin);
        ok(c.send("POST","/api/author/topics",Map.of("requestId",key(),"code","denied","name","Denied","description","")),403);
        ok(c.send("POST","/api/author/units",Map.of("requestId",key(),"topicId",cat[0],"title","Denied","description","","stage",1,"sortOrder",0)),403);
        var update=assignment(cat,other.id());ok(c.send("PUT","/api/author/units/"+cat[2],update),403);
        var invalid=new HashMap<>(update);invalid.put("assignedTeacherId",learner.id());ok(a.send("PUT","/api/author/units/"+cat[2],invalid),400);
        ok(c.send("GET","/api/author/teachers",null),403);var choices=ok(a.send("GET","/api/author/teachers",null),200);
        assertThat(choices.toString()).contains(author.fullName(),other.fullName()).doesNotContain("passwordHash","password_hash","@test.local");
    }
    @Test void assignedTeacherCanWriteLessonsButAnotherTeacherCannotCreateOrReadPrivateLessons()throws Exception{
        var c=new Client().login(author);var b=new Client().login(other);var cat=catalog(c);var root=readyLesson(c,cat);long id=root.path("id").asLong();
        ok(b.send("GET","/api/author/lessons/"+id,null),404);ok(b.send("GET","/api/author/units/"+cat[2],null),404);
        ok(b.send("POST","/api/author/lessons",lessonData(c,cat,questions(c,cat))),404);
        assertThat(ok(b.send("GET","/api/author/units",null),200).path("items")).isEmpty();
        assertThat(ok(c.send("GET","/api/author/lessons/"+id,null),200).path("canEdit").asBoolean()).isTrue();
    }
    @Test void submitFreezesDraftAndOnlyAdminApprovalPublishesTheExactClosure()throws Exception{
        var c=new Client().login(author);var b=new Client().login(other);var student=new Client().login(learner);var cat=catalog(c);var root=readyLesson(c,cat);long id=root.path("id").asLong();
        var input=command(preview(c,"lessons",id));var response=c.send("POST","/api/author/lessons/"+id+"/submit-review",input);var submitted=ok(response,201);var review=submitted.path("review");
        assertThat(submitted.path("content").path("review").path("state").asText()).isEqualTo("PENDING");
        assertThat(ok(c.send("POST","/api/author/lessons/"+id+"/submit-review",input),201)).isEqualTo(submitted);
        var current=ok(c.send("GET","/api/author/lessons/"+id,null),200);var data=lessonData(c,cat,questions(c,cat));data.remove("unitId");data.remove("unitBaseRevision");
        ok(c.send("PUT","/api/author/lessons/"+id+"/draft",edit(current,data)),409);
        var direct=new HashMap<>(input);direct.put("requestId",key());
        ok(c.send("POST","/api/author/lessons/"+id+"/publish",direct),403);
        ok(new Client().login(admin).send("POST","/api/author/lessons/"+id+"/publish",direct),409);
        ok(student.send("GET","/api/lessons/"+id,null),404);ok(b.send("GET","/api/author/reviews/"+review.path("id").asLong(),null),404);
        ok(c.send("POST","/api/author/reviews/"+review.path("id").asLong()+"/approve",decision(review,"")),403);
        var a=new Client().login(admin);var decide=decision(review,"Approved");String path="/api/author/reviews/"+review.path("id").asLong()+"/approve";
        var approved=ok(a.send("POST",path,decide),200);assertThat(approved.path("state").asText()).isEqualTo("APPROVED");assertThat(ok(a.send("POST",path,decide),200)).isEqualTo(approved);
        assertThat(ok(student.send("GET","/api/lessons/"+id,null),200).path("lesson").path("title").asText()).isEqualTo("Morning routines");
        assertThat(ok(c.send("GET","/api/author/lessons/"+id,null),200).path("publishedVersionId").asLong()).isEqualTo(review.path("versionId").asLong());
    }
    @Test void returnsRequireReasonAndResubmissionKeepsImmutablePreviousSnapshot()throws Exception{
        var c=new Client().login(author);var cat=catalog(c);var root=readyLesson(c,cat);long id=root.path("id").asLong();var submitted=submitted(c,"lessons",id);var review=submitted.path("review");var a=new Client().login(admin);
        String path="/api/author/reviews/"+review.path("id").asLong();ok(a.send("POST",path+"/return",decision(review,"   ")),400);
        ok(a.send("POST",path+"/return",decision(review,"Bổ sung phần hướng dẫn")),200);
        var before=ok(c.send("GET",path,null),200).path("snapshot");assertThat(before.path("data").path("lesson").path("title").asText()).isEqualTo("Morning routines");
        var existing=ok(c.send("GET","/api/author/lessons/"+id,null),200);var data=new HashMap<String,Object>();existing.path("draft").fields().forEachRemaining(e->{if(Set.of("title","description","instructions","objectiveIds","questions").contains(e.getKey()))data.put(e.getKey(),e.getValue());});data.put("title","Revised title");
        ok(c.send("PUT","/api/author/lessons/"+id+"/draft",edit(existing,data)),200);var again=submitted(c,"lessons",id);
        assertThat(again.path("review").path("id")).isNotEqualTo(review.path("id"));assertThat(ok(c.send("GET",path,null),200).path("snapshot")).isEqualTo(before);
        approve(again);
    }
    @Test void withdrawingUnlocksDraftAndOldSubmitReceiptDoesNotCreateAnotherRequest()throws Exception{
        var c=new Client().login(author);var cat=catalog(c);var root=q(c,single(cat[0],cat[1],"Pending question"));long id=root.path("id").asLong();var command=command(preview(c,"questions",id));
        var submitted=ok(c.send("POST","/api/author/questions/"+id+"/submit-review",command),201);var r=submitted.path("review");
        ok(c.send("POST","/api/author/reviews/"+r.path("id").asLong()+"/withdraw",decision(r,"")),200);
        ok(c.send("PUT","/api/author/questions/"+id+"/draft",edit(root,single(cat[0],cat[1],"Edited after withdrawing"))),200);
        assertThat(ok(c.send("POST","/api/author/questions/"+id+"/submit-review",command),201)).isEqualTo(submitted);
        var list=ok(c.send("GET","/api/author/reviews?state=ALL",null),200);assertThat(list.path("totalElements").asInt()).isEqualTo(1);assertThat(list.path("items").get(0).path("state").asText()).isEqualTo("WITHDRAWN");
    }
    @Test void dependencyChangeMakesReviewStaleAndCannotPublishAnyPartialClosure()throws Exception{
        var c=new Client().login(author);var cat=catalog(c);var m=create(c,reading("Original material"));var qs=new ArrayList<JsonNode>();
        for(int i=0;i<3;i++){var data=single(cat[0],cat[1],"Question "+i);data.put("materialVersionId",m.path("draftVersionId").asLong());qs.add(q(c,data));}
        var lesson=createLesson(c,lessonData(c,cat,qs));long id=lesson.path("id").asLong();var submitted=submitted(c,"lessons",id);var r=submitted.path("review");
        ok(c.send("PUT","/api/author/materials/"+m.path("id").asLong()+"/draft",update(m,reading("Changed material"))),200);
        var a=new Client().login(admin);String path="/api/author/reviews/"+r.path("id").asLong();assertThat(ok(a.send("GET",path,null),200).path("stale").asBoolean()).isTrue();
        var failed=a.send("POST",path+"/approve",decision(r,""));assertThat(failed.statusCode()).isEqualTo(409);assertThat(body(failed).path("code").asText()).isEqualTo("PREVIEW_STALE");
        assertThat(ok(c.send("GET","/api/author/lessons/"+id,null),200).path("publishedVersionId").isNull()).isTrue();for(var q:qs)assertThat(ok(c.send("GET","/api/author/questions/"+q.path("id").asLong(),null),200).path("publishedVersionId").isNull()).isTrue();
    }
    @Test void reassignmentRevokesFormerTeacherAndGrantsCurrentLessonDependenciesWithoutQuestionEditRights()throws Exception{
        var c=new Client().login(author);var b=new Client().login(other);var a=new Client().login(admin);var cat=catalog(c);var m=create(c,reading("Private source"));var qs=new ArrayList<JsonNode>();
        for(int i=0;i<3;i++){var d=single(cat[0],cat[1],"Private "+i);d.put("materialVersionId",m.path("draftVersionId").asLong());qs.add(q(c,d));}
        var root=createLesson(c,lessonData(c,cat,qs));long id=root.path("id").asLong();var submitted=submitted(c,"lessons",id);var r=submitted.path("review");
        ok(b.send("GET","/api/author/versions/questions/"+qs.get(0).path("draftVersionId").asLong(),null),404);
        ok(a.send("PUT","/api/author/units/"+cat[2],assignment(cat,other.id())),200);
        ok(c.send("GET","/api/author/lessons/"+id,null),404);ok(c.send("GET","/api/author/reviews/"+r.path("id").asLong(),null),404);
        assertThat(ok(b.send("GET","/api/author/lessons/"+id,null),200).path("canEdit").asBoolean()).isTrue();
        ok(b.send("GET","/api/author/versions/questions/"+qs.get(0).path("draftVersionId").asLong(),null),200);ok(b.send("GET","/api/author/versions/materials/"+m.path("draftVersionId").asLong(),null),200);
        ok(b.send("PUT","/api/author/questions/"+qs.get(0).path("id").asLong()+"/draft",edit(qs.get(0),single(cat[0],cat[1],"Forbidden"))),404);
        var rejected=a.send("POST","/api/author/reviews/"+r.path("id").asLong()+"/approve",decision(r,""));assertThat(body(rejected).path("code").asText()).isEqualTo("ASSIGNMENT_CHANGED");
        ok(b.send("POST","/api/author/reviews/"+r.path("id").asLong()+"/withdraw",decision(r,"Kiểm tra lại sau phân công")),200);approve(submitted(b,"lessons",id));
    }
    @Test void previousPublishedVersionRemainsAvailableUntilRevisedDraftIsApproved()throws Exception{
        var c=new Client().login(author);var s=new Client().login(learner);var cat=catalog(c);var root=readyLesson(c,cat);long id=root.path("id").asLong();approve(submitted(c,"lessons",id));
        var old=ok(s.send("GET","/api/lessons/"+id,null),200);var current=ok(c.send("GET","/api/author/lessons/"+id,null),200);
        var draft=ok(c.send("POST","/api/author/lessons/"+id+"/draft",Map.of("requestId",key(),"baseRevision",current.path("rootRevision").asLong())),200);
        var data=new HashMap<String,Object>();draft.path("draft").fields().forEachRemaining(e->{if(Set.of("title","description","instructions","objectiveIds","questions").contains(e.getKey()))data.put(e.getKey(),e.getValue());});data.put("title","Next version");
        ok(c.send("PUT","/api/author/lessons/"+id+"/draft",edit(draft,data)),200);var submitted=submitted(c,"lessons",id);assertThat(ok(s.send("GET","/api/lessons/"+id,null),200)).isEqualTo(old);
        approve(submitted);var next=ok(s.send("GET","/api/lessons/"+id,null),200);assertThat(next.path("lesson").path("title").asText()).isEqualTo("Next version");assertThat(next.path("lesson").path("lessonVersionId")).isNotEqualTo(old.path("lesson").path("lessonVersionId"));
    }
    @Test void questionReviewIsSettledWhenAdminApprovesItsContainingLesson()throws Exception{
        var c=new Client().login(author);var cat=catalog(c);var qs=questions(c,cat);var questionReview=submitted(c,"questions",qs.get(0).path("id").asLong());
        var lesson=createLesson(c,lessonData(c,cat,qs));approve(submitted(c,"lessons",lesson.path("id").asLong()));
        var review=ok(c.send("GET","/api/author/reviews/"+questionReview.path("review").path("id").asLong(),null),200);
        assertThat(review.path("review").path("state").asText()).isEqualTo("APPROVED");assertThat(review.path("review").path("note").asText()).contains("cùng yêu cầu bài học");
    }
    @Test void concurrentDecisionsCommitOneOutcomeAndCannotReuseTheReceiptForAnotherAction()throws Exception{
        var c=new Client().login(author);var cat=catalog(c);var root=readyLesson(c,cat);var r=submitted(c,"lessons",root.path("id").asLong()).path("review");var a=new Client().login(admin);var b=new Client().login(admin);String base="/api/author/reviews/"+r.path("id").asLong();
        var pool=Executors.newFixedThreadPool(2);var approve=decision(r,"Approved");var reject=decision(r,"Needs work");
        try{var x=pool.submit(()->a.send("POST",base+"/approve",approve));var y=pool.submit(()->b.send("POST",base+"/return",reject));assertThat(List.of(x.get(30,TimeUnit.SECONDS).statusCode(),y.get(30,TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200,409);}finally{pool.shutdownNow();}
        var finalState=ok(a.send("GET",base,null),200).path("review").path("state").asText();assertThat(finalState).isIn("APPROVED","RETURNED");
        var committed=finalState.equals("APPROVED")?approve:reject;var otherAction=finalState.equals("APPROVED")?"return":"approve";var reused=a.send("POST",base+"/"+otherAction,committed);assertThat(reused.statusCode()).isEqualTo(409);assertThat(body(reused).path("code").asText()).isEqualTo("REQUEST_ID_REUSED");
    }
    @Test void studentsAndUnassignedAuthorsCannotReadReviewHistoryOrPrivateVersions()throws Exception{
        var c=new Client().login(author);var cat=catalog(c);var root=readyLesson(c,cat);var r=submitted(c,"lessons",root.path("id").asLong()).path("review");var b=new Client().login(other);var s=new Client().login(learner);
        ok(s.send("GET","/api/author/reviews",null),403);ok(s.send("GET","/api/author/reviews/"+r.path("id").asLong(),null),403);ok(b.send("GET","/api/author/reviews/"+r.path("id").asLong(),null),404);
        assertThat(ok(b.send("GET","/api/author/reviews?state=ALL",null),200).path("totalElements").asInt()).isZero();
        ok(c.send("POST","/api/author/lessons/"+root.path("id").asLong()+"/retire",Map.of("requestId",key(),"baseRevision",root.path("rootRevision").asLong())),403);
    }
    @Test void privateAssetReadGrantFollowsAssignmentAndIsRevokedOnUnassignment()throws Exception{
        var c=new Client().login(author);var b=new Client().login(other);var a=new Client().login(admin);var cat=catalog(c);
        var pixels=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(pixels,"png",bytes);
        long asset=ok(c.upload(key(),"private.png","image/png",bytes.toByteArray()),201).path("id").asLong();
        var material=create(c,new HashMap<>(Map.of("requestId",key(),"kind","IMAGE","title","Private image","assetId",asset,"altText","Lesson picture","caption","Test","source",Map.of("label","Test","url","https://example.com","license","Test"))));
        var qs=new ArrayList<JsonNode>();for(int i=0;i<3;i++){var d=single(cat[0],cat[1],"Image "+i);d.put("materialVersionId",material.path("draftVersionId").asLong());qs.add(q(c,d));}
        createLesson(c,lessonData(c,cat,qs));assertThat(b.file(asset,null).statusCode()).isEqualTo(404);
        ok(a.send("PUT","/api/author/units/"+cat[2],assignment(cat,other.id())),200);assertThat(b.file(asset,null).statusCode()).isEqualTo(200);
        assertThat(new Client().login(learner).file(asset,null).statusCode()).isEqualTo(404);
        var clear=new HashMap<>(assignment(cat,other.id()));clear.remove("assignedTeacherId");clear.put("clearAssignment",true);
        ok(a.send("PUT","/api/author/units/"+cat[2],clear),200);assertThat(b.file(asset,null).statusCode()).isEqualTo(404);
    }
    @Test void legacyTeacherCreateReceiptReplaysWithoutGrantingNewCurriculumWritePermission()throws Exception{
        var c=new Client().login(author);var cat=catalog(c);String requestId=key();
        var payload=new TreeMap<String,Object>(Map.of("topicId",cat[0],"title","Legacy round","description","","stage",1,"sortOrder",0));
        String canonical=json.writeValueAsString(payload);
        String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);
        JsonNode old=tx.execute(t->{
            int number=((Number)em.createQuery("select coalesce(max(u.roundNumber),0) from UnitEntity u").getSingleResult()).intValue()+1;
            var unit=new vn.edu.ptit.english.entity.UnitEntity(author.id(),cat[0],"Legacy round","",1,0,number);em.persist(unit);em.flush();
            var response=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(unit.view());response.remove("assignedTeacherId");
            receipts.saveAndFlush(new vn.edu.ptit.english.entity.RequestReceiptEntity(author.id(),requestId,"POST /api/author/units",digest,201,response));return response;
        });
        var command=new HashMap<String,Object>(payload);command.put("requestId",requestId);
        var replay=c.send("POST","/api/author/units",command);assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");assertThat(ok(replay,201)).isEqualTo(json.readTree(old.toString()));
        command.put("requestId",key());ok(c.send("POST","/api/author/units",command),403);
    }
}
