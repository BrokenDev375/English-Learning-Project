package vn.edu.ptit.english.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import vn.edu.ptit.english.dto.AuthoringData.*;
import static vn.edu.ptit.english.dto.AuthoringRequests.*;
import static vn.edu.ptit.english.dto.MaterialRequests.invalid;

/** Versioned activity layout; no grading keys are stored in this structure. */
public final class LessonActivities {
    private LessonActivities() {}
    public static final List<String> KINDS=List.of("CHOICE","MATCHING","ORDERING","FILL","READING_CLOZE","READING_CHOICE","LISTENING_CHOICE");
    private static final List<String> TYPES=List.of("SINGLE_CHOICE","MATCHING","ORDERING","FILL_BLANK","FILL_BLANK","SINGLE_CHOICE","SINGLE_CHOICE");

    public static JsonNode parse(JsonNode value) {
        if(value==null)return JsonNodeFactory.instance.arrayNode();
        if(!value.isArray()||value.size()>7)throw invalid("activities","Tối đa bảy hoạt động.");
        var ids=new HashSet<String>();
        for(var activity:value) {
            strict(activity,"id","kind","questionVersionIds","passages");
            String key=text(activity,"id",40,false,true);
            if(!key.matches("[a-zA-Z0-9_-]+")||!ids.add(key))throw invalid("activities.id","Mã hoạt động phải duy nhất, chỉ gồm chữ, số, _ hoặc -.");
            if(!KINDS.contains(text(activity,"kind",30,false,true)))throw invalid("activities.kind","Loại hoạt động không hợp lệ.");
            var questions=ids(activity,"questionVersionIds",20);
            if(new HashSet<>(questions).size()!=questions.size())throw invalid("activities.questionVersionIds","Câu trong hoạt động bị trùng.");
            for(var passage:array(activity,"passages",2)) {
                strict(passage,"materialVersionId","parts");number(passage,"materialVersionId",1,false);
                int length=0;
                for(var part:array(passage,"parts",41)) {
                    String kind=text(part,"kind",10,false,true);
                    if(kind.equals("TEXT")){strict(part,"kind","text");length+=text(part,"text",12000,false,false).length();}
                    else if(kind.equals("BLANK")){strict(part,"kind","questionVersionId","blankId");number(part,"questionVersionId",1,false);text(part,"blankId",40,false,true);}
                    else throw invalid("activities.passages.parts","Loại đoạn không hợp lệ.");
                }
                if(length>12000)throw invalid("activities.passages.parts","Đoạn đọc vượt 12.000 ký tự.");
            }
        }
        return value.deepCopy();
    }

    public static List<Long> order(JsonNode activities) {
        var result=new ArrayList<Long>();
        for(var activity:activities)activity.path("questionVersionIds").forEach(q->result.add(q.asLong()));
        return List.copyOf(result);
    }

    public static List<Issue> errors(Lesson lesson,Map<Long,Question> questions,Map<Long,MaterialData> materials) {
        var activities=lesson.activities(); if(activities.isEmpty())return List.of();
        var errors=new ArrayList<Issue>();
        var slots=new HashMap<Long,Slot>();lesson.questions().forEach(s->slots.put(s.questionVersionId(),s));
        var learn=lesson.questions().stream().filter(s->!s.practiceRole().equals("REVIEW_VARIANT")).map(Slot::questionVersionId).collect(java.util.stream.Collectors.toSet());
        var ordered=order(activities);
        need(errors,activities.size()<=7&&ordered.size()>=15&&ordered.size()<=20&&new HashSet<>(ordered).size()==ordered.size()&&new HashSet<>(ordered).equals(learn),"Bài theo hoạt động cần 15–20 câu học, không thiếu/thừa/trùng câu; chỉ chọn các dạng cần thiết.");
        int previous=-1;
        for(var activity:activities) {
            int i=KINDS.indexOf(activity.path("kind").asText());
            need(errors,i>previous,"Mỗi dạng chỉ xuất hiện một lần, theo thứ tự trắc nghiệm, ghép cặp, xếp từ, điền từ, đọc điền từ, đọc hiểu, nghe chọn đáp án; được bỏ qua dạng chưa cần.");
            if(i<0)continue;
            previous=i;
            boolean reading=i==4||i==5,cloze=i==4,listening=i==6;
            var ids=new ArrayList<Long>();activity.path("questionVersionIds").forEach(q->ids.add(q.asLong()));
            need(errors,ids.size()>=2,"Mỗi hoạt động cần ít nhất một câu hướng dẫn và một câu tự làm.");
            var usedMaterials=new HashSet<Long>();Long exampleMaterial=null;var mainMaterials=new HashSet<Long>();
            for(int qIndex=0;qIndex<ids.size();qIndex++) {
                long id=ids.get(qIndex);var q=questions.get(id);var slot=slots.get(id);
                if(q==null||slot==null){need(errors,false,"Hoạt động tham chiếu câu ngoài bài.");continue;}
                need(errors,q.type().equals(TYPES.get(i)),"Dạng đáp án không khớp hoạt động.");
                need(errors,slot.practiceRole().equals(qIndex==0?"GUIDED":"INDEPENDENT"),"Mỗi hoạt động cần một GUIDED đầu tiên, còn lại INDEPENDENT.");
                if(listening) {
                    var material=q.materialVersionId()==null?null:materials.get(q.materialVersionId());
                    need(errors,material!=null&&material.kind().equals("AUDIO"),"Câu nghe phải tham chiếu học liệu AUDIO.");
                    need(errors,q.content().path("options").size()>=3&&q.content().path("options").size()<=6,"Mỗi câu nghe chọn đáp án cần 3–6 lựa chọn.");
                }
                if(reading) {
                    var material=q.materialVersionId()==null?null:materials.get(q.materialVersionId());
                    need(errors,material!=null&&material.kind().equals("READING"),"Câu đọc phải tham chiếu học liệu READING.");
                    if(q.materialVersionId()!=null){usedMaterials.add(q.materialVersionId());if(qIndex==0)exampleMaterial=q.materialVersionId();else mainMaterials.add(q.materialVersionId());}
                    if(cloze)need(errors,q.content().path("parts").findValues("blankId").size()==1,"Mỗi câu đọc điền từ cần đúng một ô trống.");
                    else need(errors,q.content().path("options").size()==4,"Mỗi câu đọc hiểu cần đúng bốn lựa chọn.");
                }
            }
            if(reading)need(errors,exampleMaterial!=null&&mainMaterials.size()==1&&!mainMaterials.contains(exampleMaterial),"Ví dụ GUIDED phải dùng đoạn riêng; các câu tự làm dùng chung đoạn chính.");
            var passageMaterials=new HashSet<Long>();var blanks=new HashSet<Long>();
            for(var passage:activity.path("passages")) {
                long materialId=passage.path("materialVersionId").asLong();var material=materials.get(materialId);
                need(errors,cloze&&usedMaterials.contains(materialId)&&passageMaterials.add(materialId),"Chỉ hoạt động đọc điền từ có cấu trúc đoạn, đúng học liệu và không trùng.");
                var rendered=new StringBuilder();
                for(var part:passage.path("parts")) {
                    if(part.path("kind").asText().equals("TEXT")){rendered.append(part.path("text").asText());continue;}
                    long qId=part.path("questionVersionId").asLong();var q=questions.get(qId);String blank=part.path("blankId").asText();
                    need(errors,ids.contains(qId)&&q!=null&&Objects.equals(q.materialVersionId(),materialId)&&q.content().path("parts").findValuesAsText("blankId").equals(List.of(blank))&&blanks.add(qId),"Ô trống phải ánh xạ đúng một câu/blank ID thuộc đoạn; không trùng hoặc tham chiếu ngoài nhóm.");
                    rendered.append("____");
                }
                need(errors,material!=null&&Objects.equals(material.readingText(),rendered.toString()),"Văn bản các phần phải khớp học liệu đã chốt, mỗi ô thay bằng ____.");
            }
            if(cloze)need(errors,passageMaterials.equals(usedMaterials)&&blanks.equals(new HashSet<>(ids)),"Cần đủ hai đoạn ví dụ/chính và đủ ánh xạ cho mọi câu đọc điền từ.");
        }
        return List.copyOf(errors);
    }
    private static void need(List<Issue> errors,boolean condition,String message) {if(!condition)errors.add(new Issue("activities","INVALID_ACTIVITY_LAYOUT",message));}

    public static JsonNode remap(JsonNode activities,Map<Long,Long> versions) {
        var result=activities.deepCopy();
        for(var activity:result) {
            var ids=(ArrayNode)activity.path("questionVersionIds");
            for(int i=0;i<ids.size();i++)ids.set(i,LongNode.valueOf(versions.getOrDefault(ids.get(i).asLong(),ids.get(i).asLong())));
            for(var passage:activity.path("passages"))for(var part:passage.path("parts"))if(part.has("questionVersionId")) {
                long old=part.path("questionVersionId").asLong();((ObjectNode)part).put("questionVersionId",versions.getOrDefault(old,old));
            }
        }
        return result;
    }
}
