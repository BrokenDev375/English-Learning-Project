package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.dto.AttemptData.*;
import vn.edu.ptit.english.dto.AuthoringData.Question;
import vn.edu.ptit.english.enums.AttemptEnums.Outcome;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.service.impl.*;
import static org.assertj.core.api.Assertions.*;

class OrderingQuestionTest {
    final ObjectMapper json=new ObjectMapper();
    final AnswerGraderImpl grader=new AnswerGraderImpl(new AnswerValidator());
    Question question()throws Exception {
        return new Question("ORDERING",1,1,"EASY","Make a sentence",json.readTree("""
            {"tokens":[{"id":"a","text":"I"},{"id":"b","text":"am"},{"id":"c","text":"here"}]}
            """),json.readTree("{\"acceptedOrders\":[[\"a\",\"b\",\"c\"],[\"c\",\"a\",\"b\"]]}"),"Explanation","Hint",null,new MaterialData.Source("Test",null,null));
    }
    @Test void acceptsDeclaredOrdersAndSeparatesPartialWrongAndUnanswered()throws Exception {
        var q=question();
        assertThat(grader.grade(q,new OrderingAnswer(List.of("a","b","c"))).outcome()).isEqualTo(Outcome.CORRECT);
        assertThat(grader.grade(q,new OrderingAnswer(List.of("c","a","b"))).outcome()).isEqualTo(Outcome.CORRECT);
        var wrong=grader.grade(q,new OrderingAnswer(List.of("a","c","b")));assertThat(wrong.outcome()).isEqualTo(Outcome.INCORRECT);assertThat(wrong.responseComplete()).isTrue();
        var partial=grader.grade(q,new OrderingAnswer(List.of("a","b")));assertThat(partial.outcome()).isEqualTo(Outcome.INCORRECT);assertThat(partial.responseComplete()).isFalse();
        assertThat(grader.grade(q,new OrderingAnswer(List.of())).outcome()).isEqualTo(Outcome.UNANSWERED);
        assertThat(grader.grade(q,null).outcome()).isEqualTo(Outcome.UNANSWERED);
    }
    @Test void repeatedWordsAreInterchangeableButForeignOrDuplicateIdsAreRejected()throws Exception {
        var original=question();
        var q=new Question("ORDERING",1,1,"EASY","Make a sentence",json.readTree("{\"tokens\":[{\"id\":\"a\",\"text\":\"very\"},{\"id\":\"b\",\"text\":\"very\"},{\"id\":\"c\",\"text\":\"good\"}]}"),json.readTree("{\"acceptedOrders\":[[\"a\",\"b\",\"c\"]]}"),original.explanation(),original.hint(),null,original.source());
        assertThat(grader.grade(q,new OrderingAnswer(List.of("b","a","c"))).outcome()).isEqualTo(Outcome.CORRECT);
        for(var ids:List.of(List.of("a","a","c"),List.of("a","foreign","c")))assertThatThrownBy(()->grader.grade(q,new OrderingAnswer(ids))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->grader.grade(q,new MatchingAnswer(List.of()))).isInstanceOf(ApiException.class);
    }
    @Test void publicationRequiresCompleteUniquePermutations()throws Exception {
        var q=question();assertThat(new QuestionValidator().errors(q)).isEmpty();
        for(String key:List.of("{\"acceptedOrders\":[]}","{\"acceptedOrders\":[[\"a\",\"b\"]]}","{\"acceptedOrders\":[[\"a\",\"a\",\"c\"]]}")) {
            var invalid=new Question(q.type(),q.topicId(),q.objectiveId(),q.difficulty(),q.prompt(),q.content(),json.readTree(key),q.explanation(),q.hint(),q.materialVersionId(),q.source());
            assertThat(new QuestionValidator().errors(invalid)).isNotEmpty();
        }
    }
    @Test void authoringAndAnswerParsersStayStrictAndJacksonCanRecoverStoredResponses()throws Exception {
        var input=json.valueToTree(question().fields());((com.fasterxml.jackson.databind.node.ObjectNode)input).put("requestId",UUID.randomUUID().toString());
        assertThat(AuthoringRequests.question(input,false).data().type()).isEqualTo("ORDERING");
        var response=json.readTree("{\"type\":\"ORDERING\",\"tokenIds\":[\"a\",\"b\"]}");
        assertThat(AttemptRequests.answer(response,false,"response")).isEqualTo(new OrderingAnswer(List.of("a","b")));
        assertThat(json.readValue(json.writeValueAsString(new OrderingAnswer(List.of("b","a"))),Answer.class)).isEqualTo(new OrderingAnswer(List.of("b","a")));
        for(String bad:List.of("{\"type\":\"ORDERING\",\"tokenIds\":[\"a\",\"a\"]}","{\"type\":\"ORDERING\",\"tokenIds\":[1]}","{\"type\":\"ORDERING\",\"tokenIds\":[],\"extra\":true}"))assertThatThrownBy(()->AttemptRequests.answer(json.readTree(bad),false,"response")).isInstanceOf(ApiException.class);
    }
}
