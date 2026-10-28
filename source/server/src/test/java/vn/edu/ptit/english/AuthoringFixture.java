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
abstract class AuthoringFixture {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("english_authoring_test").withUsername("test_app").withPassword("test-password");
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
    @Autowired vn.edu.ptit.english.service.PublishedContentReader reader;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    UserRecord author,other,admin,learner;
    @BeforeEach void fixtures()throws Exception{
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            em.createNativeQuery("DELETE FROM content_review_requests").executeUpdate();
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

    Map<String,Object> single(long topic,long objective,String prompt){var d=new LinkedHashMap<String,Object>();d.put("requestId",key());d.put("type","SINGLE_CHOICE");d.put("topicId",topic);d.put("objectiveId",objective);d.put("difficulty","EASY");d.put("prompt",prompt);d.put("explanation","Linh gets up at seven.");d.put("hint","Look for the time.");d.put("materialVersionId",null);d.put("source",Map.of("label","Dự án tự soạn","url","https://example.com","license","Demo"));d.put("content",Map.of("options",List.of(Map.of("id","a","text","Seven"),Map.of("id","b","text","Eight"))));d.put("answerKey",Map.of("correctOptionId","a"));return d;}
    JsonNode ok(HttpResponse<String> response,int code)throws Exception{assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(code);return response.body().isBlank()?json.nullNode():body(response);}
    long[] catalog(Client c)throws Exception{
        var manager=new Client().login(admin);var me=ok(c.send("GET","/api/auth/me",null),200);
        long topic=ok(manager.send("POST","/api/author/topics",Map.of("requestId",key(),"code","topic-"+key(),"name","Daily routines","description","")),201).path("id").asLong();
        long objective=ok(manager.send("POST","/api/author/objectives",Map.of("requestId",key(),"code","obj-"+key(),"topicId",topic,"name","Tell the time","description","")),201).path("id").asLong();
        var input=new HashMap<String,Object>(Map.of("requestId",key(),"topicId",topic,"title","Routines","description","","stage",1,"sortOrder",0));
        if(me.path("role").asText().equals("TEACHER"))input.put("assignedTeacherId",me.path("id").asLong());
        long unit=ok(manager.send("POST","/api/author/units",input),201).path("id").asLong();return new long[]{topic,objective,unit};
    }
    JsonNode q(Client c,Map<String,Object> d)throws Exception{return ok(c.send("POST","/api/author/questions",d),201);}
    Map<String,Object> lessonData(Client c,long[] cat,List<JsonNode> qs)throws Exception{var unit=ok(c.send("GET","/api/author/units/"+cat[2],null),200).path("unit");var d=new LinkedHashMap<String,Object>();d.put("requestId",key());d.put("unitId",cat[2]);d.put("unitBaseRevision",unit.path("revision").asLong());d.put("title","Morning routines");d.put("description","Read and practise.");d.put("instructions",List.of(Map.of("kind","TEXT","text","Read the passage and answer.")));d.put("objectiveIds",List.of(cat[1]));var slots=new ArrayList<Map<String,Object>>();String[] roles={"GUIDED","INDEPENDENT","REVIEW_VARIANT"};for(int i=0;i<qs.size();i++)slots.add(Map.of("questionVersionId",qs.get(i).path("draftVersionId").asLong(),"practiceRole",roles[i%3],"position",i/3));d.put("questions",slots);return d;}
    List<JsonNode> questions(Client c,long[] cat)throws Exception{var list=new ArrayList<JsonNode>();for(int i=0;i<3;i++)list.add(q(c,single(cat[0],cat[1],"What time "+i+"?")));return list;}
    JsonNode createLesson(Client c,Map<String,Object> data)throws Exception{return ok(c.send("POST","/api/author/lessons",data),201);}
    JsonNode preview(Client c,String kind,long id)throws Exception{return ok(c.send("GET","/api/author/"+kind+"/"+id+"/preview",null),200);}
    Map<String,Object> command(JsonNode p){return Map.of("requestId",key(),"draftVersionId",p.path("draftVersionId").asLong(),"baseRevision",p.path("baseRevision").asLong(),"previewToken",p.path("previewToken").asText());}
    JsonNode publishContent(Client c,String kind,long id)throws Exception{return ok(new Client().login(admin).send("POST","/api/author/"+kind+"/"+id+"/publish",command(preview(c,kind,id))),200);}
    Map<String,Object> edit(JsonNode r,Map<String,Object> data){var d=new LinkedHashMap<>(data);d.put("requestId",key());d.remove("unitId");d.remove("unitBaseRevision");d.put("draftVersionId",r.path("draftVersionId").asLong());d.put("baseRevision",r.path("draft").path("revision").asLong());return d;}

}
