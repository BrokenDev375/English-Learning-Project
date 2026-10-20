package vn.edu.ptit.english.service;

import vn.edu.ptit.english.dto.AttemptData.Answer;
import vn.edu.ptit.english.dto.AuthoringData.Question;
import vn.edu.ptit.english.enums.AttemptEnums.Outcome;

public interface AnswerGrader {
    record Grade(Outcome outcome,boolean responseComplete) {}
    Grade grade(Question question,Answer response);
}
