package vn.edu.ptit.english.service.impl;

import java.util.*;
import org.springframework.stereotype.Component;
import vn.edu.ptit.english.dto.AttemptData.*;
import vn.edu.ptit.english.dto.AttemptRequests.Mutation;
import vn.edu.ptit.english.entity.AttemptQuestionEntity;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.service.PublishedContentReader;
import static vn.edu.ptit.english.dto.MaterialRequests.invalid;

/** Validate the complete snapshot before writing any answer. Does not grade or create trials. */
@Component
public class AttemptDraftValidator {
    private final PublishedContentReader content;
    private final AnswerValidator answers;
    public AttemptDraftValidator(PublishedContentReader content,AnswerValidator answers) {
        this.content=content; this.answers=answers;
    }
    public Map<Long,Answer> validate(List<AttemptQuestionEntity> items,Mutation input) {
        var questions=new HashMap<Long,AttemptQuestionEntity>(); items.forEach(i->questions.put(i.getId(),i));
        if(input.answers().size()!=items.size()) throw invalid("answers","Cần gửi đủ mỗi câu của lượt đúng một lần.");
        var responses=new LinkedHashMap<Long,Answer>(); var audio=new HashSet<Long>(); int index=0;
        for(var entry:input.answers()) {
            String path="answers["+index++ +"]";
            var item=questions.get(entry.attemptQuestionId());
            if(item==null||responses.containsKey(entry.attemptQuestionId())) throw invalid(path+".attemptQuestionId","Câu không thuộc lượt hoặc bị trùng.");
            var q=content.questionVersion(item.getQuestionVersionId()).data();
            try { answers.validate(q,entry.response()); }
            catch(ApiException e) {
                var fields=new LinkedHashMap<String,String>();
                e.fieldErrors().forEach((key,value)->fields.put(path+"."+key,value));
                throw new ApiException(e.status(),e.code(),e.getMessage(),fields);
            }
            responses.put(entry.attemptQuestionId(),entry.response());
            if(q.materialVersionId()!=null&&content.materialVersion(q.materialVersionId()).data().kind().equals("AUDIO")) audio.add(q.materialVersionId());
        }
        Long current=input.playerState().currentAttemptQuestionId();
        if(current!=null&&!questions.containsKey(current)) throw invalid("playerState.currentAttemptQuestionId","Câu hiện tại không thuộc lượt.");
        var seen=new HashSet<Long>(); index=0;
        for(var position:input.playerState().audioPositions()) {
            String path="playerState.audioPositions["+index++ +"]";
            if(!audio.contains(position.materialVersionId())||!seen.add(position.materialVersionId())) throw invalid(path+".materialVersionId","Audio không thuộc lượt hoặc bị trùng.");
            if(!Double.isFinite(position.seconds())||position.seconds()<0||position.seconds()>86400) throw invalid(path+".seconds","Vị trí nghe không hợp lệ.");
        }
        return Collections.unmodifiableMap(responses);
    }
    public static boolean answered(Answer answer) {
        if(answer==null) return false;
        if(answer instanceof SingleAnswer a) return a.optionId()!=null;
        if(answer instanceof FillAnswer a) return a.values().stream().anyMatch(v->!v.value().isBlank());
        if(answer instanceof OrderingAnswer a) return !a.tokenIds().isEmpty();
        return !((MatchingAnswer)answer).pairs().isEmpty();
    }
}
