package vn.edu.ptit.english.service;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import vn.edu.ptit.english.dto.AuthoringData.*;
import vn.edu.ptit.english.dto.MaterialData;
/** Server-only snapshots for M03. These contain keys/transcripts and must never be serialized by learner controllers. */
public interface PublishedContentReader {
    record PublishedLesson(long lessonId,long versionId,long unitId,String title,JsonNode instructions,List<Long> objectiveIds,List<Slot> questions,JsonNode activities) {}
    record PublishedQuestion(long questionId,long versionId,String originKey,Question data) {}
    record PublishedMaterial(long materialId,long versionId,MaterialData data) {}
    PublishedLesson activeLesson(long lessonId);
    List<PublishedLesson> activeLessons(Long topicId);
    PublishedLesson lessonVersion(long versionId);
    PublishedQuestion questionVersion(long versionId);
    PublishedMaterial materialVersion(long versionId);
}
