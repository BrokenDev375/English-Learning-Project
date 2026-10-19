package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="question_exposures")
public class QuestionExposureEntity {
    @EmbeddedId private QuestionExposureKey id;
    @Column(name="first_seen_at",nullable=false,updatable=false,columnDefinition="datetime(6)") private Instant firstSeenAt;
    @Column(name="last_seen_at",nullable=false,columnDefinition="datetime(6)") private Instant lastSeenAt;
    @Column(name="seen_count",nullable=false) private int seenCount;
    @Column(name="first_attempt_id",nullable=false,updatable=false) private long firstAttemptId;
    @Column(name="last_attempt_id",nullable=false) private long lastAttemptId;
    protected QuestionExposureEntity() {}
    public QuestionExposureEntity(long userId,String origin,long attempt,Instant now) {
        id=new QuestionExposureKey(userId,origin); firstAttemptId=lastAttemptId=attempt; firstSeenAt=lastSeenAt=now; seenCount=1;
    }
    public QuestionExposureKey getId(){return id;} public int getSeenCount(){return seenCount;}
    public Instant getFirstSeenAt(){return firstSeenAt;} public Instant getLastSeenAt(){return lastSeenAt;}
    public long getFirstAttemptId(){return firstAttemptId;} public long getLastAttemptId(){return lastAttemptId;}
    public void seenIn(long attempt,Instant now){seenCount++; lastAttemptId=attempt; lastSeenAt=now;}
}
