package vn.edu.ptit.english.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.service.ReviewRewardContextRecorder;

@Service
public class GamificationReviewContextRecorder implements ReviewRewardContextRecorder {
    private final ReviewRepository review;
    private final GamificationContextRepository rewards;
    public GamificationReviewContextRecorder(ReviewRepository review,GamificationContextRepository rewards) { this.review=review; this.rewards=rewards; }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void capture(long attemptId,long userId,Collection<Long> objectiveIds,Instant at) {
        var policy=rewards.policy();
        for(long objectiveId:new TreeSet<>(objectiveIds)) {
            var p=review.progress(new ReviewObjectiveKey(userId,objectiveId)).orElse(null);
            // Other provider implementations may have no M04 schedule. Never invent evidence.
            if(p==null) continue;
            rewards.save(new ReviewRewardContextEntity(attemptId,userId,p,policy.getVersion(),cycleKey(userId,objectiveId,p.getNextReviewAt(),p.getStage()),at));
        }
    }
    public static String cycleKey(long userId,long objectiveId,Instant dueAt,int stage) {
        long micros=Math.addExact(Math.multiplyExact(dueAt.getEpochSecond(),1_000_000L),dueAt.getNano()/1000);
        String source="m05-review-cycle-v1\n"+userId+"\n"+objectiveId+"\n"+micros+"\n"+stage+"\n";
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
