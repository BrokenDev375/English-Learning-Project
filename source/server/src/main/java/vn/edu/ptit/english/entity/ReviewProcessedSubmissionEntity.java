package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "review_processed_submissions")
public class ReviewProcessedSubmissionEntity {
    @Id @Column(name = "submission_id") private Long submissionId;
    @Column(name = "user_id", nullable = false) private long userId;
    @Column(name = "processed_at", nullable = false, columnDefinition = "datetime(6)") private Instant processedAt;
    protected ReviewProcessedSubmissionEntity() {}
    public ReviewProcessedSubmissionEntity(long submissionId, long userId, Instant now) {
        this.submissionId = submissionId; this.userId = userId; this.processedAt = now;
    }
}
