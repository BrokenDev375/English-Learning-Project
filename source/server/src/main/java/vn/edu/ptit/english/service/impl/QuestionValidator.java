package vn.edu.ptit.english.service.impl;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Component;
import vn.edu.ptit.english.dto.AuthoringData.*;

@Component
public class QuestionValidator {
    private void need(List<Issue> errors,boolean condition,String path,String message){if(!condition)errors.add(new Issue(path,"INVALID_CONTENT",message));}
    private Set<String> ids(JsonNode items,String key){var result=new HashSet<String>();items.forEach(item->result.add(item.path(key).asText()));return result;}
    private boolean validItems(JsonNode items){return ids(items,"id").size()==items.size()&&items.size()>=2&&items.size()<=6&&java.util.stream.StreamSupport.stream(items.spliterator(),false).allMatch(i->!i.path("id").asText().isBlank()&&!i.path("text").asText().isBlank());}
    public List<Issue> errors(Question data){
        var errors=new ArrayList<Issue>();need(errors,!data.prompt().isBlank(),"prompt","Cần nhập nội dung câu hỏi.");need(errors,!data.explanation().isBlank(),"explanation","Cần giải thích đáp án trước khi xuất bản.");var c=data.content();var a=data.answerKey();
        switch(data.type()){
            case "SINGLE_CHOICE" -> {var options=c.path("options");need(errors,validItems(options),"content.options","Cần 2–6 lựa chọn có nội dung và mã duy nhất.");need(errors,a.path("correctOptionId").isTextual()&&ids(options,"id").contains(a.path("correctOptionId").asText()),"answerKey.correctOptionId","Chọn đúng một đáp án thuộc các lựa chọn.");}
            case "FILL_BLANK" -> {var blankIds=new ArrayList<String>();boolean meaningful=false;for(var p:c.path("parts")){if(p.path("kind").asText().equals("BLANK"))blankIds.add(p.path("blankId").asText());else if(!p.path("text").asText().isBlank())meaningful=true;}var expected=new HashSet<>(blankIds);need(errors,meaningful&&blankIds.size()>=1&&blankIds.size()<=5&&expected.size()==blankIds.size()&&blankIds.stream().noneMatch(String::isBlank),"content.parts","Cần ngữ cảnh và 1–5 ô trống có mã duy nhất.");var blanks=a.path("blanks");need(errors,ids(blanks,"blankId").equals(expected)&&blanks.size()==expected.size(),"answerKey.blanks","Đáp án phải khớp đủ từng ô trống, không trùng hoặc thừa ô.");for(var b:blanks){var answers=b.path("acceptedAnswers");need(errors,answers.size()>=1&&answers.size()<=10&&java.util.stream.StreamSupport.stream(answers.spliterator(),false).noneMatch(v->v.asText().isBlank()),"answerKey.blanks","Mỗi ô cần 1–10 đáp án chấp nhận có nội dung.");}}
            case "MATCHING" -> {var left=c.path("leftItems");var right=c.path("rightItems");var pairs=a.path("pairs");need(errors,validItems(left)&&validItems(right)&&left.size()==right.size(),"content","Hai cột cần cùng 2–6 mục có nội dung và mã duy nhất.");need(errors,pairs.size()==left.size()&&ids(pairs,"leftId").equals(ids(left,"id"))&&ids(pairs,"rightId").equals(ids(right,"id")),"answerKey.pairs","Mỗi mục phải ghép đúng một mục ở cột đối diện, không trùng đầu ghép.");}
        }
        return errors;
    }
}
