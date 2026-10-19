package vn.edu.ptit.english.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import static vn.edu.ptit.english.dto.MaterialRequests.invalid;

public final class AudioUpdateData {
    private AudioUpdateData() {}
    public record QuestionRow(long id,String title,long versionId,String reason) {}
    public record LessonRow(long id,String title,long versionId,List<Long> requiredQuestionIds,String reason) {}
    public record Preview(long materialId,long targetVersionId,int targetVersionNo,String previewToken,
        List<QuestionRow> questions,List<LessonRow> lessons,int foreignQuestionCount,int foreignLessonCount) {}
    public record Request(String requestId,long targetVersionId,String previewToken,
        List<Long> questionIds,List<Long> lessonIds,boolean contentConfirmed) {}
    public record Updated(long id,long versionId) {}
    public record Applied(long materialId,long targetVersionId,List<Updated> questions,List<Updated> lessons) {}
    public static Request request(JsonNode input) {
        AuthoringRequests.strict(input,"requestId","targetVersionId","previewToken","questionIds","lessonIds","contentConfirmed");
        String token=AuthoringRequests.text(input,"previewToken",64,false,true);
        if(!token.matches("[a-f0-9]{64}"))throw invalid("previewToken","Cần xem danh sách cập nhật trước khi áp dụng.");
        var questions=AuthoringRequests.ids(input,"questionIds",100);
        var lessons=AuthoringRequests.ids(input,"lessonIds",100);
        if(questions.isEmpty()&&lessons.isEmpty())throw invalid("questionIds","Chọn ít nhất một câu hỏi hoặc bài học.");
        if(!input.path("contentConfirmed").isBoolean()||!input.path("contentConfirmed").asBoolean())
            throw invalid("contentConfirmed","Xác nhận đã kiểm tra nội dung, đáp án và transcript phù hợp với audio mới.");
        return new Request(AuthoringRequests.key(input),AuthoringRequests.number(input,"targetVersionId",1,false),
            token,questions,lessons,true);
    }
}
