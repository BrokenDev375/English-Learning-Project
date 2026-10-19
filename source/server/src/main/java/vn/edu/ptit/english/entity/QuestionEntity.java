package vn.edu.ptit.english.entity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="questions")
public class QuestionEntity extends CatalogEntity {
    @Column(name="seed_key",length=96) private String seedKey;
    @Column(name="origin_key",nullable=false,length=36,columnDefinition="char(36)") private String originKey;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="draft_version_id") private QuestionVersionEntity draft;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="published_version_id") private QuestionVersionEntity published;
    @Column(name="last_version_no",nullable=false) private int lastVersionNo=1;
    @Column(name="retired_at",columnDefinition="datetime(6)") private Instant retiredAt;
    protected QuestionEntity(){}
    public QuestionEntity(long owner,String origin){ownerId=owner;originKey=origin;}
    public String getOriginKey(){return originKey;}
    public QuestionVersionEntity getDraft(){return draft;}
    public QuestionVersionEntity getPublished(){return published;}
    public int getLastVersionNo(){return lastVersionNo;}
    public Instant getRetiredAt(){return retiredAt;}
    public void setSeedKey(String key){seedKey=key;}
    public void initialDraft(QuestionVersionEntity version){draft=version;}
    public void startDraft(QuestionVersionEntity version){draft=version;lastVersionNo=version.getVersionNo();revision++;}
    public void changed(){revision++;}
    public void discard(){draft=null;revision++;}
    public void publish(){draft.publish();published=draft;draft=null;retiredAt=null;revision++;}
    public void retire(){if(retiredAt==null){retiredAt=Instant.now();revision++;}}
    public String status(){return retiredAt!=null?"RETIRED":published!=null?"PUBLISHED":"DRAFT";}
}
