package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.dto.AttemptData.PlayerState;
import vn.edu.ptit.english.enums.AttemptEnums.Mode;
import static org.assertj.core.api.Assertions.*;

class WritingApiTest extends AuthoringFixture {
    @Override @BeforeEach void fixtures() throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            em.createNativeQuery("DELETE FROM unit_writing_grades").executeUpdate();
            em.createNativeQuery("UPDATE unit_writing_works SET latest_submission_id=NULL").executeUpdate();
            em.createNativeQuery("DELETE FROM unit_writing_submissions").executeUpdate();em.createNativeQuery("DELETE FROM unit_writing_works").executeUpdate();
            em.createNativeQuery("UPDATE unit_writing_tasks SET published_version_id=NULL").executeUpdate();em.createNativeQuery("DELETE FROM unit_writing_versions").executeUpdate();em.createNativeQuery("DELETE FROM unit_writing_tasks").executeUpdate();
            em.createNativeQuery("DELETE FROM lesson_progress").executeUpdate();em.createNativeQuery("DELETE FROM attempts").executeUpdate();
        });
        super.fixtures();
    }
    Map<String,Object> definition(String title){return Map.of("title",title,"prompt","Write about your daily routine.","minWords",80,"maxWords",120,"criteria",List.of(Map.of("id","content","text","At least three activities","points",6),Map.of("id","grammar","text","Use the present simple","points",4)));}
    String path(long unit){return "/api/author/units/"+unit+"/writing";}
    String me(long unit){return "/api/me/units/"+unit+"/writing";}
    JsonNode configured(Client c,long unit,String title)throws Exception{
        var t=ok(c.send("GET",path(unit),null),200);t=ok(c.send("PUT",path(unit)+"/draft",Map.of("requestId",key(),"baseRevision",t.path("revision").asLong(),"definition",definition(title))),200);
        return ok(c.send("POST",path(unit)+"/publish",Map.of("requestId",key(),"baseRevision",t.path("revision").asLong())),200);
    }
    long[] lesson(Client teacher)throws Exception{var cat=catalog(teacher);var l=createLesson(teacher,lessonData(teacher,cat,questions(teacher,cat)));publishContent(teacher,"lessons",l.path("id").asLong());return new long[]{cat[2],l.path("id").asLong()};}
    // The completion prerequisite is a DB fixture; the writing lifecycle itself uses real HTTP transactions.
    void completed(long lesson)throws Exception{new TransactionTemplate(transactions).executeWithoutResult(tx->{
        var l=em.find(LessonEntity.class,lesson);var a=new AttemptEntity(learner.id(),Mode.LEARN,l.getPublished().getId(),json.createObjectNode(),PlayerState.empty());em.persist(a);em.flush();var p=new LessonProgressEntity(learner.id(),lesson,a.getId());p.confirmCompletion(l.getPublished().getId(),java.time.Instant.now());em.persist(p);
    });}
    Map<String,Object> text(JsonNode view,String text){return Map.of("requestId",key(),"baseRevision",view.path("work").path("revision").asLong(),"text",text);}
    Map<String,Object> grade(JsonNode detail,boolean revise){return Map.of("requestId",key(),"baseRevision",detail.path("workRevision").asLong(),"marks",List.of(Map.of("criterionId","content","met",true,"comment","Clear activities"),Map.of("criterionId","grammar","met",false,"comment","Check verb forms")),"comment","Revise the grammar.","allowRevision",revise);}

    @Test void gatesWritingUntilLessonsCompleteAndSubmissionUnlocksNextRoundBeforeGrading()throws Exception{
        var teacher=new Client().login(author);var student=new Client().login(learner);var first=lesson(teacher);var second=lesson(teacher);configured(teacher,first[0],"Daily writing");
        assertThat(ok(student.send("GET",me(first[0]),null),200).path("unlocked").asBoolean()).isFalse();ok(student.send("POST",me(first[0])+"/start",Map.of("requestId",key())),409);
        completed(first[1]);var round=ok(student.send("GET","/api/me/rounds/"+first[0],null),200);assertThat(round.path("state").asText()).isEqualTo("IN_PROGRESS");assertThat(round.path("writing").path("submitted").asBoolean()).isFalse();assertThat(ok(student.send("GET","/api/me/rounds/"+second[0],null),200).path("locked").asBoolean()).isTrue();
        var start=ok(student.send("POST",me(first[0])+"/start",Map.of("requestId",key())),201);var submitted=ok(student.send("POST",me(first[0])+"/submit",text(start,"I wake up at seven. I go to school. I play football.")),200);
        assertThat(submitted.path("work").path("state").asText()).isEqualTo("SUBMITTED");assertThat(ok(student.send("GET","/api/me/rounds/"+second[0],null),200).path("locked").asBoolean()).isFalse();ok(student.send("POST",me(first[0])+"/draft",text(submitted,"Cannot edit")),409);
        long id=submitted.path("history").get(0).path("id").asLong();var detail=ok(teacher.send("GET","/api/author/writing-submissions/"+id,null),200);var graded=ok(teacher.send("POST","/api/author/writing-submissions/"+id+"/grade",grade(detail,true)),200);
        assertThat(graded.path("submission").path("grade").path("earnedPoints").asInt()).isEqualTo(6);assertThat(ok(student.send("GET","/api/me/rounds/"+second[0],null),200).path("locked").asBoolean()).isFalse();
        var returned=ok(student.send("GET",me(first[0]),null),200);var again=ok(student.send("POST",me(first[0])+"/submit",text(returned,"I wake up at seven every day. I study and play.")),200);assertThat(again.path("history")).hasSize(2);assertThat(again.path("history").get(1)).isEqualTo(returned.path("history").get(0));
    }
    @Test void pinningDraftSavingAndReceiptReplayKeepOriginalDefinitionAndImmutableSubmissions()throws Exception{
        var t=new Client().login(author);var s=new Client().login(learner);var l=lesson(t);var original=configured(t,l[0],"Original title");completed(l[1]);var startBody=Map.of("requestId",key());var start=ok(s.send("POST",me(l[0])+"/start",startBody),201);assertThat(ok(s.send("POST",me(l[0])+"/start",startBody),201)).isEqualTo(start);
        configured(t,l[0],"Replacement title");assertThat(ok(s.send("GET",me(l[0]),null),200).path("version").path("id")).isEqualTo(original.path("published").path("id"));
        var save=text(start,"My private draft.");var saved=ok(s.send("POST",me(l[0])+"/draft",save),200);assertThat(ok(s.send("POST",me(l[0])+"/draft",save),200)).isEqualTo(saved);ok(s.send("POST",me(l[0])+"/draft",text(start,"Stale draft")),409);
        var submit=text(saved,"Final text sent without depending on autosave.");var submitted=ok(s.send("POST",me(l[0])+"/submit",submit),200);assertThat(ok(s.send("POST",me(l[0])+"/submit",submit),200)).isEqualTo(submitted);assertThat(submitted.path("history")).hasSize(1);assertThat(submitted.path("history").get(0).path("text").asText()).isEqualTo(submit.get("text"));
        var changed=new HashMap<>(submit);changed.put("text","Other payload");assertThat(ok(s.send("POST",me(l[0])+"/submit",changed),409).path("code").asText()).isEqualTo("REQUEST_ID_REUSED");
        long id=submitted.path("history").get(0).path("id").asLong();var d=ok(t.send("GET","/api/author/writing-submissions/"+id,null),200);var input=grade(d,false);var g=ok(t.send("POST","/api/author/writing-submissions/"+id+"/grade",input),200);assertThat(ok(t.send("POST","/api/author/writing-submissions/"+id+"/grade",input),200)).isEqualTo(g);assertThat(g.path("version").path("definition").path("title").asText()).isEqualTo("Original title");
        assertThat(ok(s.send("GET",me(l[0]),null),200).path("work").path("state").asText()).isEqualTo("GRADED");
    }
    @Test void rolesOwnershipReassignmentAndExactRubricAreEnforced()throws Exception{
        var t=new Client().login(author);var b=new Client().login(other);var s=new Client().login(learner);var a=new Client().login(admin);var l=lesson(t);configured(t,l[0],"Private writing");completed(l[1]);
        ok(b.send("GET",path(l[0]),null),404);ok(s.send("GET",path(l[0]),null),403);ok(t.send("GET",me(l[0]),null),403);ok(a.send("GET",me(l[0]),null),403);
        var start=ok(s.send("POST",me(l[0])+"/start",Map.of("requestId",key())),201);var submitted=ok(s.send("POST",me(l[0])+"/submit",text(start,"Student private text")),200);long id=submitted.path("history").get(0).path("id").asLong();String sub="/api/author/writing-submissions/"+id;
        ok(b.send("GET",sub,null),404);ok(s.send("GET",sub,null),403);ok(b.send("GET","/api/author/writing-submissions?unitId="+l[0],null),404);
        var detail=ok(t.send("GET",sub,null),200);var invalid=new HashMap<>(grade(detail,false));invalid.put("marks",List.of(Map.of("criterionId","forged","met",true,"comment","")));ok(t.send("POST",sub+"/grade",invalid),400);
        assertThat(ok(s.send("GET",me(l[0]),null),200).path("work").path("state").asText()).isEqualTo("SUBMITTED");
        var u=ok(a.send("GET","/api/author/units/"+l[0],null),200).path("unit");ok(a.send("PUT","/api/author/units/"+l[0],Map.of("requestId",key(),"baseRevision",u.path("revision").asLong(),"topicId",u.path("topicId").asLong(),"title",u.path("title").asText(),"description","","stage",1,"sortOrder",0,"assignedTeacherId",other.id())),200);
        ok(t.send("GET",sub,null),404);ok(t.send("POST",sub+"/grade",grade(detail,false)),404);ok(b.send("POST",sub+"/grade",grade(ok(b.send("GET",sub,null),200),false)),200);
    }
    @Test void simultaneousSubmissionsAndGradesCommitOnlyOneBusinessMutation()throws Exception{
        var t=new Client().login(author);var s=new Client().login(learner);var l=lesson(t);configured(t,l[0],"Concurrent writing");completed(l[1]);var started=ok(s.send("POST",me(l[0])+"/start",Map.of("requestId",key())),201);var body=text(started,"Concurrent final text");var s2=new Client().login(learner);
        var pool=Executors.newFixedThreadPool(2);try {
            var tasks=pool.<java.net.http.HttpResponse<String>>invokeAll(List.of(()->s.send("POST",me(l[0])+"/submit",body),()->s2.send("POST",me(l[0])+"/submit",body)));for(var task:tasks)assertThat(task.get().statusCode()).isEqualTo(200);
            var view=ok(s.send("GET",me(l[0]),null),200);assertThat(view.path("history")).hasSize(1);long id=view.path("history").get(0).path("id").asLong();String path="/api/author/writing-submissions/"+id;var detail=ok(t.send("GET",path,null),200);var t2=new Client().login(author);var g1=grade(detail,false);var g2=grade(detail,true);
            var results=pool.<java.net.http.HttpResponse<String>>invokeAll(List.of(()->t.send("POST",path+"/grade",g1),()->t2.send("POST",path+"/grade",g2)));assertThat(results.stream().map(f->{try{return f.get().statusCode();}catch(Exception e){throw new RuntimeException(e);}}).toList()).containsExactlyInAnyOrder(200,409);
        }finally{pool.shutdownNow();}
    }
    @Test void invalidDefinitionsAndUnexpectedFieldsAreRejectedWithoutPublishing()throws Exception{
        var t=new Client().login(author);var l=lesson(t);var bad=new HashMap<>(definition("Invalid"));bad.put("criteria",List.of(Map.of("id","one","text","One","points",9)));
        ok(t.send("PUT",path(l[0])+"/draft",Map.of("requestId",key(),"baseRevision",0,"definition",bad)),400);assertThat(ok(t.send("GET",path(l[0]),null),200).path("published").isNull()).isTrue();
        ok(t.send("PUT",path(l[0])+"/draft",Map.of("requestId",key(),"baseRevision",0,"definition",definition("Good"),"studentId",learner.id())),400);
    }
}
