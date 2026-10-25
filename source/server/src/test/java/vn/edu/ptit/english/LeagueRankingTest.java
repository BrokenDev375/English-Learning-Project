package vn.edu.ptit.english;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import vn.edu.ptit.english.dto.LeagueViews.*;
import vn.edu.ptit.english.service.impl.LeagueRanking;
import static vn.edu.ptit.english.service.impl.LeagueRanking.*;

class LeagueRankingTest {
    List<Score> scores(long... xp){var list=new ArrayList<Score>();for(int i=0;i<xp.length;i++)list.add(new Score("member-"+i,xp[i]));return list;}
    @Test void ranksAndTieBlocksDoNotUseMemberKeyToBreakMovementTies(){
        var r=rank(scores(100,90,90,70,60,50,40,30,20,10),Tier.SILVER,RULES);
        assertThat(r.placements().get("member-0").outcome()).isEqualTo("PROMOTE");
        assertThat(r.placements().get("member-1").rank()).isEqualTo(2);
        assertThat(r.placements().get("member-2").rank()).isEqualTo(2);
        assertThat(r.placements().get("member-1").reason()).isEqualTo("TIED_BOUNDARY");
        assertThat(r.placements().get("member-2").outcome()).isEqualTo("KEEP");
        assertThat(r.placements().get("member-3").rank()).isEqualTo(4);
        assertThat(r.placements().get("member-8").outcome()).isEqualTo("DEMOTE");
    }
    @Test void bottomTieAcrossBoundaryKeepsWholeBlockAndDoesNotTransferQuota(){
        var r=rank(scores(100,90,80,70,60,50,40,20,20,20),Tier.GOLD,RULES);
        for(int i=7;i<10;i++)assertThat(r.placements().get("member-"+i).reason()).isEqualTo("TIED_BOUNDARY");
        assertThat(r.placements().values().stream().filter(p->p.outcome().equals("DEMOTE")).count()).isZero();
    }
    @Test void smallOrInactiveGroupsKeepTheirTierIncludingAllZeroTies(){
        var small=rank(scores(20,10),Tier.SILVER,RULES);assertThat(small.stats().holdReason()).isEqualTo("TOO_FEW_MEMBERS");
        var inactive=rank(scores(10,10,10,10,0,0,0,0,0,0),Tier.SILVER,RULES);
        assertThat(inactive.stats().holdReason()).isEqualTo("TOO_FEW_ACTIVE");
        assertThat(inactive.stats().promotionQuota()).isZero();assertThat(inactive.placements().values()).allMatch(p->p.outcome().equals("KEEP"));
    }
    @Test void bronzeAndDiamondClampMovementAndZeroXpNeverPromotes(){
        var values=scores(10,9,8,7,6,5,4,3,2,1);
        var bronze=rank(values,Tier.BRONZE,RULES);assertThat(bronze.stats().demotionQuota()).isZero();
        assertThat(bronze.placements().get("member-9").reason()).isEqualTo("MINIMUM_TIER");
        var diamond=rank(values,Tier.DIAMOND,RULES);assertThat(diamond.stats().promotionQuota()).isZero();
        assertThat(diamond.placements().get("member-0").reason()).isEqualTo("MAXIMUM_TIER");
    }
    @Test void vietnamMondayBoundaryUsesHalfOpenWeek(){
        assertThat(weekStart(Instant.parse("2026-10-25T16:59:59.999999Z"))).isEqualTo(Instant.parse("2026-10-18T17:00:00Z"));
        assertThat(weekStart(Instant.parse("2026-10-25T17:00:00Z"))).isEqualTo(Instant.parse("2026-10-25T17:00:00Z"));
    }
    @Test void fullGroupQuotasAndExactActivityThresholds(){
        var values=new ArrayList<Score>();for(int i=0;i<25;i++)values.add(new Score("k"+i,250-i*10));
        var full=rank(values,Tier.GOLD,RULES);
        assertThat(full.stats().promotionQuota()).isEqualTo(5);assertThat(full.stats().demotionQuota()).isEqualTo(5);
        assertThat(full.placements().values().stream().filter(p->p.outcome().equals("PROMOTE")).count()).isEqualTo(5);
        assertThat(full.placements().values().stream().filter(p->p.outcome().equals("DEMOTE")).count()).isEqualTo(5);
        assertThat(rank(values.subList(0,9),Tier.GOLD,RULES).stats().movementEnabled()).isFalse();
        assertThat(rank(scores(5,4,3,2,1,0,0,0,0,0),Tier.GOLD,RULES).stats().movementEnabled()).isTrue();
    }
    @Test void tiesAtBothTwentyFiveMemberBoundariesAndIntactTiesShareOutcomes(){
        var values=new ArrayList<Score>();for(int i=0;i<25;i++)values.add(new Score("k"+i,250-i*10));
        values.set(5,new Score("k5",210));values.set(20,new Score("k20",60));
        var result=rank(values,Tier.SILVER,RULES);
        for(String key:List.of("k4","k5","k19","k20"))assertThat(result.placements().get(key).reason()).isEqualTo("TIED_BOUNDARY");
        var intact=rank(scores(100,100,80,70,60,50,40,30,10,10),Tier.SILVER,RULES);
        assertThat(intact.placements().get("member-0").outcome()).isEqualTo("PROMOTE");assertThat(intact.placements().get("member-1").outcome()).isEqualTo("PROMOTE");
        assertThat(intact.placements().get("member-8").outcome()).isEqualTo("DEMOTE");assertThat(intact.placements().get("member-9").outcome()).isEqualTo("DEMOTE");
        assertThat(rank(Collections.nCopies(25,0L).stream().map(x->new Score(UUID.randomUUID().toString(),x)).toList(),Tier.SILVER,RULES).stats().movementEnabled()).isFalse();
    }
}
