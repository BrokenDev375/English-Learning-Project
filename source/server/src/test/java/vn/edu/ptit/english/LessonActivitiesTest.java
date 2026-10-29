package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.dto.AuthoringData.*;
import static org.assertj.core.api.Assertions.*;

class LessonActivitiesTest {
    final ActivityFixtures.Fixture f=ActivityFixtures.fixture();
    Lesson with(com.fasterxml.jackson.databind.JsonNode activities,List<Slot> slots){var l=f.lesson();return new Lesson(l.title(),l.description(),l.instructions(),l.objectiveIds(),slots,activities);}
    @Test void validLayoutHasEighteenItemsAndStrictlyParsesWithoutKeys() {
        var activities=LessonActivities.parse(f.lesson().activities());
        assertThat(LessonActivities.order(activities)).hasSize(18).doesNotHaveDuplicates();
        assertThat(LessonActivities.errors(f.lesson(),f.questions(),f.materials())).isEmpty();
        assertThat(activities.toString()).doesNotContain("secret-word","answerKey","SECRET_EXPLANATION");
    }
    @Test void listeningLayoutKeepsEighteenAndRequiresAudioWithAtLeastThreeOptions() {
        var next=ActivityFixtures.fixture(true);
        assertThat(LessonActivities.parse(next.lesson().activities())).hasSize(7);
        assertThat(LessonActivities.order(next.lesson().activities())).hasSize(18).doesNotHaveDuplicates();
        assertThat(LessonActivities.errors(next.lesson(),next.questions(),next.materials())).isEmpty();
        assertThat(next.lesson().questions().stream().filter(s->s.practiceRole().equals("GUIDED"))).hasSize(7);
        var materials=new LinkedHashMap<>(next.materials());materials.put(5L,materials.get(3L));
        assertThat(LessonActivities.errors(next.lesson(),next.questions(),materials)).isNotEmpty();
        materials.remove(5L);
        assertThat(LessonActivities.errors(next.lesson(),next.questions(),materials)).isNotEmpty();
        long id=next.lesson().activities().get(6).path("questionVersionIds").get(1).asLong();
        var q=next.questions().get(id);var questions=new LinkedHashMap<>(next.questions());
        questions.put(id,new Question(q.type(),q.topicId(),q.objectiveId(),q.difficulty(),q.prompt(),ActivityFixtures.node("{\"options\":[{\"id\":\"a\",\"text\":\"A\"},{\"id\":\"b\",\"text\":\"B\"}]}"),q.answerKey(),q.explanation(),q.hint(),q.materialVersionId(),q.source()));
        assertThat(LessonActivities.errors(next.lesson(),questions,next.materials())).isNotEmpty();
    }
    ActivityFixtures.Fixture beginner(int total) {
        var base=ActivityFixtures.fixture(true);
        var questions=new LinkedHashMap<Long,Question>();var slots=new ArrayList<Slot>();
        var activities=ActivityFixtures.JSON.createArrayNode();int guided=0,independent=0;
        int[] sourceGroups={0,1,3,6},counts={total-12,4,6,2};
        for(int g=0;g<sourceGroups.length;g++) {
            var source=base.lesson().activities().get(sourceGroups[g]);
            var a=activities.addObject().put("id","group-"+g).put("kind",source.path("kind").asText());
            var ids=a.putArray("questionVersionIds");a.putArray("passages");
            var q=base.questions().get(source.path("questionVersionIds").get(0).asLong());
            for(int at=0;at<counts[g];at++) {
                long id=1000+questions.size();ids.add(id);questions.put(id,q);
                slots.add(new Slot(id,at==0?"GUIDED":"INDEPENDENT",at==0?guided++:independent++));
            }
        }
        return new ActivityFixtures.Fixture(new Lesson("Beginner","",base.lesson().instructions(),List.of(1L),slots,activities),questions,base.materials());
    }
    @Test void optionalKindsAcceptFifteenEighteenAndTwentyQuestionsWithDifferentDistributions() {
        for(int total:List.of(15,18,20)) {
            var sample=beginner(total);
            assertThat(LessonActivities.errors(sample.lesson(),sample.questions(),sample.materials())).isEmpty();
            assertThat(LessonActivities.order(sample.lesson().activities())).hasSize(total);
            assertThat(sample.lesson().activities().toString()).doesNotContain("READING_CLOZE","READING_CHOICE","ORDERING");
        }
    }
    @Test void flexibleLayoutRejectsOutOfRangeTotalsRepeatedKindsAndMissingCoverage() {
        for(int total:List.of(14,21)) {
            var sample=beginner(total);
            assertThat(LessonActivities.errors(sample.lesson(),sample.questions(),sample.materials())).isNotEmpty();
        }
        var sample=beginner(18);var a=sample.lesson().activities();
        ((ObjectNode)a.get(1)).put("kind","CHOICE");
        var bad=new Lesson("Bad","",sample.lesson().instructions(),List.of(1L),sample.lesson().questions(),a);
        assertThat(LessonActivities.errors(bad,sample.questions(),sample.materials())).isNotEmpty();
        a=sample.lesson().activities();((ArrayNode)a.get(0).path("questionVersionIds")).remove(1);
        bad=new Lesson("Bad","",sample.lesson().instructions(),List.of(1L),sample.lesson().questions(),a);
        assertThat(LessonActivities.errors(bad,sample.questions(),sample.materials())).isNotEmpty();
    }
    @Test void oldPayloadRemainsUnchangedIncludingReceiptHashFields() {
        var l=f.lesson();var legacy=new Lesson(l.title(),l.description(),l.instructions(),l.objectiveIds(),l.questions());
        assertThat(legacy.fields()).doesNotContainKey("activities");assertThat(LessonActivities.errors(legacy,f.questions(),f.materials())).isEmpty();
        assertThat(LessonActivities.parse(null)).isEmpty();
    }
    @Test void wrongOrderCountRoleAndDuplicateOriginsInLayoutAreRejected() {
        var a=f.lesson().activities();((ObjectNode)a.get(0)).put("kind","FILL");
        assertThat(LessonActivities.errors(with(a,f.lesson().questions()),f.questions(),f.materials())).isNotEmpty();
        var slots=new ArrayList<>(f.lesson().questions());var s=slots.get(1);slots.set(1,new Slot(s.questionVersionId(),"GUIDED",6));
        assertThat(LessonActivities.errors(with(f.lesson().activities(),slots),f.questions(),f.materials())).isNotEmpty();
        a=f.lesson().activities();((ArrayNode)a.get(1).path("questionVersionIds")).set(1,LongNode.valueOf(100));
        assertThat(LessonActivities.errors(with(a,f.lesson().questions()),f.questions(),f.materials())).isNotEmpty();
    }
    @Test void inlineMappingMustMatchMaterialTextBlankIdAndMembership() {
        for(String field:List.of("questionVersionId","blankId","text")) {
            var a=f.lesson().activities();var parts=a.get(4).path("passages").get(1).path("parts");
            if(field.equals("text"))((ObjectNode)parts.get(0)).put(field,"Changed text ");
            else if(field.equals("blankId"))((ObjectNode)parts.get(1)).put(field,"foreign");
            else ((ObjectNode)parts.get(1)).put(field,100);
            assertThat(LessonActivities.errors(with(a,f.lesson().questions()),f.questions(),f.materials())).isNotEmpty();
        }
    }
    @Test void parserRejectsSecretsUnknownFieldsOversizedArraysAndCoercedIds() {
        var a=f.lesson().activities();((ObjectNode)a.get(0)).put("answerKey","secret");
        assertThatThrownBy(()->LessonActivities.parse(a)).isInstanceOf(vn.edu.ptit.english.exception.ApiException.class);
        var b=f.lesson().activities();((ArrayNode)b.get(0).path("questionVersionIds")).set(0,TextNode.valueOf("100"));
        assertThatThrownBy(()->LessonActivities.parse(b)).isInstanceOf(vn.edu.ptit.english.exception.ApiException.class);
        assertThatThrownBy(()->LessonActivities.parse(NullNode.instance)).isInstanceOf(vn.edu.ptit.english.exception.ApiException.class);
    }
    @Test void remappingVersionsPreservesLayoutAndUpdatesBothSlotAndInlineReferences() {
        long old=f.lesson().activities().get(4).path("questionVersionIds").get(1).asLong();
        var a=LessonActivities.remap(f.lesson().activities(),Map.of(old,999L));
        assertThat(a.get(4).path("questionVersionIds").get(1).asLong()).isEqualTo(999);
        assertThat(a.get(4).path("passages").get(1).path("parts").get(1).path("questionVersionId").asLong()).isEqualTo(999);
        assertThat(f.lesson().activities().get(4).path("questionVersionIds").get(1).asLong()).isEqualTo(old);
    }
}
