package vn.edu.ptit.english.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import vn.edu.ptit.english.dto.AttemptData.*;
import vn.edu.ptit.english.enums.AttemptEnums.Mode;
import vn.edu.ptit.english.exception.ApiException;
import org.springframework.http.HttpStatus;

/** Parse transport shape here; membership, ownership and state belong to AttemptService. */
public final class AttemptRequests {
    private AttemptRequests() {}
    public record Create(String requestId, Mode mode, Long lessonId, Long topicId,
                         String difficulty, Integer count, String reviewKind) {
        public Map<String,Object> selection() {
            var m=new LinkedHashMap<String,Object>(); m.put("mode",mode);
            switch(mode) {
                case LEARN -> m.put("lessonId",lessonId);
                case PRACTICE -> { m.put("topicId",topicId); m.put("difficulty",difficulty); m.put("count",count); }
                case REVIEW -> { m.put("reviewKind",reviewKind); m.put("topicId",topicId); m.put("count",count); }
            }
            return Collections.unmodifiableMap(m);
        }
        public Map<String,Object> payload() { var m=new LinkedHashMap<>(selection()); m.put("requestId",requestId); return m; }
    }
    public record Mutation(String requestId,long baseRevision,List<AnswerEntry> answers,PlayerState playerState) {
        public Mutation { answers=List.copyOf(answers); }
    }
    public record Hint(String requestId,long baseRevision) {}
    public record Check(String requestId,long baseRevision,Answer response) {}

    public static Create create(JsonNode n) {
        String mode=text(n,"mode",16,false,false,"mode");
        Mode parsed=enumValue(Mode.class,mode,"mode");
        switch(parsed) {
            case LEARN -> {
                fields(n,"body","requestId","mode","lessonId");
                return new Create(key(n),parsed,id(n,"lessonId",false),null,null,null,null);
            }
            case PRACTICE -> {
                fields(n,"body","requestId","mode","topicId","difficulty","count");
                String difficulty=text(n,"difficulty",8,false,false,"difficulty");
                if(!AuthoringRequests.DIFFICULTIES.contains(difficulty)) throw invalid("difficulty","Độ khó không hợp lệ.");
                return new Create(key(n),parsed,null,id(n,"topicId",false),difficulty,count(n),null);
            }
            default -> {
                fields(n,"body","requestId","mode","reviewKind","topicId","count");
                String kind=text(n,"reviewKind",16,false,false,"reviewKind");
                if(!Set.of("DUE","MISTAKES").contains(kind)) throw invalid("reviewKind","Loại ôn không hợp lệ.");
                return new Create(key(n),parsed,null,id(n,"topicId",true),null,count(n),kind);
            }
        }
    }
    public static Mutation mutation(JsonNode n) {
        fields(n,"body","requestId","baseRevision","answers","playerState");
        var answers=new ArrayList<AnswerEntry>(); var seen=new HashSet<Long>(); int i=0;
        for(var entry:array(n,"answers",100,"answers")) {
            String path="answers["+i++ +"]";
            fields(entry,path,"attemptQuestionId","response"); long id=id(entry,"attemptQuestionId",false);
            if(!seen.add(id)) throw invalid(path+".attemptQuestionId","Câu trả lời bị trùng.");
            answers.add(new AnswerEntry(id,answer(entry.get("response"),true,path+".response")));
        }
        return new Mutation(key(n),revision(n),answers,player(n.get("playerState")));
    }
    public static Hint hint(JsonNode n) {
        fields(n,"body","requestId","baseRevision"); return new Hint(key(n),revision(n));
    }
    public static Check check(JsonNode n) {
        fields(n,"body","requestId","baseRevision","response");
        return new Check(key(n),revision(n),answer(n.get("response"),false,"response"));
    }
    public static PlayerState player(JsonNode n) {
        fields(n,"playerState","currentAttemptQuestionId","audioPositions");
        var positions=new ArrayList<AudioPosition>(); var seen=new HashSet<Long>(); int i=0;
        for(var p:array(n,"audioPositions",100,"playerState.audioPositions")) {
            String path="playerState.audioPositions["+i++ +"]";
            fields(p,path,"materialVersionId","seconds"); long material=id(p,"materialVersionId",false);
            var seconds=p.get("seconds");
            if(!seen.add(material)||seconds==null||!seconds.isNumber()||!Double.isFinite(seconds.doubleValue())
                ||seconds.doubleValue()<0||seconds.doubleValue()>86400) throw invalid(path,"Vị trí nghe không hợp lệ.");
            positions.add(new AudioPosition(material,seconds.doubleValue()));
        }
        return new PlayerState(id(n,"currentAttemptQuestionId",true),positions);
    }
    public static Answer answer(JsonNode n,boolean nullable,String path) {
        if(n!=null&&n.isNull()&&nullable) return null;
        try {
            String type=text(n,"type",24,false,false,path+".type");
            switch(type) {
                case "SINGLE_CHOICE" -> {
                    fields(n,path,"type","optionId");
                    return new SingleAnswer(text(n,"optionId",40,true,false,path+".optionId"));
                }
                case "FILL_BLANK" -> {
                    fields(n,path,"type","values"); var values=new ArrayList<BlankValue>(); var seen=new HashSet<String>(); int i=0;
                    for(var v:array(n,"values",5,path+".values")) {
                        String p=path+".values["+i++ +"]"; fields(v,p,"blankId","value");
                        String id=text(v,"blankId",40,false,false,p+".blankId");
                        if(!seen.add(id)) throw invalid(p+".blankId","Ô trống bị trùng.");
                        values.add(new BlankValue(id,text(v,"value",200,false,true,p+".value")));
                    }
                    return new FillAnswer(values);
                }
                case "MATCHING" -> {
                    fields(n,path,"type","pairs"); var pairs=new ArrayList<Pair>(); var left=new HashSet<String>(); var right=new HashSet<String>(); int i=0;
                    for(var v:array(n,"pairs",6,path+".pairs")) {
                        String p=path+".pairs["+i++ +"]"; fields(v,p,"leftId","rightId");
                        String l=text(v,"leftId",40,false,false,p+".leftId"), r=text(v,"rightId",40,false,false,p+".rightId");
                        if(!left.add(l)||!right.add(r)) throw invalid(p,"Không ghép lặp một đầu.");
                        pairs.add(new Pair(l,r));
                    }
                    return new MatchingAnswer(pairs);
                }
                default -> throw invalid(path+".type","Loại đáp án không hợp lệ.");
            }
        } catch(ApiException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ANSWER",ex.getMessage(),ex.fieldErrors());
        }
    }
    private static int count(JsonNode n) {
        long c=integer(n,"count",1,10,false,"count"); return (int)c;
    }
    private static String key(JsonNode n) {
        String key=text(n,"requestId",36,false,false,"requestId");
        if(!key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw invalid("requestId","Cần UUID chữ thường.");
        return key;
    }
    private static long revision(JsonNode n) { return integer(n,"baseRevision",0,Long.MAX_VALUE,false,"baseRevision"); }
    private static Long id(JsonNode n,String key,boolean nullable) { return integer(n,key,1,9007199254740991L,nullable,key); }
    private static Long integer(JsonNode n,String key,long min,long max,boolean nullable,String path) {
        var v=n==null?null:n.get(key);
        if(v!=null&&v.isNull()&&nullable) return null;
        if(v==null||!v.isIntegralNumber()||!v.canConvertToLong()||v.longValue()<min||v.longValue()>max) throw invalid(path,"Cần số nguyên hợp lệ.");
        return v.longValue();
    }
    private static String text(JsonNode n,String key,int max,boolean nullable,boolean empty,String path) {
        var v=n==null?null:n.get(key);
        if(v!=null&&v.isNull()&&nullable) return null;
        if(v==null||!v.isTextual()||v.textValue().codePointCount(0,v.textValue().length())>max||(!empty&&v.textValue().isEmpty())) throw invalid(path,"Thông tin thiếu hoặc vượt độ dài.");
        return v.textValue();
    }
    private static JsonNode array(JsonNode n,String key,int max,String path) {
        var v=n==null?null:n.get(key);
        if(v==null||!v.isArray()||v.size()>max) throw invalid(path,"Danh sách thiếu hoặc quá dài.");
        return v;
    }
    private static void fields(JsonNode n,String path,String... names) {
        if(n==null||!n.isObject()) throw invalid(path,"Cần đối tượng.");
        var actual=new HashSet<String>(); n.fieldNames().forEachRemaining(actual::add);
        if(!actual.equals(Set.of(names))) throw invalid(path,"Các trường phải đúng và đủ theo hợp đồng.");
    }
    private static <T extends Enum<T>> T enumValue(Class<T> type,String value,String path) {
        try { return Enum.valueOf(type,value); } catch(IllegalArgumentException e) { throw invalid(path,"Giá trị không hợp lệ."); }
    }
    private static ApiException invalid(String path,String message) { return MaterialRequests.invalid(path,message); }
}
