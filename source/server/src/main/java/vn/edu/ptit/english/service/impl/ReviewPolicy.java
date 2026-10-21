package vn.edu.ptit.english.service.impl;

import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReviewPolicy {
    private final List<Integer> intervals;
    private final int minimumQuestions, goodPercent, goodSets;
    private final ZoneId zone;
    public ReviewPolicy(@Value("${app.review.intervals-days:1,3,7}") List<Integer> intervals,
                        @Value("${app.review.minimum-fresh-questions:3}") int minimumQuestions,
                        @Value("${app.review.good-percent:80}") int goodPercent,
                        @Value("${app.review.minimum-good-sets:2}") int goodSets,
                        @Value("${app.review.zone:Asia/Ho_Chi_Minh}") String zone) {
        if (intervals.size() != 3 || intervals.stream().anyMatch(n -> n == null || n < 1 || n > 365)
            || intervals.get(0) > intervals.get(1) || intervals.get(1) > intervals.get(2)
            || minimumQuestions < 1 || minimumQuestions > 10 || goodPercent < 1 || goodPercent > 100 || goodSets < 2)
            throw new IllegalArgumentException("Invalid personal-review policy");
        this.intervals = List.copyOf(intervals); this.minimumQuestions = minimumQuestions;
        this.goodPercent = goodPercent; this.goodSets = goodSets; this.zone = ZoneId.of(zone);
    }
    public List<Integer> intervals() { return intervals; }
    public int minimumQuestions() { return minimumQuestions; }
    public int goodPercent() { return goodPercent; }
    public int goodSets() { return goodSets; }
    public ZoneId zone() { return zone; }
    public static int difficultyRank(String difficulty) { return List.of("EASY", "MEDIUM", "HARD").indexOf(difficulty); }
    public static String nextDifficulty(String difficulty) {
        return switch (difficulty) { case "EASY" -> "MEDIUM"; case "MEDIUM" -> "HARD"; default -> null; };
    }
}
