package vn.edu.ptit.english.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.ptit.english.dto.AttemptData.PlayerState;
import vn.edu.ptit.english.enums.AttemptEnums.*;

@Entity @Table(name="attempts")
public class AttemptEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id",nullable=false,updatable=false) private long userId;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable=false,length=16,updatable=false) private Mode mode;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable=false,length=16) private State state=State.IN_PROGRESS;
    @Column(name="lesson_version_id",updatable=false) private Long lessonVersionId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="selection_json",nullable=false,updatable=false) private JsonNode selection;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="player_state_json",nullable=false) private PlayerState playerState;
    // Explicit aggregate revision: service changes it exactly once per logical mutation under lock.
    @Column(nullable=false) private long revision;
    @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false,columnDefinition="datetime(6)") private Instant createdAt;
    @UpdateTimestamp @Column(name="updated_at",nullable=false,columnDefinition="datetime(6)") private Instant updatedAt;
    @Column(name="submitted_at",columnDefinition="datetime(6)") private Instant submittedAt;
    protected AttemptEntity() {}
    public AttemptEntity(long userId,Mode mode,Long lessonVersionId,JsonNode selection,PlayerState playerState) {
        this.userId=userId; this.mode=mode; this.lessonVersionId=lessonVersionId;
        this.selection=selection.deepCopy(); this.playerState=playerState;
    }
    public Long getId(){return id;} public long getUserId(){return userId;} public Mode getMode(){return mode;}
    public State getState(){return state;} public Long getLessonVersionId(){return lessonVersionId;}
    public JsonNode getSelection(){return selection.deepCopy();} public PlayerState getPlayerState(){return playerState;}
    public long getRevision(){return revision;} public Instant getCreatedAt(){return createdAt;}
    public Instant getUpdatedAt(){return updatedAt;} public Instant getSubmittedAt(){return submittedAt;}
    public void savePlayerState(PlayerState playerState){requireInProgress(); this.playerState=playerState;}
    public void changed(){revision++;}
    public void submit(Instant time){requireInProgress(); state=State.SUBMITTED; submittedAt=time;}
    private void requireInProgress(){if(state!=State.IN_PROGRESS) throw new IllegalStateException("Attempt already submitted");}
}
