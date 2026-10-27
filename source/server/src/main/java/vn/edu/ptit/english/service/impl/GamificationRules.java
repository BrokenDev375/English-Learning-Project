package vn.edu.ptit.english.service.impl;

import java.time.*;
import java.util.*;
import vn.edu.ptit.english.dto.AttemptViews.*;
import vn.edu.ptit.english.dto.GamificationViews.Streak;
import vn.edu.ptit.english.enums.AttemptEnums.*;

/** Pure P0 rules. FIRST facts, not final answers or client-provided scores. */
public final class GamificationRules {
    private GamificationRules() {}
    public static final String LEGACY_VERSION="m05-p0-v1",FIXED_VERSION="m05-p0-v2",VERSION="m05-p0-v3";
    public static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    public static final int REWARD=5,CAP=200;
    public static boolean independentComplete(ResultItem item) {
        return item.practiceRole()!=PracticeRole.GUIDED&&!item.firstSupportUsed()&&item.firstResponseComplete();
    }
    public static String newQuestionReason(ResultItem item) {
        if(item.practiceRole()==PracticeRole.GUIDED) return "GUIDED";
        if(item.firstSupportUsed()) return "SUPPORTED";
        if(!item.firstResponseComplete()) return "INCOMPLETE_FIRST";
        if(item.firstOutcome()!=Outcome.CORRECT) return "FIRST_NOT_CORRECT";
        if(item.wasSeenBefore()) return "SEEN_BEFORE";
        return null;
    }
    public static boolean qualified(Result result) {
        long assigned=result.items().stream().filter(i->i.practiceRole()!=PracticeRole.GUIDED).count();
        long complete=result.items().stream().filter(GamificationRules::independentComplete).count();
        return assigned>0&&complete>=Math.min(3,assigned)&&result.items().stream().anyMatch(i->independentComplete(i)&&i.firstOutcome()==Outcome.CORRECT);
    }
    public static boolean objectivePassed(List<ResultItem> items) {
        return !items.isEmpty()&&items.stream().allMatch(i->independentComplete(i)&&i.firstOutcome()==Outcome.CORRECT);
    }
    public static Streak streak(Collection<LocalDate> activityDays,LocalDate today) {
        int running=0,best=0; LocalDate previous=null;
        for(var date:new TreeSet<>(activityDays)) {
            if(date.isAfter(today)) continue;
            running=previous!=null&&date.equals(previous.plusDays(1))?running+1:1;
            best=Math.max(best,running); previous=date;
        }
        int current=previous!=null&&!previous.isBefore(today.minusDays(1))?running:0;
        return new Streak(current,best,previous);
    }
}
