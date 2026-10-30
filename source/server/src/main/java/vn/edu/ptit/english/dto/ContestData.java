package vn.edu.ptit.english.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.time.*;
import java.util.*;
import vn.edu.ptit.english.dto.AuthoringData.Question;
import static vn.edu.ptit.english.dto.MaterialRequests.invalid;

/** Contest transport never accepts curriculum IDs, grades, client clocks or penalty counters. */
public final class ContestData {
    private ContestData() {}
    public record Settings(String title,String description,Instant registrationClosesAt,Instant startsAt,Instant endsAt,
                           int penaltyMinutes,boolean shuffleQuestions,List<Long> proctorIds,Long paperId) {}
    public record Command(String requestId,Long baseRevision,Settings settings,JsonNode questions,String clientId,
                          Boolean hidden,String reason,JsonNode answers,JsonNode payload) {}
    public static Command parse(JsonNode n,String action) {
        List<String> fields=new ArrayList<>(List.of("requestId"));
        boolean revision=Set.of("draft","paper","publish","close","cancel","archive","approve","reject","revoke","acknowledge","save","submit").contains(action);
        if(revision)fields.add("baseRevision");
        if(Set.of("create","draft").contains(action))fields.addAll(List.of("title","description","registrationClosesAt","startsAt","endsAt","penaltyMinutes","shuffleQuestions","proctorIds","paperId"));
        if(action.equals("paper"))fields.add("questions");
        if(Set.of("check-in","claim","save","submit","visibility").contains(action))fields.add("clientId");
        if(Set.of("close","cancel","reject","revoke","acknowledge").contains(action))fields.add("reason");
        if(action.equals("visibility"))fields.add("hidden");
        if(Set.of("save","submit").contains(action))fields.add("answers");
        if(!Set.of("create","draft","paper","publish","close","cancel","archive","register","check-in","claim","save","submit","visibility","approve","reject","revoke","acknowledge").contains(action))throw invalid("action","Thao tác cuộc thi không hợp lệ.");
        AuthoringRequests.strict(n,fields.toArray(String[]::new));
        String key=AuthoringRequests.key(n);
        Settings settings=null;
        if(action.equals("create")||action.equals("draft")) {
            var registration=instant(n,"registrationClosesAt");var start=instant(n,"startsAt");var end=instant(n,"endsAt");
            if(registration.isAfter(start)||Duration.between(start,end).getSeconds()<60||Duration.between(start,end).getSeconds()>86400)throw invalid("endsAt","Giờ đăng ký không sau giờ bắt đầu; cuộc thi dài từ 1 phút đến 24 giờ.");
            long penalty=AuthoringRequests.number(n,"penaltyMinutes",1,false);
            if(penalty>120)throw invalid("penaltyMinutes","Mức phạt từ 1 đến 120 phút.");
            var shuffle=n.get("shuffleQuestions");if(shuffle==null||!shuffle.isBoolean())throw invalid("shuffleQuestions","Cần giá trị đúng/sai.");
            settings=new Settings(AuthoringRequests.text(n,"title",160,false,true).strip(),AuthoringRequests.text(n,"description",4000,false,false),registration,start,end,(int)penalty,shuffle.asBoolean(),AuthoringRequests.ids(n,"proctorIds",20),AuthoringRequests.number(n,"paperId",1,true));
            if(settings.title().isBlank())throw invalid("title","Cần tên cuộc thi.");
        }
        JsonNode questions=action.equals("paper")?paper(n.get("questions")):null;
        String client=fields.contains("clientId")?clientId(n):null;
        Boolean hidden=null;
        if(action.equals("visibility")){if(!n.path("hidden").isBoolean())throw invalid("hidden","Cần giá trị đúng/sai.");hidden=n.path("hidden").asBoolean();}
        String reason=fields.contains("reason")?AuthoringRequests.text(n,"reason",2000,false,true).strip():null;
        if(reason!=null&&reason.isBlank())throw invalid("reason","Cần ghi lý do xử lý.");
        JsonNode answers=fields.contains("answers")?answers(n.get("answers")):null;
        return new Command(key,revision?AuthoringRequests.number(n,"baseRevision",0,false):null,settings,questions,client,hidden,reason,answers,n.deepCopy());
    }
    public static String clientId(JsonNode n){String value=AuthoringRequests.text(n,"clientId",36,false,true);uuid(value,"clientId");return value;}
    public static Instant instant(JsonNode n,String key){try{return Instant.parse(AuthoringRequests.text(n,key,40,false,true));}catch(java.time.format.DateTimeParseException ex){throw invalid(key,"Cần ngày giờ ISO 8601 có múi giờ.");}}
    public static void uuid(String value,String field){if(!value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw invalid(field,"Cần mã UUID chữ thường.");}
    public static JsonNode paper(JsonNode value) {
        if(value==null||!value.isArray()||value.size()>100)throw invalid("questions","Đề có tối đa 100 câu.");
        var ids=new HashSet<String>();
        for(var item:value){AuthoringRequests.strict(item,"id","type","prompt","context","difficulty","content","answerKey","explanation");String id=AuthoringRequests.text(item,"id",36,false,true);uuid(id,"questions.id");if(!ids.add(id))throw invalid("questions.id","Mã câu hỏi bị trùng.");AuthoringRequests.text(item,"context",12000,true,false);gradingQuestion(item);}
        return value.deepCopy();
    }
    /** Reuse syntax and pure grading only. The synthetic curriculum IDs are never stored or resolved. */
    public static Question gradingQuestion(JsonNode question) {
        ObjectNode n=JsonNodeFactory.instance.objectNode();
        for(String name:List.of("type","prompt","difficulty","content","answerKey","explanation"))n.set(name,question.get(name));
        n.put("requestId","00000000-0000-0000-0000-000000000000");n.put("topicId",1);n.put("objectiveId",1);n.putNull("hint");n.putNull("materialVersionId");
        n.set("source",JsonNodeFactory.instance.objectNode().put("label","Đề cuộc thi riêng").putNull("url").putNull("license"));
        return AuthoringRequests.question(n,false).data();
    }
    public static JsonNode answers(JsonNode value) {
        if(value==null||!value.isObject()||value.size()>100)throw invalid("answers","Cần tập đáp án tối đa 100 câu.");
        var fields=value.fields();while(fields.hasNext()){var entry=fields.next();uuid(entry.getKey(),"answers");AttemptRequests.answer(entry.getValue(),true,"answers."+entry.getKey());}
        return value.deepCopy();
    }
    public static Map<String,Object> map(Object... pairs){var result=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;}
    public static void page(int page,int size){if(page<0||size<1||size>50)throw invalid("page","Trang từ 0, số dòng từ 1 đến 50.");}
}
