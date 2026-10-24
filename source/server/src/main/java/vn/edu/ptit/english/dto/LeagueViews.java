package vn.edu.ptit.english.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;

public final class LeagueViews {
    private LeagueViews() {}
    public enum Tier { BRONZE,SILVER,GOLD,PLATINUM,DIAMOND }
    public record Rules(int capacity,int minimumMembers,int minimumActive,int promotionPercent,int demotionPercent) {}
    public record Season(long id,@JsonFormat(shape=JsonFormat.Shape.STRING) Instant startAt,
        @JsonFormat(shape=JsonFormat.Shape.STRING) Instant endAt,@JsonFormat(shape=JsonFormat.Shape.STRING) Instant cutoffAt,
        String status,String policyVersion,Rules rules) {}
    public record Result(int rank,int memberCount,int activeCount,long weeklyXp,Tier tierBefore,Tier tierAfter,
        String outcome,String holdReason,@JsonFormat(shape=JsonFormat.Shape.STRING) Instant finalizedAt) {}
    public record Membership(String memberKey,long groupId,Tier tier,@JsonFormat(shape=JsonFormat.Shape.STRING) Instant joinedAt,
        long weeklyXp,Integer rank,String projectedOutcome,Result result) {}
    public record Overview(@JsonFormat(shape=JsonFormat.Shape.STRING) Instant serverTime,long revision,String state,
        boolean canJoin,Season season,Membership membership) {}
    public record Join(String requestId,boolean created,long revision,Season season,Membership membership) {}
    public record GroupStats(int memberCount,int activeCount,int capacity,int promotionQuota,int demotionQuota,
        boolean movementEnabled,String holdReason) {}
    public record Member(String memberKey,String displayName,boolean isSelf,int rank,long weeklyXp,String outcome) {}
    public record Board(List<Member> items,long totalElements,int page,int size,int totalPages,
        @JsonFormat(shape=JsonFormat.Shape.STRING) Instant serverTime,long seasonId,long groupId,Tier tier,long revision,
        boolean provisional,GroupStats groupStats,Member self) {}
    public record HistoryItem(Season season,long groupId,Result result) {}
    public record History(List<HistoryItem> items,long totalElements,int page,int size,int totalPages) {}
}
