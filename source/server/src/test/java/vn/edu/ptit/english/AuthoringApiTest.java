package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import vn.edu.ptit.english.dto.UserRecord;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.AssetService;
import static org.assertj.core.api.Assertions.*;

class AuthoringApiTest extends AuthoringFixture {
    @Test void rejectsStudentUnknownMissingNullableAndCrossTopic()throws Exception{
        var c=new Client().login(author);long[] cat=catalog(c);var d=single(cat[0],cat[1],"Question");
        assertThat(new Client().login(learner).send("POST","/api/author/questions",d).statusCode()).isEqualTo(403);
        d.put("ownerId",author.id());assertThat(c.send("POST","/api/author/questions",d).statusCode()).isEqualTo(400);d.remove("ownerId");d.remove("hint");assertThat(c.send("POST","/api/author/questions",d).statusCode()).isEqualTo(400);d.put("hint",null);d.put("topicId",cat[0]+999);assertThat(c.send("POST","/api/author/questions",d).statusCode()).isEqualTo(404);
        d.put("topicId",cat[0]);d.put("type","MULTIPLE_CHOICE");assertThat(c.send("POST","/api/author/questions",d).statusCode()).isEqualTo(400);
    }
    @Test void validatesAllThreeTypesAndPreservesPublishedHistory()throws Exception{
        var c=new Client().login(author);var cat=catalog(c);
        for(String type:List.of("SINGLE_CHOICE","FILL_BLANK","MATCHING")){
            var d=single(cat[0],cat[1],"Complete the sentence.");d.put("type",type);
            if(type.equals("FILL_BLANK")){d.put("content",Map.of("parts",List.of(Map.of("kind","TEXT","text","She "),Map.of("kind","BLANK","blankId","b1"),Map.of("kind","TEXT","text"," at seven."))));d.put("answerKey",Map.of("blanks",List.of(Map.of("blankId","b1","acceptedAnswers",List.of("gets up"))),"caseSensitive",false,"normalizeWhitespace",true));}
            if(type.equals("MATCHING")){d.put("content",Map.of("leftItems",List.of(Map.of("id","l1","text","seven"),Map.of("id","l2","text","eight")),"rightItems",List.of(Map.of("id","r1","text","7"),Map.of("id","r2","text","8"))));d.put("answerKey",Map.of("pairs",List.of(Map.of("leftId","l1","rightId","r1"),Map.of("leftId","l2","rightId","r2"))));}
            var root=q(c,d);var published=publishContent(c,"questions",root.path("id").asLong());long old=published.path("publishedVersionId").asLong();
            var draft=ok(c.send("POST","/api/author/questions/"+root.path("id").asLong()+"/draft",Map.of("requestId",key(),"baseRevision",published.path("rootRevision").asLong())),200);d.put("prompt","Changed prompt");ok(c.send("PUT","/api/author/questions/"+root.path("id").asLong()+"/draft",edit(draft,d)),200);publishContent(c,"questions",root.path("id").asLong());
            assertThat(ok(c.send("GET","/api/author/questions/"+root.path("id").asLong()+"/versions/"+old,null),200).path("prompt").asText()).isEqualTo("Complete the sentence.");
        }
    }
    @Test void draftAllowsIncompleteButPublishRejectsInvalidKeys()throws Exception{
        var c=new Client().login(author);var cat=catalog(c);var d=single(cat[0],cat[1],"");d.put("content",Map.of("options",List.of()));var answer=new HashMap<String,Object>();answer.put("correctOptionId",null);d.put("answerKey",answer);var root=q(c,d);var p=preview(c,"questions",root.path("id").asLong());assertThat(p.path("errors").size()).isGreaterThan(1);assertThat(new Client().login(admin).send("POST","/api/author/questions/"+root.path("id").asLong()+"/publish",command(p)).statusCode()).isEqualTo(400);
    }
    @Test void lessonPublishesMaterialAndQuestionClosureAtomically()throws Exception{
        var c=new Client().login(author);var cat=catalog(c);var material=create(c,reading("Shared reading"));var qs=new ArrayList<JsonNode>();for(int i=0;i<3;i++){var d=single(cat[0],cat[1],"Read question "+i);d.put("materialVersionId",material.path("draftVersionId").asLong());qs.add(q(c,d));}var root=createLesson(c,lessonData(c,cat,qs));var result=publishContent(c,"lessons",root.path("id").asLong());assertThat(result.path("draftVersionId").isNull()).isTrue();for(var question:qs)assertThat(ok(c.send("GET","/api/author/questions/"+question.path("id").asLong(),null),200).path("status").asText()).isEqualTo("PUBLISHED");assertThat(ok(c.send("GET","/api/author/materials/"+material.path("id").asLong(),null),200).path("status").asText()).isEqualTo("PUBLISHED");
    }
    @Test void invalidLessonDoesNotPartiallyPublishDependenciesOrReceipt()throws Exception{
        var c=new Client().login(author);var cat=catalog(c);var qs=questions(c,cat);var data=lessonData(c,cat,qs.subList(0,2));var root=createLesson(c,data);long receiptsBefore=receipts.count();var p=preview(c,"lessons",root.path("id").asLong());assertThat(p.path("errors").toString()).contains("MISSING_GROUP");assertThat(new Client().login(admin).send("POST","/api/author/lessons/"+root.path("id").asLong()+"/publish",command(p)).statusCode()).isEqualTo(400);assertThat(receipts.count()).isEqualTo(receiptsBefore);for(var question:qs)assertThat(ok(c.send("GET","/api/author/questions/"+question.path("id").asLong(),null),200).path("publishedVersionId").isNull()).isTrue();
    }
    @Test void dependencyEditInvalidatesLessonPreview()throws Exception{
        var c=new Client().login(author);var cat=catalog(c);var qs=questions(c,cat);var root=createLesson(c,lessonData(c,cat,qs));var p=preview(c,"lessons",root.path("id").asLong());var d=single(cat[0],cat[1],"A newer question");ok(c.send("PUT","/api/author/questions/"+qs.get(0).path("id").asLong()+"/draft",edit(qs.get(0),d)),200);var response=new Client().login(admin).send("POST","/api/author/lessons/"+root.path("id").asLong()+"/publish",command(p));assertThat(response.statusCode()).isEqualTo(409);assertThat(body(response).path("code").asText()).isEqualTo("PREVIEW_STALE");publishContent(c,"lessons",root.path("id").asLong());
    }
    @Test void foreignDraftHiddenAndCopyRetainsOriginRejectingDuplicateOriginInLesson()throws Exception{
        var c=new Client().login(author);var b=new Client().login(other);var cat=catalog(c);var original=q(c,single(cat[0],cat[1],"Original"));long id=original.path("id").asLong();assertThat(b.send("GET","/api/author/questions/"+id,null).statusCode()).isEqualTo(404);var published=publishContent(c,"questions",id);var copy=ok(b.send("POST","/api/author/questions/"+id+"/copy",Map.of("requestId",key(),"sourceVersionId",published.path("publishedVersionId").asLong())),201);assertThat(copy.path("originKey").asText()).isEqualTo(original.path("originKey").asText());assertThat(ok(b.send("GET","/api/author/questions/"+id,null),200).path("draft").isNull()).isTrue();
        var adminClient=new Client().login(admin);var third=q(c,single(cat[0],cat[1],"Third"));var publishedAsSlot=published.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)publishedAsSlot).put("draftVersionId",published.path("publishedVersionId").asLong());var root=createLesson(adminClient,lessonData(adminClient,cat,List.of(publishedAsSlot,copy,third)));assertThat(preview(adminClient,"lessons",root.path("id").asLong()).path("errors").toString()).contains("DUPLICATE_ORIGIN");
    }
    @Test void discardChecksReferencesAndReplays204()throws Exception{
        var c=new Client().login(author);var cat=catalog(c);var qs=questions(c,cat);var root=createLesson(c,lessonData(c,cat,qs));var q=qs.get(0);String qPath="/api/author/questions/"+q.path("id").asLong()+"/draft?requestId="+key()+"&draftVersionId="+q.path("draftVersionId").asLong()+"&baseRevision=0";assertThat(c.send("DELETE",qPath,null).statusCode()).isEqualTo(409);
        String path="/api/author/lessons/"+root.path("id").asLong()+"/draft?requestId="+key()+"&draftVersionId="+root.path("draftVersionId").asLong()+"&baseRevision=0";ok(c.send("DELETE",path,null),204);var replay=c.send("DELETE",path,null);assertThat(replay.statusCode()).isEqualTo(204);assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");ok(c.send("DELETE",qPath,null),204);
    }
    @Test void retireChecksPublishedOldQuestionVersionAndHistoricalReaderKeepsIt()throws Exception{
        var c=new Client().login(author);var cat=catalog(c);var qs=questions(c,cat);var root=publishContent(c,"lessons",createLesson(c,lessonData(c,cat,qs)).path("id").asLong());long qid=qs.get(0).path("id").asLong();var published=ok(c.send("GET","/api/author/questions/"+qid,null),200);var draft=ok(c.send("POST","/api/author/questions/"+qid+"/draft",Map.of("requestId",key(),"baseRevision",published.path("rootRevision").asLong())),200);var next=publishContent(c,"questions",qid);assertThat(new Client().login(admin).send("POST","/api/author/questions/"+qid+"/retire",Map.of("requestId",key(),"baseRevision",next.path("rootRevision").asLong())).statusCode()).isEqualTo(409);ok(new Client().login(admin).send("POST","/api/author/lessons/"+root.path("id").asLong()+"/retire",Map.of("requestId",key(),"baseRevision",root.path("rootRevision").asLong())),200);ok(new Client().login(admin).send("POST","/api/author/questions/"+qid+"/retire",Map.of("requestId",key(),"baseRevision",next.path("rootRevision").asLong())),200);assertThat(c.send("GET","/api/lessons/"+root.path("id").asLong(),null).statusCode()).isEqualTo(404);assertThat(ok(c.send("GET","/api/author/lessons/"+root.path("id").asLong()+"/versions/"+root.path("publishedVersionId").asLong(),null),200).path("questions").size()).isEqualTo(3);
    }
    @Test void materialEditInvalidatesQuestionPreviewAndCopyReplayIsStable()throws Exception{
        var c=new Client().login(author);var cat=catalog(c);var material=create(c,reading("Reading"));var data=single(cat[0],cat[1],"Question");data.put("materialVersionId",material.path("draftVersionId").asLong());var question=q(c,data);long id=question.path("id").asLong();var before=preview(c,"questions",id);var changed=reading("New reading title");ok(c.send("PUT","/api/author/materials/"+material.path("id").asLong()+"/draft",update(material,changed)),200);var stale=new Client().login(admin).send("POST","/api/author/questions/"+id+"/publish",command(before));assertThat(body(stale).path("code").asText()).isEqualTo("PREVIEW_STALE");var published=publishContent(c,"questions",id);var copy=Map.of("requestId",key(),"sourceVersionId",published.path("publishedVersionId").asLong());var b=new Client().login(other);var first=ok(b.send("POST","/api/author/questions/"+id+"/copy",copy),201);var replay=b.send("POST","/api/author/questions/"+id+"/copy",copy);assertThat(ok(replay,201)).isEqualTo(first);assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
    }
    @Test void lateDatabaseFailureRollsBackPublishedDependencies()throws Exception{
        var c=new Client().login(author);var cat=catalog(c);var material=create(c,reading("Atomic reading"));var qs=new ArrayList<JsonNode>();for(int i=0;i<3;i++){var d=single(cat[0],cat[1],"Atomic question "+i);d.put("materialVersionId",material.path("draftVersionId").asLong());qs.add(q(c,d));}var root=createLesson(c,lessonData(c,cat,qs));var command=command(preview(c,"lessons",root.path("id").asLong()));long before=receipts.count();
        new TransactionTemplate(transactions).executeWithoutResult(s->em.createNativeQuery("ALTER TABLE lessons ADD CONSTRAINT test_publication_failure CHECK (published_version_id IS NULL)").executeUpdate());
        try{assertThat(new Client().login(admin).send("POST","/api/author/lessons/"+root.path("id").asLong()+"/publish",command).statusCode()).isGreaterThanOrEqualTo(400);}finally{new TransactionTemplate(transactions).executeWithoutResult(s->em.createNativeQuery("ALTER TABLE lessons DROP CHECK test_publication_failure").executeUpdate());}
        assertThat(receipts.count()).isEqualTo(before);for(var q:qs)assertThat(ok(c.send("GET","/api/author/questions/"+q.path("id").asLong(),null),200).path("publishedVersionId").isNull()).isTrue();assertThat(ok(c.send("GET","/api/author/materials/"+material.path("id").asLong(),null),200).path("publishedVersionId").isNull()).isTrue();
        ok(new Client().login(admin).send("POST","/api/author/lessons/"+root.path("id").asLong()+"/publish",command),200);
    }
    @Test void concurrentDependencyEditAndPublishCannotSilentlyOverwritePreview()throws Exception{
        var c=new Client().login(author);var adminClient=new Client().login(admin);var cat=catalog(c);var qs=questions(c,cat);var root=createLesson(c,lessonData(c,cat,qs));var pub=command(preview(c,"lessons",root.path("id").asLong()));var edit=edit(qs.get(0),single(cat[0],cat[1],"Concurrent question"));var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{var a=pool.submit(()->{gate.await();return new Client().login(admin).send("POST","/api/author/lessons/"+root.path("id").asLong()+"/publish",pub);});var b=pool.submit(()->{gate.await();return adminClient.send("PUT","/api/author/questions/"+qs.get(0).path("id").asLong()+"/draft",edit);});gate.countDown();var published=a.get(30,TimeUnit.SECONDS);var edited=b.get(30,TimeUnit.SECONDS);assertThat(List.of(published.statusCode(),edited.statusCode())).containsExactlyInAnyOrder(200,409);if(published.statusCode()==200)assertThat(reader.questionVersion(qs.get(0).path("draftVersionId").asLong()).data().prompt()).isEqualTo("What time 0?");}finally{pool.shutdownNow();}
    }
    @Test void materialRetireAndLessonPublishSerializeWithoutBrokenPublishedReferences()throws Exception{
        var c=new Client().login(author);var adminClient=new Client().login(admin);var cat=catalog(c);var material=publish(c,create(c,reading("Concurrent material")));var qs=new ArrayList<JsonNode>();for(int i=0;i<3;i++){var d=single(cat[0],cat[1],"Concurrent material question "+i);d.put("materialVersionId",material.path("publishedVersionId").asLong());qs.add(q(c,d));}var root=createLesson(c,lessonData(c,cat,qs));var command=command(preview(c,"lessons",root.path("id").asLong()));var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{var a=pool.submit(()->{gate.await();return new Client().login(admin).send("POST","/api/author/lessons/"+root.path("id").asLong()+"/publish",command);});var b=pool.submit(()->{gate.await();return adminClient.send("POST","/api/author/materials/"+material.path("id").asLong()+"/retire",Map.of("requestId",key(),"baseRevision",material.path("rootRevision").asLong()));});gate.countDown();int published=a.get(30,TimeUnit.SECONDS).statusCode(),retired=b.get(30,TimeUnit.SECONDS).statusCode();assertThat(published==200&&retired==200).isFalse();assertThat(published==200||retired==200).isTrue();if(published==200){assertThat(retired).isEqualTo(409);assertThat(ok(c.send("GET","/api/author/materials/"+material.path("id").asLong(),null),200).path("status").asText()).isEqualTo("PUBLISHED");}else{assertThat(published).isIn(400,409);assertThat(retired).isEqualTo(200);}}finally{pool.shutdownNow();}
    }
    @Test void learnerCatalogExposesOnlyIntroductionsAndOrderRequiresCompleteList()throws Exception{
        var c=new Client().login(author);var student=new Client().login(learner);var cat=catalog(c);var qs=questions(c,cat);var first=createLesson(c,lessonData(c,cat,qs));assertThat(ok(student.send("GET","/api/units",null),200).path("totalElements").asLong()).isZero();var firstPublished=publishContent(c,"lessons",first.path("id").asLong());var second=createLesson(c,lessonData(c,cat,List.of()));var detail=ok(c.send("GET","/api/author/units/"+cat[2],null),200);long revision=detail.path("unit").path("revision").asLong();assertThat(c.send("PUT","/api/author/units/"+cat[2]+"/lesson-order",Map.of("requestId",key(),"baseRevision",revision,"lessonIds",List.of(first.path("id").asLong()))).statusCode()).isEqualTo(400);var order=Map.of("requestId",key(),"baseRevision",revision,"lessonIds",List.of(second.path("id").asLong(),first.path("id").asLong()));ok(c.send("PUT","/api/author/units/"+cat[2]+"/lesson-order",order),200);assertThat(c.send("PUT","/api/author/units/"+cat[2]+"/lesson-order",Map.of("requestId",key(),"baseRevision",revision,"lessonIds",List.of(first.path("id").asLong(),second.path("id").asLong()))).statusCode()).isEqualTo(409);
        assertThat(ok(student.send("GET","/api/topics",null),200).path("totalElements").asLong()).isEqualTo(1);assertThat(ok(student.send("GET","/api/objectives?topicId="+cat[0],null),200).path("totalElements").asLong()).isEqualTo(1);assertThat(ok(student.send("GET","/api/units/"+cat[2],null),200).path("lessons").size()).isEqualTo(1);var intro=student.send("GET","/api/lessons/"+first.path("id").asLong(),null);ok(intro,200);assertThat(intro.body()).doesNotContain("answerKey","correctOptionId","transcript","explanation","materialVersionId");
    }

    @Test
    void adminCreatedLessonKeepsCreatorAndIsEditableByTheAssignedTeacher() throws Exception {
        var teacher = new Client().login(author);
        var administrator = new Client().login(admin);
        var stranger = new Client().login(other);
        var cat = catalog(teacher);
        var data = lessonData(administrator, cat, List.of());

        var created = createLesson(administrator, data);

        assertThat(created.path("ownerId").asLong()).isEqualTo(admin.id());
        long lessonId = created.path("id").asLong();
        assertThat(ok(teacher.send("GET", "/api/author/units/" + cat[2], null), 200)
            .path("unit").path("ownerId").asLong()).isEqualTo(admin.id());
        assertThat(ok(teacher.send("GET", "/api/author/units/" + cat[2], null), 200).path("unit").path("assignedTeacherId").asLong()).isEqualTo(author.id());
        var ownLesson = ok(teacher.send("GET", "/api/author/lessons/" + lessonId, null), 200);
        assertThat(ownLesson.path("draftVersionId").asLong()).isEqualTo(created.path("draftVersionId").asLong());
        assertThat(stranger.send("GET", "/api/author/lessons/" + lessonId, null).statusCode()).isEqualTo(404);

        data.put("title", "Updated by the assigned teacher");
        var updated = ok(teacher.send("PUT", "/api/author/lessons/" + lessonId + "/draft", edit(ownLesson, data)), 200);
        assertThat(updated.path("ownerId").asLong()).isEqualTo(admin.id());
        assertThat(updated.path("draft").path("title").asText()).isEqualTo("Updated by the assigned teacher");
        assertThat(ok(administrator.send("GET", "/api/author/lessons/" + lessonId, null), 200)
            .path("ownerId").asLong()).isEqualTo(admin.id());

    }

    @Test
    void publishedMaterialFilesRemainReadableAfterReplacementWithoutExposingDrafts() throws Exception {
        var teacher = new Client().login(author);
        var borrower = new Client().login(other);
        var student = new Client().login(learner);
        var cat = catalog(borrower);
        var png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", png);
        long firstAsset = ok(teacher.upload(key(), "first.png", "image/png", png.toByteArray()), 201).path("id").asLong();
        long secondAsset = ok(teacher.upload(key(), "second.png", "image/png", png.toByteArray()), 201).path("id").asLong();
        var originalData = reading("Shared illustrated reading");
        originalData.put("assetId", firstAsset);
        originalData.put("altText", "A simple demo image");
        var original = publish(teacher, create(teacher, originalData));
        long materialId = original.path("id").asLong();
        long oldVersion = original.path("publishedVersionId").asLong();
        var questionData = single(cat[0], cat[1], "A question using the shared reading");
        questionData.put("materialVersionId", oldVersion);
        var question = q(borrower, questionData);
        var publishedQuestion = publishContent(borrower, "questions", question.path("id").asLong());

        var draft = ok(teacher.send("POST", "/api/author/materials/" + materialId + "/draft",
            Map.of("requestId", key(), "baseRevision", original.path("rootRevision").asLong())), 200);
        var newerData = reading("Revised illustrated reading");
        newerData.put("assetId", secondAsset);
        newerData.put("altText", "A replacement demo image");
        var saved = ok(teacher.send("PUT", "/api/author/materials/" + materialId + "/draft", update(draft, newerData)), 200);

        assertThat(borrower.file(firstAsset, null).statusCode()).isEqualTo(200);
        assertThat(borrower.file(secondAsset, null).statusCode()).isEqualTo(404);
        assertThat(borrower.send("GET", "/api/author/materials/" + materialId + "/versions/"
            + saved.path("draftVersionId").asLong(), null).statusCode()).isEqualTo(404);
        publish(teacher, saved);

        assertThat(ok(borrower.send("GET", "/api/author/materials/" + materialId + "/versions/" + oldVersion, null), 200)
            .path("assetId").asLong()).isEqualTo(firstAsset);
        assertThat(borrower.file(firstAsset, null).statusCode()).isEqualTo(200);
        assertThat(borrower.file(secondAsset, null).statusCode()).isEqualTo(200);
        assertThat(student.file(firstAsset, null).statusCode()).isEqualTo(404);

        var editableQuestion = ok(borrower.send("POST", "/api/author/questions/" + question.path("id").asLong() + "/draft",
            Map.of("requestId", key(), "baseRevision", publishedQuestion.path("rootRevision").asLong())), 200);
        assertThat(preview(borrower, "questions", question.path("id").asLong()).path("errors").isEmpty()).isTrue();
        publishContent(borrower, "questions", editableQuestion.path("id").asLong());
    }

    @Test
    void retiredMaterialKeepsFilesPrivateFromOtherAuthorsIncludingHistoricalVersions() throws Exception {
        var teacher = new Client().login(author);
        var borrower = new Client().login(other);
        var png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", png);
        long firstAsset = ok(teacher.upload(key(), "old.png", "image/png", png.toByteArray()), 201).path("id").asLong();
        long secondAsset = ok(teacher.upload(key(), "new.png", "image/png", png.toByteArray()), 201).path("id").asLong();
        var originalData = reading("Illustrated reading");
        originalData.put("assetId", firstAsset);
        originalData.put("altText", "The old image");
        var original = publish(teacher, create(teacher, originalData));
        long materialId = original.path("id").asLong();
        var draft = ok(teacher.send("POST", "/api/author/materials/" + materialId + "/draft",
            Map.of("requestId", key(), "baseRevision", original.path("rootRevision").asLong())), 200);
        var newerData = reading("Updated illustrated reading");
        newerData.put("assetId", secondAsset);
        newerData.put("altText", "The new image");
        var saved = ok(teacher.send("PUT", "/api/author/materials/" + materialId + "/draft", update(draft, newerData)), 200);
        var published = publish(teacher, saved);

        ok(teacher.send("POST", "/api/author/materials/" + materialId + "/retire",
            Map.of("requestId", key(), "baseRevision", published.path("rootRevision").asLong())), 200);

        assertThat(borrower.file(firstAsset, null).statusCode()).isEqualTo(404);
        assertThat(borrower.file(secondAsset, null).statusCode()).isEqualTo(404);
        assertThat(teacher.file(firstAsset, null).statusCode()).isEqualTo(200);
        assertThat(teacher.file(secondAsset, null).statusCode()).isEqualTo(200);
    }
}
