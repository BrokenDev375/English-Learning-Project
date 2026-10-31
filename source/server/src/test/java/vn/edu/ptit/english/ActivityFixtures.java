package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.dto.AuthoringData.*;

final class ActivityFixtures {
    static final ObjectMapper JSON=new ObjectMapper();
    record Fixture(Lesson lesson,Map<Long,Question> questions,Map<Long,MaterialData> materials) {}
    static JsonNode node(String value){try{return JSON.readTree(value);}catch(Exception e){throw new IllegalArgumentException(e);}}
    static Fixture fixture() { return fixture(false); }
    static Fixture fixture(boolean listening) {
        var source=new MaterialData.Source("Test",null,null);
        var materials=new LinkedHashMap<Long,MaterialData>();
        List.of("Example ____.","First ____. Next ____. Last ____.","A short example.","The main reading passage.").forEach(text->materials.put((long)materials.size()+1,new MaterialData("READING","Reading",text,null,null,null,null,null,null,source)));
        if(listening) {
            materials.put(2L,new MaterialData("READING","Reading","First ____. Next ____.",null,null,null,null,null,null,source));
            materials.put(5L,new MaterialData("AUDIO","Listening",null,99L,"SECRET_TRANSCRIPT",null,null,java.math.BigDecimal.ZERO,null,source));
        }
        var questions=new LinkedHashMap<Long,Question>();var slots=new ArrayList<Slot>();var activities=JSON.createArrayNode();
        int[] counts=listening?new int[]{3,2,2,3,3,3,2}:new int[]{3,2,2,3,4,4};String[] types={"SINGLE_CHOICE","MATCHING","ORDERING","FILL_BLANK","FILL_BLANK","SINGLE_CHOICE","SINGLE_CHOICE"};int guided=0,independent=0;
        for(int group=0;group<counts.length;group++) {
            var activity=activities.addObject().put("id","part-"+group).put("kind",LessonActivities.KINDS.get(group));var ids=activity.putArray("questionVersionIds");var passages=activity.putArray("passages");
            var groupIds=new ArrayList<Long>();
            for(int at=0;at<counts[group];at++) {
                long id=100+questions.size();groupIds.add(id);ids.add(id);Long material=null;
                if(group==4)material=at==0?1L:2L;
                if(group==5)material=at==0?3L:4L;
                if(group==6)material=5L;
                String content=switch(types[group]) {
                    case "SINGLE_CHOICE" -> "{\"options\":[{\"id\":\"a\",\"text\":\"A\"},{\"id\":\"b\",\"text\":\"B\"},{\"id\":\"c\",\"text\":\"C\"},{\"id\":\"d\",\"text\":\"D\"}]}";
                    case "MATCHING" -> "{\"leftItems\":[{\"id\":\"l1\",\"text\":\"One\"},{\"id\":\"l2\",\"text\":\"Two\"}],\"rightItems\":[{\"id\":\"r1\",\"text\":\"1\"},{\"id\":\"r2\",\"text\":\"2\"}]}";
                    case "ORDERING" -> "{\"tokens\":[{\"id\":\"t1\",\"text\":\"Hello\"},{\"id\":\"t2\",\"text\":\"world\"}]}";
                    default -> "{\"parts\":[{\"kind\":\"TEXT\",\"text\":\"Word: \"},{\"kind\":\"BLANK\",\"blankId\":\"word\"}]}";
                };
                String key=switch(types[group]) {
                    case "SINGLE_CHOICE" -> "{\"correctOptionId\":\"b\"}";
                    case "MATCHING" -> "{\"pairs\":[{\"leftId\":\"l1\",\"rightId\":\"r1\"},{\"leftId\":\"l2\",\"rightId\":\"r2\"}]}";
                    case "ORDERING" -> "{\"acceptedOrders\":[[\"t1\",\"t2\"]]}";
                    default -> "{\"blanks\":[{\"blankId\":\"word\",\"acceptedAnswers\":[\"secret-word\"]}],\"caseSensitive\":false,\"normalizeWhitespace\":true}";
                };
                questions.put(id,new Question(types[group],1,1,"EASY","Question "+id,node(content),node(key),"SECRET_EXPLANATION",at==0?"A useful hint":null,material,source));
                slots.add(new Slot(id,at==0?"GUIDED":"INDEPENDENT",at==0?guided++:independent++));
            }
            if(group==4) {
                var example=passages.addObject().put("materialVersionId",1).putArray("parts");text(example,"Example ");blank(example,groupIds.get(0));text(example,".");
                var main=passages.addObject().put("materialVersionId",2).putArray("parts");
                for(int i=1;i<counts[group];i++){text(main,List.of("First ",". Next ",". Last ").get(i-1));blank(main,groupIds.get(i));}text(main,".");
            }
        }
        return new Fixture(new Lesson("Sample","",node("[{\"kind\":\"TEXT\",\"text\":\"Read and answer.\"}]"),List.of(1L),slots,activities),questions,materials);
    }
    static void text(ArrayNode parts,String text){parts.addObject().put("kind","TEXT").put("text",text);}
    static void blank(ArrayNode parts,long id){parts.addObject().put("kind","BLANK").put("questionVersionId",id).put("blankId","word");}
}
