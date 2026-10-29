package vn.edu.ptit.english.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import static vn.edu.ptit.english.dto.MaterialRequests.invalid;

public final class WritingData {
    private WritingData() {}
    public record Criterion(String id,String text,int points) {}
    public record Definition(String title,String prompt,int minWords,int maxWords,List<Criterion> criteria) {
        public Definition { criteria=List.copyOf(criteria); }
    }
    public record Mark(String criterionId,boolean met,String comment) {}
    public record Command(String requestId,long baseRevision,String text,Definition definition,List<Mark> marks,String comment,boolean allowRevision) {}
    public record Version(long id,Definition definition,Instant createdAt) {}
    public record Task(long unitId,long revision,Definition draft,Version published) {}
    public record Work(long id,long versionId,long revision,String state,String text,Long latestSubmissionId,Instant updatedAt) {}
    public record Grade(long id,int earnedPoints,int totalPoints,List<Mark> marks,String comment,boolean allowRevision,Instant gradedAt) {}
    public record Submission(long id,long workId,long versionId,String text,Instant submittedAt,Grade grade) {}
    public record Learner(long unitId,boolean available,boolean unlocked,String blockReason,Version version,Work work,List<Submission> history) {}
    public record Progress(boolean required,boolean submitted,String state) {}
    public record QueueItem(long id,long unitId,String studentName,String title,Instant submittedAt,boolean graded) {}
    public record Grading(long unitId,String studentName,long workRevision,boolean canGrade,Version version,Submission submission) {}

    public static Command parse(JsonNode n,String operation) {
        Set<String> fields=switch(operation){
            case "start" -> Set.of("requestId");
            case "definition" -> Set.of("requestId","baseRevision","definition");
            case "draft","submit" -> Set.of("requestId","baseRevision","text");
            case "grade" -> Set.of("requestId","baseRevision","marks","comment","allowRevision");
            default -> Set.of("requestId","baseRevision");
        };
        exact(n,fields,"body");String key=text(n,"requestId",36,true);
        if(!key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw invalid("requestId","Mã yêu cầu không hợp lệ.");
        long revision=operation.equals("start")?0:number(n,"baseRevision",0,9007199254740991L);
        Definition definition=operation.equals("definition")?definition(n.get("definition")):null;
        String body=Set.of("draft","submit").contains(operation)?text(n,"text",30000,operation.equals("submit")):null;
        var marks=new ArrayList<Mark>();String comment="";boolean allow=false;
        if(operation.equals("grade")){
            JsonNode rows=n.get("marks");if(rows==null||!rows.isArray()||rows.size()<1||rows.size()>20)throw invalid("marks","Cần đánh giá đầy đủ tiêu chí.");
            var ids=new HashSet<String>();
            for(var row:rows){exact(row,Set.of("criterionId","met","comment"),"marks");String id=text(row,"criterionId",40,true);if(!ids.add(id))throw invalid("marks","Tiêu chí bị lặp.");marks.add(new Mark(id,bool(row,"met"),text(row,"comment",1000,false)));}
            comment=text(n,"comment",4000,false);allow=bool(n,"allowRevision");
            if(allow&&comment.isBlank())throw invalid("comment","Hãy hướng dẫn học sinh sửa bài.");
        }
        return new Command(key,revision,body,definition,List.copyOf(marks),comment,allow);
    }
    public static Definition definition(JsonNode n){
        exact(n,Set.of("title","prompt","minWords","maxWords","criteria"),"definition");
        int min=(int)number(n,"minWords",0,5000),max=(int)number(n,"maxWords",1,5000);if(min>max)throw invalid("maxWords","Số từ tối đa phải lớn hơn hoặc bằng tối thiểu.");
        var rows=n.get("criteria");if(rows==null||!rows.isArray()||rows.isEmpty()||rows.size()>20)throw invalid("criteria","Cần 1–20 tiêu chí.");
        var criteria=new ArrayList<Criterion>();var ids=new HashSet<String>();int total=0;
        for(var row:rows){exact(row,Set.of("id","text","points"),"criteria");String id=text(row,"id",40,true);if(!id.matches("[a-zA-Z0-9_-]+")||!ids.add(id))throw invalid("criteria","Mã tiêu chí phải duy nhất và hợp lệ.");int points=(int)number(row,"points",1,10);total+=points;criteria.add(new Criterion(id,text(row,"text",1000,true),points));}
        if(total!=10)throw invalid("criteria","Tổng điểm các tiêu chí phải bằng 10.");
        return new Definition(text(n,"title",160,true),text(n,"prompt",12000,true),min,max,criteria);
    }
    public static int score(Definition definition,List<Mark> marks){
        var expected=new HashSet<String>();definition.criteria().forEach(c->expected.add(c.id()));var actual=new HashSet<String>();marks.forEach(m->actual.add(m.criterionId()));
        if(marks.size()!=expected.size()||!actual.equals(expected))throw invalid("marks","Phải đánh giá đúng và đủ tiêu chí của bản đề đã chốt.");
        return definition.criteria().stream().filter(c->marks.stream().anyMatch(m->m.criterionId().equals(c.id())&&m.met())).mapToInt(Criterion::points).sum();
    }
    private static String text(JsonNode n,String key,int max,boolean required){var v=n.get(key);if(v==null||!v.isTextual()||v.asText().length()>max||(required&&v.asText().isBlank())||v.asText().indexOf('\0')>=0)throw invalid(key,"Nội dung thiếu hoặc vượt độ dài cho phép.");return v.asText();}
    private static long number(JsonNode n,String key,long min,long max){var v=n.get(key);if(v==null||!v.isIntegralNumber()||!v.canConvertToLong()||v.asLong()<min||v.asLong()>max)throw invalid(key,"Cần số nguyên hợp lệ.");return v.asLong();}
    private static boolean bool(JsonNode n,String key){var v=n.get(key);if(v==null||!v.isBoolean())throw invalid(key,"Cần giá trị đúng/sai.");return v.asBoolean();}
    private static void exact(JsonNode n,Set<String> keys,String path){if(n==null||!n.isObject())throw invalid(path,"Cần đối tượng JSON.");var actual=new HashSet<String>();n.fieldNames().forEachRemaining(actual::add);if(!actual.equals(keys))throw invalid(path,"Các trường phải đúng và đủ theo hợp đồng.");}
}
