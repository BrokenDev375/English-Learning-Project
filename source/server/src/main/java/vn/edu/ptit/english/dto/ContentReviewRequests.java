package vn.edu.ptit.english.dto;
import jakarta.validation.constraints.*;
public final class ContentReviewRequests {
    private ContentReviewRequests(){}
    public record Decision(@NotNull @Pattern(regexp="^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$") String requestId,
        @NotNull @Min(0) Long baseRevision,@Size(max=2000) String note){public Decision{note=note==null?"":note.strip();}}
}
