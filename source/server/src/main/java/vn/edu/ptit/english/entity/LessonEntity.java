package vn.edu.ptit.english.entity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="lessons")
public class LessonEntity extends CatalogEntity {
    @Column(name="seed_key",length=96) private String seedKey;
    @Column(name="unit_id",nullable=false) private long unitId;
    @Column(name="sort_order",nullable=false) private int sortOrder;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="draft_version_id") private LessonVersionEntity draft;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="published_version_id") private LessonVersionEntity published;
    @Column(name="last_version_no",nullable=false) private int lastVersionNo=1;
    @Column(name="retired_at",columnDefinition="datetime(6)") private Instant retiredAt;
    protected LessonEntity(){}
    public LessonEntity(long owner,long unit,int order){ownerId=owner;unitId=unit;sortOrder=order;}
    public long getUnitId(){return unitId;}
    public int getSortOrder(){return sortOrder;}
    public void order(int value){if(sortOrder!=value){sortOrder=value;revision++;}}
    public LessonVersionEntity getDraft(){return draft;}
    public LessonVersionEntity getPublished(){return published;}
    public int getLastVersionNo(){return lastVersionNo;}
    public Instant getRetiredAt(){return retiredAt;}
    public void setSeedKey(String key){seedKey=key;}
    public void initialDraft(LessonVersionEntity version){draft=version;}
    public void startDraft(LessonVersionEntity version){draft=version;lastVersionNo=version.getVersionNo();revision++;}
    public void changed(){revision++;}
    public void discard(){draft=null;revision++;}
    public void publish(){draft.publish();published=draft;draft=null;retiredAt=null;revision++;}
    public void retire(){if(retiredAt==null){retiredAt=Instant.now();revision++;}}
    public String status(){return retiredAt!=null?"RETIRED":published!=null?"PUBLISHED":"DRAFT";}
}
