package vn.edu.ptit.english.service.impl;

import java.text.Normalizer;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.edu.ptit.english.dto.AttemptData.*;
import vn.edu.ptit.english.dto.AuthoringData.Question;
import vn.edu.ptit.english.enums.AttemptEnums.Outcome;
import vn.edu.ptit.english.service.AnswerGrader;

/** Pure grading: never mutates answers, history, progress or points. */
@Service
public class AnswerGraderImpl implements AnswerGrader {
    private final AnswerValidator validator;
    public AnswerGraderImpl(AnswerValidator validator) { this.validator=validator; }
    @Override public Grade grade(Question q,Answer response) {
        validator.validate(q,response);
        if(response==null) return new Grade(Outcome.UNANSWERED,false);
        if(response instanceof SingleAnswer a) {
            if(a.optionId()==null) return new Grade(Outcome.UNANSWERED,false);
            return graded(true,a.optionId().equals(q.answerKey().path("correctOptionId").asText()));
        }
        if(response instanceof FillAnswer a) {
            var values=new HashMap<String,String>(); a.values().forEach(v -> values.put(v.blankId(),v.value()));
            if(values.values().stream().allMatch(String::isBlank)) return new Grade(Outcome.UNANSWERED,false);
            boolean complete=true,correct=true;
            boolean whitespace=q.answerKey().path("normalizeWhitespace").asBoolean();
            boolean sensitive=q.answerKey().path("caseSensitive").asBoolean();
            for(var blank:q.answerKey().path("blanks")) {
                String value=values.get(blank.path("blankId").asText());
                if(value==null||value.isBlank()) { complete=false; correct=false; continue; }
                String normalized=normalize(value,whitespace,sensitive); boolean accepted=false;
                for(var candidate:blank.path("acceptedAnswers")) if(normalized.equals(normalize(candidate.asText(),whitespace,sensitive))) { accepted=true; break; }
                correct &= accepted;
            }
            return graded(complete,correct);
        }
        if(response instanceof OrderingAnswer a) {
            if(a.tokenIds().isEmpty())return new Grade(Outcome.UNANSWERED,false);
            var words=new HashMap<String,String>();q.content().path("tokens").forEach(t->words.put(t.path("id").asText(),Normalizer.normalize(t.path("text").asText(),Normalizer.Form.NFC)));
            boolean complete=a.tokenIds().size()==words.size(),correct=false;
            var actual=a.tokenIds().stream().map(words::get).toList();
            for(var order:q.answerKey().path("acceptedOrders")) {
                var expected=new ArrayList<String>();order.forEach(t->expected.add(words.get(t.asText())));
                if(complete&&actual.equals(expected)){correct=true;break;}
            }
            return graded(complete,correct);
        }
        var pairs=((MatchingAnswer)response).pairs();
        if(pairs.isEmpty()) return new Grade(Outcome.UNANSWERED,false);
        var expected=new HashMap<String,String>(); q.answerKey().path("pairs").forEach(p -> expected.put(p.path("leftId").asText(),p.path("rightId").asText()));
        boolean complete=pairs.size()==expected.size();
        return graded(complete,complete&&pairs.stream().allMatch(p -> p.rightId().equals(expected.get(p.leftId()))));
    }
    private Grade graded(boolean complete,boolean correct) { return new Grade(correct?Outcome.CORRECT:Outcome.INCORRECT,complete); }
    private String normalize(String value,boolean whitespace,boolean sensitive) {
        String result=Normalizer.normalize(value,Normalizer.Form.NFC);
        if(whitespace) result=result.replaceAll("[ \\t\\r\\n]+"," ").replaceAll("^ +| +$","");
        return sensitive?result:result.toLowerCase(Locale.ROOT);
    }
}
