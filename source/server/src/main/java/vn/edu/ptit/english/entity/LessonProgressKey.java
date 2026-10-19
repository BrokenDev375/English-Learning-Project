package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class LessonProgressKey implements Serializable {
    @Column(name="user_id") private Long userId;
    @Column(name="lesson_id") private Long lessonId;
    protected LessonProgressKey() {}
    public LessonProgressKey(long userId,long lessonId){this.userId=userId; this.lessonId=lessonId;}
    public Long getUserId(){return userId;} public Long getLessonId(){return lessonId;}
    @Override public boolean equals(Object other){return other instanceof LessonProgressKey k&&Objects.equals(userId,k.userId)&&Objects.equals(lessonId,k.lessonId);}
    @Override public int hashCode(){return Objects.hash(userId,lessonId);}
}
