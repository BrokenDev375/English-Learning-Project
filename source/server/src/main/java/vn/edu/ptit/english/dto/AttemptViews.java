package vn.edu.ptit.english.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import vn.edu.ptit.english.dto.AttemptData.*;
import vn.edu.ptit.english.enums.AttemptEnums.*;

/** Learner response types. Mapping from PublishedContentReader must explicitly omit secrets. */
public final class AttemptViews {
    private AttemptViews() {}
    public record PublicQuestion(String type,String prompt,JsonNode content) {
        public PublicQuestion { content=content.deepCopy(); }
        @Override public JsonNode content(){return content.deepCopy();}
    }
    public record PublicAsset(long id,String mimeType,long sizeBytes,String url) {}
    public record PublicMaterial(long materialVersionId,String kind,String title,String readingText,
        PublicAsset asset,String altText,String caption,Double startSeconds,Double endSeconds,MaterialData.Source source) {}
    public record QuestionCounts(int guided,int independent,int reviewVariant) {}
    public record LessonSummary(long id,long unitId,long lessonVersionId,String title,String description,int sortOrder,
        List<Long> objectiveIds,QuestionCounts questionCounts) {}
    public record LessonPublic(LessonSummary lesson,JsonNode instructions,List<CatalogViews.Objective> objectives) {}
    public record Item(long attemptQuestionId,long questionVersionId,String originKey,long objectiveId,String difficulty,
        PracticeRole practiceRole,int position,boolean wasSeenBefore,boolean hintAvailable,boolean hintOpened,
        boolean feedbackOpened,PublicQuestion question,Long materialVersionId,Answer response) {}
    public record Snapshot(long attemptId,Mode mode,State state,Long lessonVersionId,long revision,Instant createdAt,
        Instant updatedAt,Instant submittedAt,List<Item> items,List<PublicMaterial> materials,PlayerState playerState,
        Long submissionId,LessonPublic lesson) {}
    public record Summary(long attemptId,Mode mode,State state,Long lessonVersionId,long revision,int totalQuestions,
        int answeredCount,Instant createdAt,Instant updatedAt,Instant submittedAt,Long submissionId) {}
    public record DraftAck(String requestId,long attemptId,long revision,State state,Instant savedAt,int answeredCount) {}
    public record HintResult(String requestId,long attemptId,long revision,long attemptQuestionId,String hint) {}
    public record Feedback(String requestId,long attemptId,long revision,long attemptQuestionId,long trialId,int trialNo,
        TrialKind kind,Outcome outcome,boolean responseComplete,boolean supportUsed,JsonNode answerKey,String explanation) {}
    public record ResultItem(long attemptQuestionId,long questionVersionId,String originKey,long objectiveId,
        PracticeRole practiceRole,boolean wasSeenBefore,Answer firstResponse,Outcome firstOutcome,
        boolean firstResponseComplete,boolean firstSupportUsed,Answer finalResponse,Outcome finalOutcome,
        boolean finalResponseComplete,JsonNode answerKey,String explanation,Long materialVersionId) {
        public ResultItem { answerKey=answerKey.deepCopy(); }
        @Override public JsonNode answerKey(){return answerKey.deepCopy();}
    }
    public record MaterialFeedback(long materialVersionId,String transcript) {}
    public record Result(int factsSchemaVersion,long submissionId,long attemptId,Mode mode,Long lessonVersionId,
        Instant submittedAt,long submittedRevision,int totalQuestions,int answeredCount,int unansweredCount,int correctCount,
        BigDecimal scorePercent,int independentFreshAnsweredCount,int independentFreshCorrectCount,boolean lessonCompleted,
        List<ResultItem> items,List<MaterialFeedback> materialFeedback) {
        public Result { items=List.copyOf(items); materialFeedback=List.copyOf(materialFeedback); }
        public Result withSubmissionId(long id) {
            return new Result(factsSchemaVersion,id,attemptId,mode,lessonVersionId,submittedAt,submittedRevision,totalQuestions,
                answeredCount,unansweredCount,correctCount,scorePercent,independentFreshAnsweredCount,
                independentFreshCorrectCount,lessonCompleted,items,materialFeedback);
        }
    }
    public record LessonProgress(long lessonId,ProgressState state,Long lastAttemptId,Instant completedAt,
        Long lastCompletedVersionId,Long currentPublishedVersionId,boolean updateAvailable,BigDecimal lastScorePercent) {}
}
