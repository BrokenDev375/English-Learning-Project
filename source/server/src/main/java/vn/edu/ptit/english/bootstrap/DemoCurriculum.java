package vn.edu.ptit.english.bootstrap;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.service.impl.QuestionValidator;

/** The versioned, original dataset is validated before any database/file mutation. */
@Component
public class DemoCurriculum {
    public record Material(String seedKey,String legacyAlias,String file,String sha256,JsonNode data) {}
    public record Question(String seedKey,String legacyAlias,String materialKey,String practiceRole,int position,JsonNode data) {}
    public record Lesson(String seedKey,String legacyAlias,JsonNode legacyBaseline,String unitSeedKey,String objectiveCode,String objectiveName,JsonNode data,List<Question> questions) {}
    public record Dataset(int schemaVersion,String datasetVersion,List<Material> materials,List<Lesson> lessons) {}
    private final Dataset dataset;
    public DemoCurriculum(ObjectMapper json,QuestionValidator validator)throws IOException {
        dataset=json.readValue(new ClassPathResource("demo/curriculum-v1.json").getInputStream(),Dataset.class);
        validate(validator);
    }
    public Dataset data(){return dataset;}
    public byte[] bytes(Material material)throws IOException{return new ClassPathResource("demo/materials/"+material.file()).getContentAsByteArray();}
    private void require(boolean condition,String detail){if(!condition)throw new IllegalStateException("Invalid demo curriculum: "+detail);}
    private void validate(QuestionValidator validator)throws IOException {
        require(dataset.schemaVersion()==1&&dataset.datasetVersion().equals("m02-demo-1.0.0"),"dataset version");
        require(dataset.lessons().size()==6&&dataset.materials().size()==13,"six lessons and thirteen materials");
        var keys=new HashSet<String>();var materialKeys=new HashSet<String>();var prompts=new HashSet<String>();var units=new HashMap<String,Integer>();var types=new HashMap<String,Integer>();int questionCount=0;
        for(var m:dataset.materials()){
            require(keys.add(m.seedKey()),"duplicate material key");materialKeys.add(m.seedKey());
            ObjectNode input=m.data().deepCopy();input.put("requestId","00000000-0000-0000-0000-000000000001");
            if(m.file()!=null){require(m.file().matches("[a-z0-9-]+\\.(wav|png)"),"resource filename");byte[] bytes=bytes(m);require(bytes.length>0&&bytes.length<=5242880,"file size");try{require(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(m.sha256()),"resource SHA-256: "+m.file());}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}input.put("assetId",1);}
            else require(input.path("kind").asText().equals("READING"),"file required for media");
            MaterialRequests.input(input,false);
        }
        for(var l:dataset.lessons()){
            require(keys.add(l.seedKey()),"duplicate lesson key");units.merge(l.unitSeedKey(),1,Integer::sum);require(l.questions().size()==15,"fifteen questions per lesson");
            var roles=new HashMap<String,Set<Integer>>();
            for(var q:l.questions()){
                require(keys.add(q.seedKey()),"duplicate question key");require(AuthoringRequests.ROLES.contains(q.practiceRole()),"practice role");
                require(q.materialKey()==null||materialKeys.contains(q.materialKey()),"material reference");
                require(roles.computeIfAbsent(q.practiceRole(),k->new HashSet<>()).add(q.position()),"duplicate position");
                ObjectNode input=q.data().deepCopy();input.put("requestId","00000000-0000-0000-0000-000000000001");input.put("topicId",1);input.put("objectiveId",1);
                var parsed=AuthoringRequests.question(input,false).data();require(validator.errors(parsed).isEmpty(),"question content: "+q.seedKey());
                require(!q.practiceRole().equals("GUIDED")||parsed.hint()!=null&&!parsed.hint().isBlank(),"guided hint");require(prompts.add(parsed.prompt()),"duplicate prompt");types.merge(parsed.type(),1,Integer::sum);questionCount++;
            }
            require(roles.size()==3&&roles.values().stream().allMatch(v->v.equals(Set.of(0,1,2,3,4))),"5/5/5 role positions");
        }
        require(questionCount==90&&types.equals(Map.of("SINGLE_CHOICE",30,"FILL_BLANK",30,"MATCHING",30)),"ninety questions, thirty per type");
        require(units.size()==3&&units.values().stream().allMatch(v->v==2),"two lessons per Unit");
    }
}
