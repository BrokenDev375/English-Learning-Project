package vn.edu.ptit.english;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.ptit.english.dto.AttemptData.*;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.AttemptEnums.*;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.service.PublishedContentReader;
import vn.edu.ptit.english.service.impl.AttemptSnapshotReader;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttemptActivityProjectionTest {
    @Test void singleReviewItemGetsOnlyItsPinnedMaterialAndEditableBlank() {
        var f=ActivityFixtures.fixture();var attempts=mock(AttemptRepository.class);var content=mock(PublishedContentReader.class);var versions=mock(ContentRepository.class);
        var reader=new AttemptSnapshotReader(attempts,content,versions,mock(CatalogRepository.class),mock(AssetRepository.class));
        long qId=f.lesson().activities().get(4).path("questionVersionIds").get(2).asLong();var q=f.questions().get(qId);
        var attempt=new AttemptEntity(1,Mode.REVIEW,null,ActivityFixtures.node("{}"),PlayerState.empty());ReflectionTestUtils.setField(attempt,"id",5L);
        var item=new AttemptQuestionEntity(5,qId,77L,"origin",PracticeRole.INDEPENDENT,0,new Display(List.of(),List.of()),0);ReflectionTestUtils.setField(item,"id",42L);
        when(attempts.items(5)).thenReturn(List.of(item));when(attempts.answers(List.of(42L))).thenReturn(List.of());when(attempts.submission(5)).thenReturn(Optional.empty());
        when(attempts.slotLessonVersions(List.of(77L))).thenReturn(Map.of(77L,9L));when(versions.lessonVersion(9)).thenReturn(Optional.of(new LessonVersionEntity(1,1,f.lesson())));
        when(content.questionVersion(qId)).thenReturn(new PublishedContentReader.PublishedQuestion(1,qId,"origin",q));
        when(content.materialVersion(2)).thenReturn(new PublishedContentReader.PublishedMaterial(1,2,f.materials().get(2L)));
        var snapshot=reader.read(attempt);
        assertThat(snapshot.items()).hasSize(1);assertThat(snapshot.materials()).hasSize(1);assertThat(snapshot.materials().get(0).materialVersionId()).isEqualTo(2);
        assertThat(snapshot.activities()).hasSize(1);var activity=snapshot.activities().get(0);
        assertThat(activity.path("attemptQuestionIds").toString()).isEqualTo("[42]");assertThat(activity.path("passages")).hasSize(1);
        var parts=activity.path("passages").get(0).path("parts");
        assertThat(java.util.stream.StreamSupport.stream(parts.spliterator(),false).filter(p->p.path("kind").asText().equals("BLANK")).count()).isEqualTo(1);
        assertThat(parts.toString()).contains("attemptQuestionId\":42","blankId\":\"word").doesNotContain("questionVersionId","secret-word","answerKey","SECRET_EXPLANATION");
    }
}
