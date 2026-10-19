package vn.edu.ptit.english.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.edu.ptit.english.dto.AttemptData.*;
import vn.edu.ptit.english.dto.AuthoringData.Question;
import vn.edu.ptit.english.exception.ApiException;

/** Validate against a published question, in addition to transport-shape validation. */
@Component
public class AnswerValidator {
    public void validate(Question q,Answer answer) {
        if(answer==null) return;
        if(answer instanceof SingleAnswer a && q.type().equals("SINGLE_CHOICE")) {
            if(a.optionId()!=null && !ids(q.content().path("options")).contains(a.optionId())) throw invalid("optionId");
        } else if(answer instanceof FillAnswer a && q.type().equals("FILL_BLANK")) {
            var allowed=new HashSet<String>();
            q.content().path("parts").forEach(p -> { if(p.path("kind").asText().equals("BLANK")) allowed.add(p.path("blankId").asText()); });
            var seen=new HashSet<String>();
            if(a.values().size()>5) throw invalid("values");
            for(var v:a.values()) if(v==null||v.value()==null||v.value().codePointCount(0,v.value().length())>200
                ||!allowed.contains(v.blankId())||!seen.add(v.blankId())) throw invalid("values");
        } else if(answer instanceof MatchingAnswer a && q.type().equals("MATCHING")) {
            var left=ids(q.content().path("leftItems")); var right=ids(q.content().path("rightItems"));
            var seenLeft=new HashSet<String>(); var seenRight=new HashSet<String>();
            if(a.pairs().size()>6) throw invalid("pairs");
            for(var pair:a.pairs()) if(pair==null||!left.contains(pair.leftId())||!right.contains(pair.rightId())
                ||!seenLeft.add(pair.leftId())||!seenRight.add(pair.rightId())) throw invalid("pairs");
        } else throw invalid("type");
    }
    private Set<String> ids(JsonNode items) { var set=new HashSet<String>(); items.forEach(i -> set.add(i.path("id").asText())); return set; }
    private ApiException invalid(String field) {
        return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ANSWER","Đáp án không khớp câu hỏi.",Map.of("response."+field,"Đáp án không khớp câu hỏi."));
    }
}
