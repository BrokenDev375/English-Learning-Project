package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class QuestionExposureKey implements Serializable {
    @Column(name="user_id") private Long userId;
    @Column(name="origin_key",columnDefinition="char(36)") private String originKey;
    protected QuestionExposureKey() {}
    public QuestionExposureKey(long userId,String originKey){this.userId=userId; this.originKey=originKey;}
    public Long getUserId(){return userId;} public String getOriginKey(){return originKey;}
    @Override public boolean equals(Object other){return other instanceof QuestionExposureKey k&&Objects.equals(userId,k.userId)&&Objects.equals(originKey,k.originKey);}
    @Override public int hashCode(){return Objects.hash(userId,originKey);}
}
