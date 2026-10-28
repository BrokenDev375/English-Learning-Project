package vn.edu.ptit.english.dto;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
public final class ContentReviewViews {
    private ContentReviewViews(){}
    public record Review(long id,String kind,long resourceId,String title,long versionId,long revision,String state,long submittedBy,String submittedByName,Instant submittedAt,Long reviewedBy,String reviewedByName,Instant reviewedAt,String note){}
    public record Detail(Review review,JsonNode snapshot,boolean stale,boolean canWithdraw){}
}
