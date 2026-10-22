package vn.edu.ptit.english;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import vn.edu.ptit.english.dto.AttemptViews.*;
import vn.edu.ptit.english.enums.AttemptEnums.*;
import static vn.edu.ptit.english.service.impl.GamificationRules.*;
import static org.assertj.core.api.Assertions.*;

class GamificationRulesTest {
    ResultItem item(PracticeRole role,boolean complete,Outcome outcome,boolean support){return new ResultItem(1,1,"origin",1,role,false,null,outcome,complete,support,null,outcome,complete,JsonNodeFactory.instance.objectNode(),"",null);}
    Result result(List<ResultItem> items){return new Result(1,1,1,Mode.PRACTICE,null,Instant.parse("2026-10-21T12:00:00Z"),1,items.size(),0,0,0,BigDecimal.ZERO,0,0,false,items,List.of());}
    @Test void guidedOnlyEmptyAndUnderThresholdAttemptsCannotCreateAnActivityDay(){
        assertThat(qualified(result(List.of()))).isFalse();assertThat(qualified(result(List.of(item(PracticeRole.GUIDED,true,Outcome.CORRECT,false))))).isFalse();
        var correct=item(PracticeRole.INDEPENDENT,true,Outcome.CORRECT,false);var blank=item(PracticeRole.INDEPENDENT,false,Outcome.UNANSWERED,false);
        assertThat(qualified(result(List.of(correct,blank,blank)))).isFalse();assertThat(qualified(result(List.of(correct,correct,blank)))).isFalse();
        assertThat(qualified(result(List.of(correct,correct,correct)))).isTrue();assertThat(qualified(result(List.of(correct)))).isTrue();
        assertThat(qualified(result(List.of(item(PracticeRole.INDEPENDENT,true,Outcome.CORRECT,true))))).isFalse();
    }
    @Test void todayYesterdayAndGapUseCalendarDaysAndNeverCountFutureRows(){
        LocalDate today=LocalDate.parse("2026-10-21");
        var days=List.of(today.minusDays(3),today.minusDays(2),today.minusDays(1));
        assertThat(streak(days,today).current()).isEqualTo(3);assertThat(streak(days,today.plusDays(1)).current()).isZero();
        assertThat(streak(days,today.plusDays(1)).best()).isEqualTo(3);
        var withToday=new ArrayList<>(days);withToday.add(today);withToday.add(today);withToday.add(today.plusDays(1));
        assertThat(streak(withToday,today).current()).isEqualTo(4);assertThat(streak(withToday,today).best()).isEqualTo(4);
    }
}
