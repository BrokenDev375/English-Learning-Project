package vn.edu.ptit.english.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.*;
import java.net.URI;
import java.util.*;
import org.springframework.http.HttpStatus;
import vn.edu.ptit.english.exception.ApiException;

/** Explicit one-of parsing preserves required nullable fields and rejects cross-kind/unknown fields. */
public final class MaterialRequests {
    private MaterialRequests(){}
    public record Input(String requestId,Long baseRevision,Long draftVersionId,MaterialData data) {
        public Map<String,Object> payload(){var result=data.fields();result.put("requestId",requestId);
            if(baseRevision!=null){result.put("baseRevision",baseRevision);result.put("draftVersionId",draftVersionId);}return result;}
    }
    public record Command(String requestId,long baseRevision,Long draftVersionId,String previewToken) {}
    public static Input input(JsonNode node,boolean update){
        object(node,"body");String kind=text(node,"kind",16,false,false);
        Set<String> fields=new HashSet<>(Set.of("requestId","kind","title","source"));
        switch(kind){
            case "READING" -> fields.addAll(Set.of("readingText","assetId","altText"));
            case "AUDIO" -> fields.addAll(Set.of("assetId","transcript","startSeconds","endSeconds"));
            case "IMAGE" -> fields.addAll(Set.of("assetId","altText","caption"));
            default -> throw invalid("kind","Loại tài liệu không hợp lệ.");
        }
        if(update)fields.addAll(Set.of("baseRevision","draftVersionId"));strict(node,fields);
        String requestId=requestId(node.get("requestId"));
        Long base=update?integer(node.get("baseRevision"),"baseRevision",0,false):null;
        Long draft=update?integer(node.get("draftVersionId"),"draftVersionId",1,false):null;
        String title=text(node,"title",160,false,false);
        var sourceNode=node.get("source");object(sourceNode,"source");strict(sourceNode,Set.of("label","url","license"));
        String label=text(sourceNode,"label",200,false,true).strip();
        String url=text(sourceNode,"url",2000,true,false),license=text(sourceNode,"license",200,true,false);
        if(url!=null){try{URI uri=URI.create(url);if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null)throw new IllegalArgumentException();}
            catch(IllegalArgumentException ex){throw invalid("source.url","Nguồn cần URL http hoặc https hợp lệ.");}}
        var source=new MaterialData.Source(label,url,license);
        Long asset=integer(node.get("assetId"),"assetId",1,kind.equals("READING"));
        String reading=kind.equals("READING")?text(node,"readingText",12000,false,false):null;
        String transcript=kind.equals("AUDIO")?text(node,"transcript",12000,false,false):null;
        String alt=!kind.equals("AUDIO")?text(node,"altText",500,kind.equals("READING"),false):null;
        String caption=kind.equals("IMAGE")?text(node,"caption",1000,false,false):null;
        BigDecimal start=kind.equals("AUDIO")?seconds(node.get("startSeconds"),"startSeconds",false):null;
        BigDecimal end=kind.equals("AUDIO")?seconds(node.get("endSeconds"),"endSeconds",true):null;
        if(end!=null&&end.compareTo(start)<=0)throw invalid("endSeconds","Điểm kết thúc phải sau điểm bắt đầu.");
        return new Input(requestId,base,draft,new MaterialData(kind,title,reading,asset,transcript,alt,caption,start,end,source));
    }
    public static Command command(JsonNode node,boolean publish){
        object(node,"body");strict(node,publish?Set.of("requestId","baseRevision","draftVersionId","previewToken"):Set.of("requestId","baseRevision"));
        String token=publish?text(node,"previewToken",64,false,true):null;
        if(token!=null&&!token.matches("[a-f0-9]{64}"))throw invalid("previewToken","Mã xem trước không hợp lệ.");
        return new Command(requestId(node.get("requestId")),integer(node.get("baseRevision"),"baseRevision",0,false),
            publish?integer(node.get("draftVersionId"),"draftVersionId",1,false):null,token);
    }
    public static Command discard(String requestId,long revision,long draft){
        requestId(com.fasterxml.jackson.databind.node.TextNode.valueOf(requestId));
        if(revision<0||draft<1||draft>9007199254740991L)throw invalid("baseRevision","Phiên bản không hợp lệ.");
        return new Command(requestId,revision,draft,null);
    }
    private static String requestId(JsonNode value){
        if(value==null||!value.isTextual()||!value.asText().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw invalid("requestId","Mã yêu cầu không hợp lệ.");
        return value.asText();
    }
    private static Long integer(JsonNode value,String path,long min,boolean nullable){
        if(value!=null&&value.isNull()&&nullable)return null;
        if(value==null||!value.isIntegralNumber()||!value.canConvertToLong()||value.asLong()<min||value.asLong()>9007199254740991L)throw invalid(path,"Cần số nguyên hợp lệ.");return value.asLong();
    }
    private static BigDecimal seconds(JsonNode value,String path,boolean nullable){
        if(value!=null&&value.isNull()&&nullable)return null;
        if(value==null||!value.isNumber())throw invalid(path,"Thời gian cần là số giây hợp lệ.");
        BigDecimal number=value.decimalValue();if(number.signum()<0||number.compareTo(BigDecimal.valueOf(86400))>0)throw invalid(path,"Thời gian phải từ 0 đến 86400 giây.");
        return number.setScale(3,RoundingMode.HALF_UP);
    }
    private static String text(JsonNode node,String name,int max,boolean nullable,boolean nonblank){
        JsonNode value=node.get(name);if(value!=null&&value.isNull()&&nullable)return null;
        if(value==null||!value.isTextual()||value.asText().length()>max||(nonblank&&value.asText().isBlank()))throw invalid(name,"Thông tin thiếu hoặc vượt độ dài cho phép.");return value.asText();
    }
    private static void object(JsonNode value,String path){if(value==null||!value.isObject())throw invalid(path,"Cần dữ liệu dạng đối tượng.");}
    private static void strict(JsonNode value,Set<String> keys){
        Set<String> actual=new HashSet<>();value.fieldNames().forEachRemaining(actual::add);
        if(!actual.equals(keys))throw invalid("body","Các trường phải đúng và đủ theo loại tài liệu đã chọn.");
    }
    public static ApiException invalid(String path,String message){return new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED",message,Map.of(path,message));}
}
