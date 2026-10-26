package vn.edu.ptit.english.service.impl;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import vn.edu.ptit.english.dto.LeagueViews.*;

/** The same tie-block algorithm supplies the provisional board and frozen results. */
public final class LeagueRanking {
    private LeagueRanking() {}
    public static final Rules LEGACY_RULES=new Rules(25,10,5,20,20),RULES=new Rules(25,2,2,20,20,1);
    public static Instant weekStart(Instant at) {
        return at.atZone(GamificationRules.ZONE).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(GamificationRules.ZONE).toInstant();
    }
    private static int quota(int members,int percent,int minimum) {
        return percent<=0?0:Math.max(minimum,members*percent/100);
    }
    public record Score(String key,long xp) {}
    public record Placement(int rank,String outcome,String reason,Tier next) {}
    public record Ranking(List<Score> sorted,GroupStats stats,Map<String,Placement> placements) {}
    public static Ranking rank(List<Score> scores,Tier tier,Rules rules) {
        var sorted=scores.stream().sorted(Comparator.comparingLong(Score::xp).reversed().thenComparing(Score::key)).toList();
        int n=sorted.size(),active=(int)sorted.stream().filter(s->s.xp()>0).count();
        String hold=n<rules.minimumMembers()?"TOO_FEW_MEMBERS":active<rules.minimumActive()?"TOO_FEW_ACTIVE":"NONE";
        boolean enabled=hold.equals("NONE"); int top=enabled&&tier!=Tier.DIAMOND?quota(n,rules.promotionPercent(),rules.minimumMovementSlots()):0;
        int bottom=enabled&&tier!=Tier.BRONZE?quota(n,rules.demotionPercent(),rules.minimumMovementSlots()):0;
        int rawTop=enabled?quota(n,rules.promotionPercent(),rules.minimumMovementSlots()):0,rawBottom=enabled?quota(n,rules.demotionPercent(),rules.minimumMovementSlots()):0;
        var placements=new HashMap<String,Placement>();
        for(int i=0;i<n;) {
            int end=i+1; while(end<n&&sorted.get(end).xp()==sorted.get(i).xp()) end++;
            String outcome="KEEP",reason=hold; Tier next=tier;
            if(enabled) {
                reason="NONE";
                if(sorted.get(i).xp()>0&&end<=rawTop) {
                    if(tier==Tier.DIAMOND) reason="MAXIMUM_TIER";
                    else {outcome="PROMOTE";next=Tier.values()[tier.ordinal()+1];}
                } else if(i>=n-rawBottom) {
                    if(tier==Tier.BRONZE) reason="MINIMUM_TIER";
                    else {outcome="DEMOTE";next=Tier.values()[tier.ordinal()-1];}
                } else if((i<top&&end>top)||(i<n-bottom&&end>n-bottom)) reason="TIED_BOUNDARY";
            }
            for(int j=i;j<end;j++) placements.put(sorted.get(j).key(),new Placement(i+1,outcome,reason,next));
            i=end;
        }
        return new Ranking(sorted,new GroupStats(n,active,rules.capacity(),top,bottom,enabled,hold),Map.copyOf(placements));
    }
}
