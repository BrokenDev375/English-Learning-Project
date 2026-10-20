package vn.edu.ptit.english.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.List;

public final class AttemptData {
    private AttemptData() {}

    @JsonTypeInfo(use=JsonTypeInfo.Id.NAME, property="type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value=SingleAnswer.class,name="SINGLE_CHOICE"),
        @JsonSubTypes.Type(value=FillAnswer.class,name="FILL_BLANK"),
        @JsonSubTypes.Type(value=MatchingAnswer.class,name="MATCHING")
    })
    public sealed interface Answer permits SingleAnswer, FillAnswer, MatchingAnswer {}
    public record SingleAnswer(String optionId) implements Answer {}
    public record BlankValue(String blankId, String value) {}
    public record FillAnswer(List<BlankValue> values) implements Answer {
        public FillAnswer { values=List.copyOf(values); }
    }
    public record Pair(String leftId, String rightId) {}
    public record MatchingAnswer(List<Pair> pairs) implements Answer {
        public MatchingAnswer { pairs=List.copyOf(pairs); }
    }
    public record AnswerEntry(long attemptQuestionId, Answer response) {}
    public record AudioPosition(long materialVersionId, double seconds) {}
    public record PlayerState(Long currentAttemptQuestionId, List<AudioPosition> audioPositions) {
        public PlayerState { audioPositions=List.copyOf(audioPositions); }
        public static PlayerState empty() { return new PlayerState(null,List.of()); }
    }
    /** Persisted once; ordering contains stable IDs rather than display indices. */
    public record Display(List<String> optionIds, List<String> rightItemIds) {
        public Display { optionIds=List.copyOf(optionIds); rightItemIds=List.copyOf(rightItemIds); }
    }
}
