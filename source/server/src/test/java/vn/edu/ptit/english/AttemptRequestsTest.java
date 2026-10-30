package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.dto.AttemptData.*;
import vn.edu.ptit.english.exception.ApiException;
import static org.assertj.core.api.Assertions.*;

class AttemptRequestsTest {
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    final String key="00000000-0000-4000-8000-000000000001";
    JsonNode read(String value)throws IOException{return json.readTree(value);}
    JsonNode fixtures()throws IOException {try(var in=getClass().getResourceAsStream("/contracts/m03-fixtures.json")){return json.readTree(in);}}
    @Test void requestAndResponseFixturesRoundTripWithoutExtraFields()throws Exception {
        for(var fixture:fixtures().path("valid")) {
            var n=fixture.path("body"); String schema=fixture.path("schema").asText().replace("#/components/schemas/","");
            Object dto=switch(schema) {
                case "AttemptCreate" -> AttemptRequests.create(n).payload();
                case "AttemptMutation" -> AttemptRequests.mutation(n);
                case "Hint" -> AttemptRequests.hint(n);
                case "Check" -> AttemptRequests.check(n);
                case "AttemptSnapshot" -> json.treeToValue(n,AttemptViews.Snapshot.class);
                case "Feedback" -> json.treeToValue(n,AttemptViews.Feedback.class);
                case "Result" -> json.treeToValue(n,AttemptViews.Result.class);
                case "DraftAck" -> json.treeToValue(n,AttemptViews.DraftAck.class);
                case "LessonProgress" -> json.treeToValue(n,AttemptViews.LessonProgress.class);
                case "Answer" -> AttemptRequests.answer(n,false,"response");
                case "PublicQuestion" -> json.treeToValue(n,AttemptViews.PublicQuestion.class);
                default -> throw new AssertionError("Untested fixture schema: "+schema);
            };
            JSONAssert.assertEquals(fixture.path("name").asText(),n.toString(),json.writeValueAsString(dto),true);
        }
    }
    @Test void rejectsUnknownFieldsMissingRequiredNullAndNumericCoercion()throws Exception {
        ObjectNode base=(ObjectNode)read("{\"requestId\":\""+key+"\",\"mode\":\"LEARN\",\"lessonId\":1}");
        for(String field:new String[]{"userId","baseRevision","answerKey"}) {
            var bad=base.deepCopy().put(field,1); assertThatThrownBy(()->AttemptRequests.create(bad)).isInstanceOf(ApiException.class);
        }
        var floating=base.deepCopy().put("lessonId",1.0); assertThatThrownBy(()->AttemptRequests.create(floating)).isInstanceOf(ApiException.class);
        var tooLarge=base.deepCopy().put("lessonId",9007199254740992L); assertThatThrownBy(()->AttemptRequests.create(tooLarge)).isInstanceOf(ApiException.class);
        var upper=base.deepCopy().put("requestId","AAAAAAAA-0000-4000-8000-000000000001"); assertThatThrownBy(()->AttemptRequests.create(upper)).isInstanceOf(ApiException.class);
        var review=(ObjectNode)read("{\"requestId\":\""+key+"\",\"mode\":\"REVIEW\",\"reviewKind\":\"DUE\",\"count\":1}");
        assertThatThrownBy(()->AttemptRequests.create(review)).isInstanceOf(ApiException.class);
        review.putNull("topicId"); assertThat(AttemptRequests.create(review).topicId()).isNull();
        var practice=(ObjectNode)read("{\"requestId\":\""+key+"\",\"mode\":\"PRACTICE\",\"topicId\":1,\"difficulty\":\"EASY\",\"count\":11}");
        assertThatThrownBy(()->AttemptRequests.create(practice)).isInstanceOf(ApiException.class);
    }
    @Test void draftAllowsPartialAndNullButRejectsDuplicateItemsAndBadPlayerShape()throws Exception {
        var n=(ObjectNode)read("{\"requestId\":\""+key+"\",\"baseRevision\":0,\"answers\":[{\"attemptQuestionId\":1,\"response\":null}],\"playerState\":{\"currentAttemptQuestionId\":null,\"audioPositions\":[]}}");
        assertThat(AttemptRequests.mutation(n).answers().get(0).response()).isNull();
        var missing=n.deepCopy(); missing.remove("baseRevision"); assertThatThrownBy(()->AttemptRequests.mutation(missing)).isInstanceOf(ApiException.class);
        var duplicated=n.deepCopy(); ((com.fasterxml.jackson.databind.node.ArrayNode)duplicated.get("answers")).add(n.path("answers").get(0));
        assertThatThrownBy(()->AttemptRequests.mutation(duplicated)).isInstanceOf(ApiException.class);
        var bad=n.deepCopy(); ((ObjectNode)bad.get("playerState")).putArray("audioPositions").addObject().put("materialVersionId",1).put("seconds",-1);
        assertThatThrownBy(()->AttemptRequests.mutation(bad)).isInstanceOf(ApiException.class);
    }
    @Test void acceptsThreeQuestionReviewChoicesAndRetainsLegacyRequests() throws Exception {
        var body = (ObjectNode) read("{\"requestId\":\"" + key + "\",\"mode\":\"REVIEW\",\"reviewKind\":\"ALL\",\"topicId\":null,\"count\":10}");
        for (String kind : new String[]{"ALL", "PRIORITY", "WRONG", "DUE", "MISTAKES"}) {
            body.put("reviewKind", kind);
            assertThat(AttemptRequests.create(body).reviewKind()).isEqualTo(kind);
        }
        body.put("reviewKind", "OTHER");
        assertThatThrownBy(() -> AttemptRequests.create(body)).isInstanceOf(ApiException.class);
    }
    @Test void invalidAnswersFailWithFieldPathAndSpecificCode()throws Exception {
        for(String payload:new String[]{
            "{\"type\":\"MULTIPLE_CHOICE\",\"optionIds\":[]}",
            "{\"type\":\"SINGLE_CHOICE\",\"optionId\":\"\"}",
            "{\"type\":\"FILL_BLANK\",\"values\":[{\"blankId\":\"x\",\"value\":\"a\"},{\"blankId\":\"x\",\"value\":\"b\"}]}",
            "{\"type\":\"MATCHING\",\"pairs\":[{\"leftId\":\"l1\",\"rightId\":\"r1\"},{\"leftId\":\"l2\",\"rightId\":\"r1\"}]}"}) {
            JsonNode n=read(payload);
            assertThatThrownBy(()->AttemptRequests.answer(n,true,"answers[0].response"))
                .isInstanceOfSatisfying(ApiException.class,e->{assertThat(e.code()).isEqualTo("INVALID_ANSWER");assertThat(e.fieldErrors().keySet()).allMatch(p->p.startsWith("answers[0].response"));});
        }
    }
    @Test void polymorphicAnswerJsonIsExactlyTheContract()throws Exception {
        for(String input:new String[]{"{\"type\":\"SINGLE_CHOICE\",\"optionId\":null}","{\"type\":\"FILL_BLANK\",\"values\":[]}","{\"type\":\"MATCHING\",\"pairs\":[]}"}) {
            var dto=AttemptRequests.answer(read(input),false,"response");
            JSONAssert.assertEquals(input,json.writeValueAsString(dto),true);
            assertThat(json.readValue(input,Answer.class)).isEqualTo(dto);
        }
    }
}
