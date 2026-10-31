package vn.edu.ptit.english.entity;

import jakarta.persistence.*;

@Entity @Table(name="attempt_audio_listens")
public class AttemptAudioListenEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="attempt_id",nullable=false,updatable=false) private long attemptId;
    @Column(name="material_version_id",nullable=false,updatable=false) private long materialVersionId;
    @Column(name="plays_used",nullable=false) private int playsUsed;
    protected AttemptAudioListenEntity() {}
    public AttemptAudioListenEntity(long attemptId,long materialVersionId){this.attemptId=attemptId;this.materialVersionId=materialVersionId;}
    public Long getId(){return id;}
    public int getPlaysUsed(){return playsUsed;}
    public void start(){if(playsUsed>=2)throw new IllegalStateException("Audio limit reached");playsUsed++;}
}
