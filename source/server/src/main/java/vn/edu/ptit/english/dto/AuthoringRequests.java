package vn.edu.ptit.english.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.*;
import static vn.edu.ptit.english.dto.MaterialRequests.invalid;
import vn.edu.ptit.english.dto.AuthoringData.*;

/** Explicit schema parsing: required nullable fields stay required; never coerce scalars. */
public final class AuthoringRequests {
    private AuthoringRequests() {}
    public record QuestionInput(String requestId,Long baseRevision,Long draftVersionId,Question data) {
        public Map<String,Object> payload(){var m=data.fields();m.put("requestId",requestId);if(baseRevision!=null){m.put("baseRevision",baseRevision);m.put("draftVersionId",draftVersionId);}return m;}
    }
    public record LessonInput(String requestId,Long baseRevision,Long draftVersionId,Long unitId,Long unitBaseRevision,Lesson data) {
        public Map<String,Object> payload(){var m=data.fields();m.put("requestId",requestId);if(baseRevision!=null){m.put("baseRevision",baseRevision);m.put("draftVersionId",draftVersionId);}else{m.put("unitId",unitId);m.put("unitBaseRevision",unitBaseRevision);}return m;}
    }
    public record Copy(String requestId,long sourceVersionId) {}
    public record Order(String requestId,long baseRevision,List<Long> lessonIds) {}
    public static final Set<String> TYPES=Set.of("SINGLE_CHOICE","FILL_BLANK","MATCHING"), DIFFICULTIES=Set.of("EASY","MEDIUM","HARD"), ROLES=Set.of("GUIDED","INDEPENDENT","REVIEW_VARIANT");
    public static void strict(JsonNode n,String... names){if(n==null||!n.isObject())throw invalid("body","Cần dữ liệu dạng đối tượng.");var actual=new HashSet<String>();n.fieldNames().forEachRemaining(actual::add);if(!actual.equals(Set.of(names)))throw invalid("body","Các trường phải đúng và đủ theo loại nội dung.");}
    public static String text(JsonNode n,String key,int max,boolean nullable,boolean nonempty){var v=n.get(key);if(v!=null&&v.isNull()&&nullable)return null;if(v==null||!v.isTextual()||v.asText().length()>max||(nonempty&&v.asText().isEmpty()))throw invalid(key,"Thông tin thiếu hoặc vượt độ dài cho phép.");return v.asText();}
    public static long id(long value){if(value<1||value>9007199254740991L)throw invalid("id","Mã nội dung không hợp lệ.");return value;}
    public static Long number(JsonNode n,String key,long min,boolean nullable){var v=n.get(key);if(v!=null&&v.isNull()&&nullable)return null;if(v==null||!v.isIntegralNumber()||!v.canConvertToLong()||v.asLong()<min||v.asLong()>9007199254740991L)throw invalid(key,"Cần số nguyên hợp lệ.");return v.asLong();}
    public static String key(JsonNode n){var v=text(n,"requestId",36,false,true);vn.edu.ptit.english.service.impl.AssetServiceImpl.validateRequestId(v);return v;}
    static String choice(JsonNode n,String key,Set<String> values){String v=text(n,key,30,false,true);if(!values.contains(v))throw invalid(key,"Giá trị lựa chọn không hợp lệ.");return v;}
    public static JsonNode array(JsonNode n,String key,int max){var v=n.get(key);if(v==null||!v.isArray()||v.size()>max)throw invalid(key,"Danh sách thiếu hoặc quá dài.");return v;}
    static void bool(JsonNode n,String key){if(n.get(key)==null||!n.get(key).isBoolean())throw invalid(key,"Cần giá trị đúng/sai.");}
    static MaterialData.Source source(JsonNode n){strict(n,"label","url","license");String label=text(n,"label",200,false,true).strip(),url=text(n,"url",2000,true,false),license=text(n,"license",200,true,false);if(label.isBlank())throw invalid("source.label","Cần ghi nguồn nội dung.");if(url!=null){try{var u=URI.create(url);if(!Set.of("http","https").contains(u.getScheme())||u.getHost()==null)throw new IllegalArgumentException();}catch(IllegalArgumentException e){throw invalid("source.url","Nguồn cần URL http hoặc https hợp lệ.");}}return new MaterialData.Source(label,url,license);}
    static void items(JsonNode n,String name){for(var item:array(n,name,6)){strict(item,"id","text");text(item,"id",40,false,true);text(item,"text",500,false,false);}}
    public static QuestionInput question(JsonNode n,boolean update){
        var fields=new ArrayList<>(List.of("requestId","type","topicId","objectiveId","difficulty","prompt","explanation","hint","materialVersionId","source","content","answerKey"));if(update)fields.addAll(List.of("baseRevision","draftVersionId"));strict(n,fields.toArray(String[]::new));
        String type=choice(n,"type",TYPES);var c=n.get("content");var a=n.get("answerKey");
        switch(type){
            case "SINGLE_CHOICE" -> {strict(c,"options");items(c,"options");strict(a,"correctOptionId");text(a,"correctOptionId",40,true,true);}
            case "FILL_BLANK" -> {strict(c,"parts");for(var p:array(c,"parts",21)){String kind=text(p,"kind",10,false,true);if(kind.equals("TEXT")){strict(p,"kind","text");text(p,"text",2000,false,false);}else if(kind.equals("BLANK")){strict(p,"kind","blankId");text(p,"blankId",40,false,true);}else throw invalid("content.parts","Loại đoạn không hợp lệ.");}strict(a,"blanks","caseSensitive","normalizeWhitespace");bool(a,"caseSensitive");bool(a,"normalizeWhitespace");for(var b:array(a,"blanks",5)){strict(b,"blankId","acceptedAnswers");text(b,"blankId",40,false,true);for(var v:array(b,"acceptedAnswers",10))if(!v.isTextual()||v.asText().isEmpty()||v.asText().length()>200)throw invalid("answerKey.blanks","Đáp án thiếu hoặc quá dài.");}}
            case "MATCHING" -> {strict(c,"leftItems","rightItems");items(c,"leftItems");items(c,"rightItems");strict(a,"pairs");for(var p:array(a,"pairs",6)){strict(p,"leftId","rightId");text(p,"leftId",40,false,true);text(p,"rightId",40,false,true);}}
        }
        return new QuestionInput(key(n),update?number(n,"baseRevision",0,false):null,update?number(n,"draftVersionId",1,false):null,
            new Question(type,number(n,"topicId",1,false),number(n,"objectiveId",1,false),choice(n,"difficulty",DIFFICULTIES),text(n,"prompt",2000,false,false),c.deepCopy(),a.deepCopy(),text(n,"explanation",4000,false,false),text(n,"hint",2000,true,false),number(n,"materialVersionId",1,true),source(n.get("source"))));
    }
    public static LessonInput lesson(JsonNode n,boolean update){
        var fields=new ArrayList<>(List.of("requestId","title","description","instructions","objectiveIds","questions"));fields.addAll(update?List.of("baseRevision","draftVersionId"):List.of("unitId","unitBaseRevision"));strict(n,fields.toArray(String[]::new));
        var instructions=array(n,"instructions",50);for(var item:instructions){String kind=text(item,"kind",10,false,true);if(kind.equals("TEXT")){strict(item,"kind","text");text(item,"text",8000,false,false);}else if(kind.equals("EXAMPLE")){strict(item,"kind","text","translation");text(item,"text",2000,false,false);text(item,"translation",2000,true,false);}else throw invalid("instructions","Loại hướng dẫn không hợp lệ.");}
        var objectives=ids(n,"objectiveIds",50);var slots=new ArrayList<Slot>();var versions=new HashSet<Long>();var positions=new HashSet<String>();
        for(var item:array(n,"questions",100)){strict(item,"questionVersionId","practiceRole","position");long version=number(item,"questionVersionId",1,false),position=number(item,"position",0,false);String role=choice(item,"practiceRole",ROLES);if(position>99||!versions.add(version)||!positions.add(role+"|"+position))throw invalid("questions","Câu hỏi hoặc vị trí bị trùng/không hợp lệ.");slots.add(new Slot(version,role,(int)position));}
        return new LessonInput(key(n),update?number(n,"baseRevision",0,false):null,update?number(n,"draftVersionId",1,false):null,update?null:number(n,"unitId",1,false),update?null:number(n,"unitBaseRevision",0,false),new Lesson(text(n,"title",160,false,false),text(n,"description",2000,false,false),instructions.deepCopy(),objectives,List.copyOf(slots)));
    }
    public static List<Long> ids(JsonNode n,String name,int max){var values=new ArrayList<Long>();for(var item:array(n,name,max)){if(!item.isIntegralNumber()||!item.canConvertToLong())throw invalid(name,"Cần mã nguyên hợp lệ.");values.add(id(item.asLong()));}if(new HashSet<>(values).size()!=values.size())throw invalid(name,"Mã bị trùng.");return List.copyOf(values);}
    public static Copy copy(JsonNode n){strict(n,"requestId","sourceVersionId");return new Copy(key(n),number(n,"sourceVersionId",1,false));}
    public static Order order(JsonNode n){strict(n,"requestId","baseRevision","lessonIds");return new Order(key(n),number(n,"baseRevision",0,false),ids(n,"lessonIds",100));}
}
