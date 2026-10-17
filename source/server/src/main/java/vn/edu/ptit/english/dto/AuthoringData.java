package vn.edu.ptit.english.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

public final class AuthoringData {
    private AuthoringData() {}
    public record Question(String type,long topicId,long objectiveId,String difficulty,String prompt,
        JsonNode content,JsonNode answerKey,String explanation,String hint,Long materialVersionId,MaterialData.Source source) {
        public Map<String,Object> fields(){var m=new LinkedHashMap<String,Object>();m.put("type",type);m.put("topicId",topicId);m.put("objectiveId",objectiveId);m.put("difficulty",difficulty);m.put("prompt",prompt);m.put("content",content);m.put("answerKey",answerKey);m.put("explanation",explanation);m.put("hint",hint);m.put("materialVersionId",materialVersionId);m.put("source",source);return m;}
    }
    public record Slot(long questionVersionId,String practiceRole,int position) {}
    public record Lesson(String title,String description,JsonNode instructions,List<Long> objectiveIds,List<Slot> questions) {
        public Map<String,Object> fields(){var m=new LinkedHashMap<String,Object>();m.put("title",title);m.put("description",description);m.put("instructions",instructions);m.put("objectiveIds",objectiveIds);m.put("questions",questions);return m;}
    }
    public record Issue(String path,String code,String message) {}
    public record Preview(long resourceId,long draftVersionId,long baseRevision,String previewToken,List<Issue> errors,List<String> warnings,Object data) {}
}
