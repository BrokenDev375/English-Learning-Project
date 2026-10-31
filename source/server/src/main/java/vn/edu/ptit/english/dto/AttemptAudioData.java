package vn.edu.ptit.english.dto;
import com.fasterxml.jackson.databind.JsonNode;
import static vn.edu.ptit.english.dto.MaterialRequests.invalid;
public final class AttemptAudioData {
    private AttemptAudioData() {}
    public record Start(String requestId) {}
    public record Allowance(long attemptId,long materialVersionId,int playsUsed,int maxPlays,int remaining) {}
    public static Start start(JsonNode body){
        if(body==null || !body.isObject() || body.size()!=1 || !body.path("requestId").isTextual()
            || !body.path("requestId").asText().matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))throw invalid("requestId","Cần mã yêu cầu UUID hợp lệ.");
        return new Start(body.get("requestId").asText());
    }
}
