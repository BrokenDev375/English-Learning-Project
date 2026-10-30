package vn.edu.ptit.english.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static vn.edu.ptit.english.dto.MaterialRequests.invalid;

public final class ContestPaperData {
    private ContestPaperData() {}
    public record Command(String requestId,Long baseRevision,String title,String description,JsonNode questions,String note,Boolean canAuthor,Boolean canProctor,JsonNode payload) {}
    public static Command parse(JsonNode n,String action){
        var names=new ArrayList<>(List.of("requestId"));
        if(!action.equals("create"))names.add("baseRevision");
        if(Set.of("create","draft").contains(action))names.addAll(List.of("title","description","questions"));
        if(action.equals("return"))names.add("note");
        if(action.equals("permissions"))names.addAll(List.of("canAuthor","canProctor"));
        if(!Set.of("create","draft","submit","approve","return","archive","clone","permissions").contains(action))throw invalid("action","Thao tác bộ đề không hợp lệ.");
        AuthoringRequests.strict(n,names.toArray(String[]::new));
        String title=null,description=null,note=null;JsonNode questions=null;Boolean author=null,proctor=null;
        if(names.contains("title")){title=AuthoringRequests.text(n,"title",160,false,true).strip();if(title.isBlank())throw invalid("title","Cần tên bộ đề.");description=AuthoringRequests.text(n,"description",4000,false,false);questions=ContestData.paper(n.get("questions"));}
        if(names.contains("note")){note=AuthoringRequests.text(n,"note",2000,false,true).strip();if(note.isBlank())throw invalid("note","Cần ghi nội dung cần sửa.");}
        if(action.equals("permissions")){if(!n.path("canAuthor").isBoolean()||!n.path("canProctor").isBoolean())throw invalid("permissions","Cần giá trị đúng/sai cho từng quyền.");author=n.path("canAuthor").asBoolean();proctor=n.path("canProctor").asBoolean();}
        return new Command(AuthoringRequests.key(n),action.equals("create")?null:AuthoringRequests.number(n,"baseRevision",0,false),title,description,questions,note,author,proctor,n.deepCopy());
    }
}
