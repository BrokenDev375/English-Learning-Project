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

@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"app.demo.enabled=false","app.bootstrap-admin.enabled=false"})
@Testcontainers
class MaterialApiTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("english_material_test").withUsername("test_app").withPassword("test-password");
    static final Path FILES=temporary();
    static Path temporary(){try{return Files.createTempDirectory("english-material-test-");}catch(IOException ex){throw new UncheckedIOException(ex);}}
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url",() -> MYSQL.getJdbcUrl()+"?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        registry.add("spring.datasource.username",MYSQL::getUsername);registry.add("spring.datasource.password",MYSQL::getPassword);
        registry.add("app.assets.directory",FILES::toString);
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired UserJpaRepository userEntities;
    @Autowired AssetJpaRepository assets;
    @Autowired MaterialJpaRepository materials;
    @Autowired MaterialVersionJpaRepository versions;
    @Autowired RequestReceiptJpaRepository receipts;
    @Autowired PasswordEncoder passwords;
    @Autowired AssetService assetService;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    UserRecord author,other,admin,learner;
    @BeforeEach void fixtures()throws Exception{
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            em.createNativeQuery("DELETE FROM lesson_questions").executeUpdate();em.createNativeQuery("UPDATE lessons SET draft_version_id=NULL,published_version_id=NULL").executeUpdate();
            em.createNativeQuery("DELETE FROM lesson_objectives").executeUpdate();em.createNativeQuery("DELETE FROM lesson_versions").executeUpdate();em.createNativeQuery("DELETE FROM lessons").executeUpdate();
            em.createNativeQuery("UPDATE questions SET draft_version_id=NULL,published_version_id=NULL").executeUpdate();em.createNativeQuery("DELETE FROM question_versions").executeUpdate();em.createNativeQuery("DELETE FROM questions").executeUpdate();
            em.createNativeQuery("UPDATE materials SET draft_version_id=NULL,published_version_id=NULL").executeUpdate();
            versions.deleteAllInBatch();materials.deleteAllInBatch();assets.deleteAllInBatch();receipts.deleteAllInBatch();
            em.createNativeQuery("DELETE FROM units").executeUpdate();em.createNativeQuery("DELETE FROM learning_objectives").executeUpdate();em.createNativeQuery("DELETE FROM topics").executeUpdate();userEntities.deleteAllInBatch();
        });
        try(var files=Files.list(FILES)){for(Path path:files.toList())Files.delete(path);}
        String hash=passwords.encode("TestPass@123");author=users.create("author@test.local",hash,"Tác giả",Role.TEACHER);
        other=users.create("other@test.local",hash,"Tác giả khác",Role.TEACHER);admin=users.create("admin@test.local",hash,"Admin",Role.ADMIN);learner=users.create("learner@test.local",hash,"Học viên",Role.STUDENT);
    }
    final class Client {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();String csrf;
        HttpResponse<String> send(String method,String path,Object data)throws Exception{
            var request=HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(30));
            if(!method.equals("GET"))request.header("X-CSRF-TOKEN",csrf);
            if(data!=null)request.header("Content-Type","application/json");
            return http.send(request.method(method,data==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data))).build(),HttpResponse.BodyHandlers.ofString());
        }
        Client login(UserRecord user)throws Exception{
            csrf=body(send("GET","/api/auth/csrf",null)).path("token").asText();
            assertThat(send("POST","/api/auth/login",Map.of("email",user.email(),"password","TestPass@123")).statusCode()).isEqualTo(200);
            csrf=body(send("GET","/api/auth/csrf",null)).path("token").asText();return this;
        }
        HttpResponse<String> upload(String requestId,String name,String mime,byte[] bytes)throws Exception{
            String boundary="test-"+key();var out=new ByteArrayOutputStream();
            out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"requestId\"\r\n\r\n"+requestId+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+name+"\"\r\nContent-Type: "+mime+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(bytes);out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
            return http.send(HttpRequest.newBuilder(uri("/api/assets")).timeout(Duration.ofSeconds(30)).header("X-CSRF-TOKEN",csrf)
                .header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<byte[]> file(long id,String range)throws Exception{
            var request=HttpRequest.newBuilder(uri("/api/assets/"+id)).timeout(Duration.ofSeconds(20));if(range!=null)request.header("Range",range);
            return http.send(request.GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        }
    }
    URI uri(String path){return URI.create("http://127.0.0.1:"+port+path);}
    String key(){return UUID.randomUUID().toString();}
    JsonNode body(HttpResponse<String> response)throws Exception{return json.readTree(response.body());}
    Map<String,Object> reading(String title){var data=new LinkedHashMap<String,Object>();data.put("requestId",key());data.put("kind","READING");data.put("title",title);data.put("readingText","Linh gets up at seven.");data.put("assetId",null);data.put("altText",null);data.put("source",Map.of("label","Dự án tự soạn","url","https://example.com/source","license","Demo"));return data;}
    JsonNode create(Client client,Map<String,Object> data)throws Exception{var response=client.send("POST","/api/author/materials",data);assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(201);return body(response);}
    Map<String,Object> update(JsonNode root,Map<String,Object> data){var result=new LinkedHashMap<>(data);result.put("requestId",key());result.put("draftVersionId",root.path("draftVersionId").asLong());result.put("baseRevision",root.path("draft").path("revision").asLong());return result;}
    JsonNode publish(Client client,JsonNode root)throws Exception{long id=root.path("id").asLong();var preview=body(client.send("GET","/api/author/materials/"+id+"/preview",null));var response=client.send("POST","/api/author/materials/"+id+"/publish",Map.of("requestId",key(),"draftVersionId",preview.path("draftVersionId").asLong(),"baseRevision",preview.path("baseRevision").asLong(),"previewToken",preview.path("previewToken").asText()));assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);return body(response);}
    byte[] image(String format)throws IOException{var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(12,12,BufferedImage.TYPE_INT_RGB),format,out);return out.toByteArray();}
    byte[] wav(){int frames=32000;ByteBuffer b=ByteBuffer.allocate(44+frames*2).order(ByteOrder.LITTLE_ENDIAN);b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+frames*2).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16).put("data".getBytes(StandardCharsets.US_ASCII)).putInt(frames*2);for(int i=0;i<frames;i++)b.putShort((short)(Math.sin(i*2*Math.PI*440/16000)*6000));return b.array();}
    byte[] encoded(String extension)throws Exception{Path source=Files.createTempFile("test-audio-",".wav"),target=Files.createTempFile("test-audio-","."+extension);try{Files.write(source,wav());var p=new ProcessBuilder("ffmpeg","-nostdin","-v","error","-y","-i",source.toString(),target.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();assertThat(p.waitFor(15,TimeUnit.SECONDS)).isTrue();assertThat(p.exitValue()).isZero();return Files.readAllBytes(target);}finally{Files.deleteIfExists(source);Files.deleteIfExists(target);}}
    long upload(Client client,String name,byte[] bytes)throws Exception{var response=client.upload(key(),name,"application/octet-stream",bytes);assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(201);return body(response).path("id").asLong();}

    @Test void uploadsDetectRealFormatsAndDoNotExposeStorageKeys()throws Exception{
        var client=new Client().login(author);
        var png=client.upload(key(),"picture.exe","text/plain",image("png"));assertThat(png.statusCode()).isEqualTo(201);
        assertThat(body(png).path("mimeType").asText()).isEqualTo("image/png");assertThat(png.body()).doesNotContain("storageKey","storage_key");
        assertThat(client.file(body(png).path("id").asLong(),null).body()).isEqualTo(image("png"));
        assertThat(body(client.upload(key(),"photo.jpg","application/octet-stream",image("jpg"))).path("mimeType").asText()).isEqualTo("image/jpeg");
        for(String format:List.of("wav","mp3","ogg")){var result=client.upload(key(),"sound."+format,"text/plain",format.equals("wav")?wav():encoded(format));assertThat(result.statusCode()).withFailMessage(result.body()).isEqualTo(201);assertThat(body(result).path("mimeType").asText()).startsWith("audio/");}
    }
    @Test void uploadRejectsFakeOversizedEmptyAndPathFilesAndRequiresAuthor()throws Exception{
        var client=new Client().login(author);var student=new Client().login(learner);
        assertThat(student.upload(key(),"image.png","image/png",image("png")).statusCode()).isEqualTo(403);
        assertThat(client.upload(key(),"fake.png","image/png","<script>fake</script>".getBytes(StandardCharsets.UTF_8)).statusCode()).isEqualTo(400);
        assertThat(client.upload(key(),"empty.wav","audio/wav",new byte[0]).statusCode()).isEqualTo(400);
        assertThat(client.upload(key(),"../../escape.png","image/png",image("png")).statusCode()).isEqualTo(400);
        assertThat(client.upload(key(),"large.png","image/png",new byte[5242881]).statusCode()).isEqualTo(413);
        assertThat(assets.count()).isZero();assertThat(receipts.count()).isZero();try(var files=Files.list(FILES)){assertThat(files.count()).isZero();}
    }
    @Test void uploadRetryAndRollbackKeepDatabaseAndFileConsistent()throws Exception{
        var client=new Client().login(author);String requestId=key();var first=client.upload(requestId,"image.png","image/png",image("png"));
        var replay=client.upload(requestId,"image.png","application/octet-stream",image("png"));assertThat(replay.statusCode()).isEqualTo(201);assertThat(body(replay)).isEqualTo(body(first));assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(client.upload(requestId,"other.png","image/png",image("png")).statusCode()).isEqualTo(409);
        assertThat(assets.count()).isEqualTo(1);try(var files=Files.list(FILES)){assertThat(files.count()).isEqualTo(1);}
        var tx=new TransactionTemplate(transactions);
        byte[] png=image("png");
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {assetService.upload(new MemoryFile(png),key(),UserPrincipal.from(author));throw new IllegalStateException("Rollback fixture");})).isInstanceOf(IllegalStateException.class);
        assertThat(assets.count()).isEqualTo(1);assertThat(receipts.count()).isEqualTo(1);try(var files=Files.list(FILES)){assertThat(files.count()).isEqualTo(1);}
    }
    private record MemoryFile(byte[] bytes)implements MultipartFile{public String getName(){return "file";}public String getOriginalFilename(){return "rollback.png";}public String getContentType(){return "image/png";}public boolean isEmpty(){return false;}public long getSize(){return bytes.length;}public byte[] getBytes(){return bytes;}public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}public void transferTo(File file)throws IOException{Files.write(file.toPath(),bytes);}}

    @Test void audioReadsSupportRealRangesAndPrivateFilesAreHidden()throws Exception{
        var one=new Client().login(author);var two=new Client().login(other);var student=new Client().login(learner);var manager=new Client().login(admin);long id=upload(one,"voice.wav",wav());
        var range=one.file(id,"bytes=0-15");assertThat(range.statusCode()).isEqualTo(206);assertThat(range.body()).isEqualTo(Arrays.copyOf(wav(),16));assertThat(range.headers().firstValue("Content-Range").orElse("")).startsWith("bytes 0-15/");
        assertThat(one.file(id,"bytes=9999999-").statusCode()).isEqualTo(416);assertThat(one.file(id,"bytes=bad").statusCode()).isEqualTo(416);
        assertThat(two.file(id,null).statusCode()).isEqualTo(404);assertThat(student.file(id,null).statusCode()).isEqualTo(404);assertThat(manager.file(id,null).statusCode()).isEqualTo(200);
    }
    @Test void publishedFilesAreReusableWithoutRevealingPrivateDrafts()throws Exception{
        var one=new Client().login(author);var two=new Client().login(other);long file=upload(one,"image.png",image("png"));
        var data=reading("Original");data.put("assetId",file);data.put("altText","Original illustration");var root=create(one,data);long id=root.path("id").asLong();long draftId=root.path("draftVersionId").asLong();
        assertThat(two.send("GET","/api/author/materials/"+id,null).statusCode()).isEqualTo(404);root=publish(one,root);
        assertThat(two.file(file,null).statusCode()).isEqualTo(200);
        root=body(one.send("POST","/api/author/materials/"+id+"/draft",Map.of("requestId",key(),"baseRevision",root.path("rootRevision").asLong())));
        var privateEdit=update(root,reading("Private title"));privateEdit.put("assetId",file);privateEdit.put("altText","Private picture note");
        assertThat(one.send("PUT","/api/author/materials/"+id+"/draft",privateEdit).statusCode()).isEqualTo(200);
        var visible=body(two.send("GET","/api/author/materials/"+id,null));assertThat(visible.path("draft").isNull()).isTrue();assertThat(visible.path("draftVersionId").isNull()).isTrue();assertThat(visible.toString()).doesNotContain("Private title","Private picture note");
        assertThat(two.send("GET","/api/author/materials/"+id+"/versions/"+root.path("draftVersionId").asLong(),null).statusCode()).isEqualTo(404);
        assertThat(two.send("GET","/api/author/materials/"+id+"/versions/"+draftId,null).statusCode()).isEqualTo(200);
        var reused=create(two,data);assertThat(reused.path("ownerId").asLong()).isEqualTo(other.id());
        assertThat(two.send("GET","/api/author/materials?scope=ALL",null).statusCode()).isEqualTo(403);
        var published=body(two.send("GET","/api/author/materials?scope=PUBLISHED",null));assertThat(published.path("totalElements").asLong()).isEqualTo(1);assertThat(published.toString()).doesNotContain("Private title");
    }
    @Test void draftsCanBeIncompleteButPublishValidatesAndPreviewTokensGoStale()throws Exception{
        var client=new Client().login(author);var data=reading("");data.put("readingText","");var root=create(client,data);long id=root.path("id").asLong();
        var preview=body(client.send("GET","/api/author/materials/"+id+"/preview",null));assertThat(preview.path("errors").size()).isEqualTo(2);
        var command=new HashMap<String,Object>();command.put("requestId",key());command.put("draftVersionId",preview.path("draftVersionId").asLong());command.put("baseRevision",preview.path("baseRevision").asLong());command.put("previewToken",preview.path("previewToken").asText());
        var rejected=client.send("POST","/api/author/materials/"+id+"/publish",command);assertThat(rejected.statusCode()).isEqualTo(400);assertThat(body(rejected).path("fieldErrors").has("readingText")).isTrue();
        root=body(client.send("PUT","/api/author/materials/"+id+"/draft",update(root,reading("Complete"))));command.put("requestId",key());
        var stale=client.send("POST","/api/author/materials/"+id+"/publish",command);assertThat(stale.statusCode()).isEqualTo(409);assertThat(body(stale).path("code").asText()).isEqualTo("PREVIEW_STALE");
        assertThat(publish(client,root).path("status").asText()).isEqualTo("PUBLISHED");
    }
    @Test void publishedHistoryIsImmutableAndRetryReplaysOldResponse()throws Exception{
        var client=new Client().login(author);var root=publish(client,create(client,reading("Version one")));long id=root.path("id").asLong(),old=root.path("publishedVersionId").asLong();
        root=body(client.send("POST","/api/author/materials/"+id+"/draft",Map.of("requestId",key(),"baseRevision",root.path("rootRevision").asLong())));
        var command=update(root,reading("Version two"));var first=client.send("PUT","/api/author/materials/"+id+"/draft",command);root=body(first);
        root=body(client.send("PUT","/api/author/materials/"+id+"/draft",update(root,reading("Version three"))));
        assertThat(body(client.send("PUT","/api/author/materials/"+id+"/draft",command))).isEqualTo(body(first));
        root=publish(client,root);assertThat(root.path("published").path("versionNo").asInt()).isEqualTo(2);
        var history=body(client.send("GET","/api/author/materials/"+id+"/versions/"+old,null));assertThat(history.path("title").asText()).isEqualTo("Version one");
    }
    @Test void staleEditsStrictNullableFieldsAssetKindsAndAudioBoundsAreRejected()throws Exception{
        var client=new Client().login(author);long file=upload(client,"voice.wav",wav());var audio=new LinkedHashMap<String,Object>();audio.put("requestId",key());audio.put("kind","AUDIO");audio.put("title","Listen");audio.put("assetId",file);audio.put("transcript","Hello");audio.put("startSeconds",0);audio.put("endSeconds",null);audio.put("source",reading("x").get("source"));
        var missing=new LinkedHashMap<>(audio);missing.remove("endSeconds");assertThat(client.send("POST","/api/author/materials",missing).statusCode()).isEqualTo(400);
        var bounds=new LinkedHashMap<>(audio);bounds.put("endSeconds",5);assertThat(client.send("POST","/api/author/materials",bounds).statusCode()).isEqualTo(400);
        var mismatch=reading("Wrong kind");mismatch.put("assetId",file);assertThat(client.send("POST","/api/author/materials",mismatch).statusCode()).isEqualTo(400);
        var root=create(client,audio);long id=root.path("id").asLong();var command=update(root,audio);command.put("title","Changed");
        assertThat(client.send("PUT","/api/author/materials/"+id+"/draft",command).statusCode()).isEqualTo(200);command.put("requestId",key());
        var stale=client.send("PUT","/api/author/materials/"+id+"/draft",command);assertThat(stale.statusCode()).isEqualTo(409);assertThat(body(stale).path("code").asText()).isEqualTo("REVISION_CONFLICT");
        var forged=reading("Forged");forged.put("ownerId",other.id());assertThat(client.send("POST","/api/author/materials",forged).statusCode()).isEqualTo(400);
    }
    @Test void discardReplays204AndKeepsRootAndVersionNumbers()throws Exception{
        var client=new Client().login(author);var root=create(client,reading("Discard"));long id=root.path("id").asLong();String path="/api/author/materials/"+id+"/draft?requestId="+key()+"&draftVersionId="+root.path("draftVersionId").asLong()+"&baseRevision=0";
        var first=client.send("DELETE",path,null);assertThat(first.statusCode()).isEqualTo(204);assertThat(first.body()).isEmpty();
        assertThat(client.send("DELETE",path,null).statusCode()).isEqualTo(204);
        root=body(client.send("GET","/api/author/materials/"+id,null));assertThat(root.path("draft").isNull()).isTrue();assertThat(materials.count()).isEqualTo(1);
        root=body(client.send("POST","/api/author/materials/"+id+"/draft",Map.of("requestId",key(),"baseRevision",root.path("rootRevision").asLong())));
        assertThat(root.path("draft").path("versionNo").asInt()).isEqualTo(2);
    }
    @Test void retireKeepsOwnHistoryAndDoesNotRemoveSharedAssetReferences()throws Exception{
        var client=new Client().login(author);var second=new Client().login(other);long file=upload(client,"image.png",image("png"));var data=reading("Shared");data.put("assetId",file);data.put("altText","Picture");
        var root=publish(client,create(client,data));long id=root.path("id").asLong();create(second,data);
        var retired=client.send("POST","/api/author/materials/"+id+"/retire",Map.of("requestId",key(),"baseRevision",root.path("rootRevision").asLong()));assertThat(retired.statusCode()).isEqualTo(200);
        assertThat(body(retired).path("status").asText()).isEqualTo("RETIRED");assertThat(second.send("GET","/api/author/materials/"+id,null).statusCode()).isEqualTo(404);
        assertThat(second.file(file,null).statusCode()).isEqualTo(200);assertThat(client.send("GET","/api/author/materials/"+id+"/versions/"+root.path("publishedVersionId").asLong(),null).statusCode()).isEqualTo(200);
    }
    @Test void missingStoredFileReturns503InsteadOfEmptySuccess()throws Exception{
        var client=new Client().login(author);long id=upload(client,"image.png",image("png"));Files.delete(FILES.resolve(assets.findById(id).orElseThrow().getStorageKey()));
        var response=client.file(id,null);assertThat(response.statusCode()).isEqualTo(503);assertThat(new String(response.body(),StandardCharsets.UTF_8)).contains("ASSET_UNAVAILABLE");
    }
    private long insert(String sql,Object...values){var query=em.createNativeQuery(sql);for(int i=0;i<values.length;i++)query.setParameter(i+1,values[i]);query.executeUpdate();return ((Number)em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();}
    @Test void referencesProtectDraftAndRetireIncludingAnOldQuestionVersionInAPublishedLesson()throws Exception{
        var client=new Client().login(author);var root=create(client,reading("Referenced"));long materialId=root.path("id").asLong(),materialVersion=root.path("draftVersionId").asLong();
        long topic=body(client.send("POST","/api/author/topics",Map.of("requestId",key(),"code","reference-topic","name","Topic","description",""))).path("id").asLong();
        long objective=body(client.send("POST","/api/author/objectives",Map.of("requestId",key(),"topicId",topic,"code","reference-objective","name","Objective","description",""))).path("id").asLong();
        long unit=body(client.send("POST","/api/author/units",Map.of("requestId",key(),"topicId",topic,"title","Unit","description","","stage",1,"sortOrder",0))).path("id").asLong();
        var tx=new TransactionTemplate(transactions);
        long[] reference=tx.execute(status -> {
            long q=insert("INSERT INTO questions(owner_id,origin_key) VALUES (?1,?2)",author.id(),key());
            long v=insert("INSERT INTO question_versions(question_id,version_no,type,topic_id,objective_id,difficulty,prompt,content_json,answer_json,explanation,material_version_id,source_json) VALUES (?1,1,'SINGLE_CHOICE',?2,?3,'EASY','Fixture','{}','{}','Fixture',?4,'{}')",q,topic,objective,materialVersion);
            em.createNativeQuery("UPDATE questions SET draft_version_id=?1 WHERE id=?2").setParameter(1,v).setParameter(2,q).executeUpdate();return new long[]{q,v};
        });
        assertThat(client.send("DELETE","/api/author/materials/"+materialId+"/draft?requestId="+key()+"&draftVersionId="+materialVersion+"&baseRevision=0",null).statusCode()).isEqualTo(409);
        root=publish(client,root);
        long lesson=tx.execute(status -> {
            em.createNativeQuery("UPDATE question_versions SET state='PUBLISHED',published_at=NOW(6) WHERE id=?1").setParameter(1,reference[1]).executeUpdate();
            em.createNativeQuery("UPDATE questions SET draft_version_id=NULL,published_version_id=?1 WHERE id=?2").setParameter(1,reference[1]).setParameter(2,reference[0]).executeUpdate();
            long l=insert("INSERT INTO lessons(owner_id,unit_id) VALUES (?1,?2)",author.id(),unit);
            long lv=insert("INSERT INTO lesson_versions(lesson_id,version_no,state,title,instructions_json,published_at) VALUES (?1,1,'PUBLISHED','Fixture','[]',NOW(6))",l);
            em.createNativeQuery("UPDATE lessons SET published_version_id=?1 WHERE id=?2").setParameter(1,lv).setParameter(2,l).executeUpdate();
            insert("INSERT INTO lesson_questions(lesson_version_id,question_version_id,practice_role,sort_order) VALUES (?1,?2,'INDEPENDENT',0)",lv,reference[1]);
            long newer=insert("INSERT INTO question_versions(question_id,version_no,state,type,topic_id,objective_id,difficulty,prompt,content_json,answer_json,explanation,source_json,published_at) VALUES (?1,2,'PUBLISHED','SINGLE_CHOICE',?2,?3,'EASY','New fixture','{}','{}','Fixture','{}',NOW(6))",reference[0],topic,objective);
            em.createNativeQuery("UPDATE questions SET published_version_id=?1,last_version_no=2 WHERE id=?2").setParameter(1,newer).setParameter(2,reference[0]).executeUpdate();return l;
        });
        var rejected=client.send("POST","/api/author/materials/"+materialId+"/retire",Map.of("requestId",key(),"baseRevision",root.path("rootRevision").asLong()));
        assertThat(rejected.statusCode()).isEqualTo(409);assertThat(body(rejected).path("code").asText()).isEqualTo("CONTENT_IN_USE");
        tx.executeWithoutResult(status -> em.createNativeQuery("UPDATE lessons SET retired_at=NOW(6) WHERE id=?1").setParameter(1,lesson).executeUpdate());
        assertThat(client.send("POST","/api/author/materials/"+materialId+"/retire",Map.of("requestId",key(),"baseRevision",root.path("rootRevision").asLong())).statusCode()).isEqualTo(200);
    }
}
