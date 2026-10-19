package vn.edu.ptit.english;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.dto.AttemptData.*;
import vn.edu.ptit.english.dto.AuthoringData.Question;
import vn.edu.ptit.english.enums.AttemptEnums.Outcome;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.service.impl.*;
import static org.assertj.core.api.Assertions.*;

class AnswerGraderTest {
    final ObjectMapper json=new ObjectMapper();
    final AnswerGraderImpl grader=new AnswerGraderImpl(new AnswerValidator());
    Question question(String type,String content,String key)throws Exception {
        return new Question(type,1,1,"EASY","Question",json.readTree(content),json.readTree(key),"Explanation","Hint",null,new MaterialData.Source("Test",null,null));
    }
    Question single()throws Exception {return question("SINGLE_CHOICE","{\"options\":[{\"id\":\"a\",\"text\":\"A\"},{\"id\":\"b\",\"text\":\"B\"}]}","{\"correctOptionId\":\"b\"}");}
    Question fill(boolean whitespace,boolean sensitive)throws Exception {
        return question("FILL_BLANK","{\"parts\":[{\"kind\":\"BLANK\",\"blankId\":\"x\"},{\"kind\":\"TEXT\",\"text\":\" and \"},{\"kind\":\"BLANK\",\"blankId\":\"y\"}]}",
            "{\"blanks\":[{\"blankId\":\"x\",\"acceptedAnswers\":[\"Café\",\"coffee\"]},{\"blankId\":\"y\",\"acceptedAnswers\":[\"hello world\"]}],\"caseSensitive\":"+sensitive+",\"normalizeWhitespace\":"+whitespace+"}");
    }
    Question matching()throws Exception {return question("MATCHING","{\"leftItems\":[{\"id\":\"l1\",\"text\":\"One\"},{\"id\":\"l2\",\"text\":\"Two\"}],\"rightItems\":[{\"id\":\"r2\",\"text\":\"Two\"},{\"id\":\"r1\",\"text\":\"One\"}]}","{\"pairs\":[{\"leftId\":\"l1\",\"rightId\":\"r1\"},{\"leftId\":\"l2\",\"rightId\":\"r2\"}]}");}
    void check(Question q,Answer a,Outcome outcome,boolean complete){var g=grader.grade(q,a);assertThat(g.outcome()).isEqualTo(outcome);assertThat(g.responseComplete()).isEqualTo(complete);}
    @Test void singleChoiceUsesStableIdsAndNullIsUnanswered()throws Exception {
        check(single(),new SingleAnswer("b"),Outcome.CORRECT,true);
        check(single(),new SingleAnswer("a"),Outcome.INCORRECT,true);
        check(single(),new SingleAnswer(null),Outcome.UNANSWERED,false);
        check(single(),null,Outcome.UNANSWERED,false);
    }
    @ParameterizedTest @CsvSource({"Café,hello world,true,false,CORRECT","coffee,hello world,true,false,CORRECT",
        "CAFÉ,hello world,true,false,CORRECT","CAFÉ,hello world,true,true,INCORRECT",
        "Cafe,hello world,true,false,INCORRECT","Café!,hello world,true,false,INCORRECT",
        "Café,hello  world,false,false,INCORRECT","Café,hello  world,true,false,CORRECT"})
    void fillRespectsNormalizationFlags(String a,String b,boolean whitespace,boolean sensitive,Outcome outcome)throws Exception {
        check(fill(whitespace,sensitive),new FillAnswer(List.of(new BlankValue("x",a),new BlankValue("y",b))),outcome,true);
    }
    @Test void fillNfcAsciiWhitespaceAndRootLocaleDoNotDependOnMachineLocale()throws Exception {
        Locale original=Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var rootLocaleQuestion=question("FILL_BLANK","{\"parts\":[{\"kind\":\"BLANK\",\"blankId\":\"x\"}]}",
                "{\"blanks\":[{\"blankId\":\"x\",\"acceptedAnswers\":[\"I\"]}],\"caseSensitive\":false,\"normalizeWhitespace\":true}");
            check(rootLocaleQuestion,new FillAnswer(List.of(new BlankValue("x","i"))),Outcome.CORRECT,true);
            check(fill(true,false),new FillAnswer(List.of(new BlankValue("x"," CAFE\u0301\t"),new BlankValue("y","\r\nhello\t world "))),Outcome.CORRECT,true);
            check(fill(true,false),new FillAnswer(List.of(new BlankValue("x","Café"),new BlankValue("y","hello\u00a0world"))),Outcome.INCORRECT,true);
            check(fill(false,false),new FillAnswer(List.of(new BlankValue("x"," Café"),new BlankValue("y","hello world"))),Outcome.INCORRECT,true);
        } finally {Locale.setDefault(original);}
    }
    @Test void partialAndEmptyFillSeparateCompletenessFromCorrectness()throws Exception {
        check(fill(true,false),new FillAnswer(List.of()),Outcome.UNANSWERED,false);
        check(fill(true,false),new FillAnswer(List.of(new BlankValue("x","  "))),Outcome.UNANSWERED,false);
        check(fill(true,false),new FillAnswer(List.of(new BlankValue("x","Café"))),Outcome.INCORRECT,false);
        check(fill(true,false),new FillAnswer(List.of(new BlankValue("x","Café"),new BlankValue("y","wrong"))),Outcome.INCORRECT,true);
    }
    @Test void matchingOrderDoesNotMatterAndPartialDoesNotEarnCredit()throws Exception {
        check(matching(),new MatchingAnswer(List.of(new Pair("l2","r2"),new Pair("l1","r1"))),Outcome.CORRECT,true);
        check(matching(),new MatchingAnswer(List.of(new Pair("l1","r2"),new Pair("l2","r1"))),Outcome.INCORRECT,true);
        check(matching(),new MatchingAnswer(List.of(new Pair("l1","r1"))),Outcome.INCORRECT,false);
        check(matching(),new MatchingAnswer(List.of()),Outcome.UNANSWERED,false);
    }
    @Test void invalidIdsTypesAndRepeatedEndsAreRejectedRatherThanGradedWrong()throws Exception {
        var cases=List.of(new Object[]{single(),new SingleAnswer("foreign")},
            new Object[]{single(),new FillAnswer(List.of())},
            new Object[]{fill(true,false),new FillAnswer(List.of(new BlankValue("unknown","x")))},
            new Object[]{fill(true,false),new FillAnswer(List.of(new BlankValue("x","a"),new BlankValue("x","b")))},
            new Object[]{matching(),new MatchingAnswer(List.of(new Pair("l1","r1"),new Pair("l2","r1")))},
            new Object[]{matching(),new MatchingAnswer(List.of(new Pair("l1","missing")))});
        for(var c:cases)assertThatThrownBy(()->grader.grade((Question)c[0],(Answer)c[1]))
            .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("INVALID_ANSWER"));
    }
}
